package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RECONCILE_KIND_SAVE
import com.nendo.argosy.data.remote.romm.RECONCILE_KIND_STATE
import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.remote.romm.RomMReconcileItem
import com.nendo.argosy.data.remote.romm.RomMReconcileOperation
import com.nendo.argosy.data.remote.romm.RomMReconcilePayload
import com.nendo.argosy.data.remote.romm.RomMState
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.libretro.LibretroStateSlots
import com.nendo.argosy.util.Logger
import dagger.Lazy
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CatalogSync"

/**
 * Sync planner for the catalog server, which keeps one blob per unit and decides by hashes
 * alone (server: docs/SAVE_SYNC.md).
 *
 * The inventory is everything this device holds: for each downloaded game, its save channels
 * (the `save_sync` rows for the game's resolved emulator) and its cached states. Each item
 * carries the hash of the server version it last synced (`baseHash`) and whether the local
 * bytes moved since. Save operations flow into the launcher's existing queue, conflict and
 * download machinery through [ReconcilePlan]; state operations are kept aside and applied by
 * [applyStateOperations], which the coordinator calls right after the plan.
 */
@Singleton
class CatalogSyncStrategy @Inject constructor(
    private val apiClient: RomMApiClient,
    private val gameDao: GameDao,
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val stateCacheDao: StateCacheDao,
    private val saveSyncApiClient: Lazy<SaveSyncApiClient>,
    private val saveCacheManager: Lazy<SaveCacheManager>,
    private val stateCacheManager: Lazy<StateCacheManager>,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val coreVersionExtractor: CoreVersionExtractor,
) : SaveSyncStrategy {

    private class GameContext(
        val game: GameEntity,
        val rommId: Long,
        val localEmulatorId: String,
        /** The core the game runs on, for the server label. */
        val coreId: String?,
        /** What the state use cases record as the cache row's core; the host id for the built-in emulator. */
        val cacheCoreId: String?,
        val serverEmulator: String,
    )

    @Volatile
    private var pendingStateOperations: List<RomMReconcileOperation> = emptyList()

    @Volatile
    private var contextsByRom: Map<Long, GameContext> = emptyMap()

    override suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan {
        val api = apiClient.api ?: return ReconcilePlan.EMPTY
        val client = saveSyncApiClient.get()
        val ownerUserId = syncPreferencesRepository.getRommUserId()

        val contexts = mutableListOf<GameContext>()
        for (game in gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())) {
            val rommId = game.rommId?.takeIf { it > 0 } ?: continue
            if (game.localPath == null) continue
            val localEmulatorId = client.resolveEmulatorForGame(game) ?: continue
            val coreId = client.resolveCoreForGame(game, localEmulatorId)
            contexts += GameContext(
                game = game,
                rommId = rommId,
                localEmulatorId = localEmulatorId,
                coreId = coreId,
                cacheCoreId = coreVersionExtractor.getCoreIdForEmulator(localEmulatorId, game.platformSlug),
                serverEmulator = EmulatorRegistry.toServerEmulator(localEmulatorId, coreId),
            )
        }
        contextsByRom = contexts.associateBy { it.rommId }

        val items = mutableListOf<RomMReconcileItem>()
        for (ctx in contexts) {
            items += saveItems(ctx, ownerUserId)
            items += stateItems(ctx, ownerUserId)
        }

        val response = try {
            api.reconcile(
                RomMReconcilePayload(
                    deviceId = client.getDeviceId(),
                    presentRoms = contexts.map { it.rommId },
                    items = items,
                )
            )
        } catch (e: Exception) {
            Logger.warn(TAG, "plan: reconcile request failed: ${e.message}")
            return ReconcilePlan.EMPTY
        }
        if (!response.isSuccessful) {
            Logger.warn(TAG, "plan: reconcile returned ${response.code()}")
            return ReconcilePlan.EMPTY
        }
        val operations = response.body()?.operations.orEmpty()

        pendingStateOperations = operations.filter { it.kind == RECONCILE_KIND_STATE }
        val saveOps = operations
            .filter { it.kind == RECONCILE_KIND_SAVE }
            .map { it.toReconcileOperation() }
        val plan = ReconcilePlan(sessionId = null, operations = saveOps)
        Logger.info(
            TAG,
            "plan: games=${contexts.size} items=${items.size} saves(up=${plan.uploadCount} down=${plan.downloadCount} " +
                "conflict=${plan.conflictCount} noop=${plan.noOpCount}) states=${pendingStateOperations.size}"
        )
        return plan
    }

    private suspend fun saveItems(ctx: GameContext, ownerUserId: Long?): List<RomMReconcileItem> {
        val rows = saveSyncDao.getByGame(ctx.game.id, ownerUserId)
            .filter { it.emulatorId == ctx.localEmulatorId }
            .filter { it.channelName?.startsWith("state_", ignoreCase = true) != true }
        val byChannel = rows.groupBy { channelKey(it.channelName) }
        return byChannel.map { (channel, candidates) ->
            // Two spellings of the autosave channel can each hold a row; the one that has
            // synced is the one whose base hash means something.
            val row = candidates.sortedWith(
                compareByDescending<SaveSyncEntity> { it.lastUploadedHash != null }.thenByDescending { it.id }
            ).first()
            val file = row.localSavePath?.let(::File)?.takeIf { it.exists() }
            val hasLocal = file != null
            val localMd5 = file?.let { saveCacheManager.get().calculateLocalSaveHash(it.absolutePath) }
            val dirty = saveCacheDao.hasNeedingRemoteSync(ctx.game.id, row.channelName) ||
                (row.channelName == null && saveCacheDao.hasNeedingRemoteSync(ctx.game.id, SaveSyncApiClient.AUTOSAVE_SLOT_NAME))
            val localChanged = hasLocal && (
                row.localContentHash == null || localMd5 != row.localContentHash || dirty
                )
            RomMReconcileItem(
                romId = ctx.rommId,
                kind = RECONCILE_KIND_SAVE,
                emulator = ctx.serverEmulator,
                channel = channel,
                slot = 0,
                hasLocal = hasLocal,
                localHash = file?.takeIf { it.isFile }?.let(::sha256OfFile),
                baseHash = row.lastUploadedHash,
                localChanged = localChanged,
            )
        }
    }

    /**
     * One cache row per unit. Rows written by earlier builds can differ only in how they spell
     * the channel or which core they recorded; the newest capture is the unit's current bytes.
     */
    private suspend fun unitRows(ctx: GameContext, ownerUserId: Long?): List<StateCacheEntity> =
        stateCacheDao.getByGameAndEmulator(ctx.game.id, ctx.localEmulatorId, ownerUserId)
            .filter { it.slotNumber != LibretroStateSlots.RESUME_SLOT }
            .groupBy { it.slotNumber to channelKey(it.channelName) }
            .map { (_, rows) -> rows.maxWith(compareBy<StateCacheEntity> { it.cachedAt }.thenBy { it.id }) }

    private suspend fun stateItems(ctx: GameContext, ownerUserId: Long?): List<RomMReconcileItem> {
        val manager = stateCacheManager.get()
        return unitRows(ctx, ownerUserId)
            .map { state ->
                val file = manager.getCacheFile(state)?.takeIf { it.exists() }
                val localHash = file?.let(::sha256OfFile)
                val localChanged = state.rommSaveId == null ||
                    state.syncStatus == StateCacheEntity.STATUS_PENDING_UPLOAD ||
                    state.syncStatus == StateCacheEntity.STATUS_LOCAL_NEWER ||
                    (state.lastUploadedHash != null && localHash != null && localHash != state.lastUploadedHash)
                RomMReconcileItem(
                    romId = ctx.rommId,
                    kind = RECONCILE_KIND_STATE,
                    emulator = ctx.serverEmulator,
                    channel = channelKey(state.channelName),
                    slot = state.slotNumber,
                    hasLocal = file != null,
                    localHash = localHash,
                    baseHash = state.lastUploadedHash,
                    localChanged = localChanged,
                )
            }
    }

    /**
     * Carries out the state half of the last plan. Uploads go through the pending queue so a
     * failure is retried like any other; downloads land in the cache and are placed into a
     * live slot by the next launch; a conflict is settled by whichever side is newer, since
     * two snapshots cannot be merged.
     */
    suspend fun applyStateOperations(): Int {
        val ops = pendingStateOperations
        pendingStateOperations = emptyList()
        if (ops.isEmpty()) return 0
        val api = apiClient.api ?: return 0
        val manager = stateCacheManager.get()
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        var applied = 0
        var queuedUpload = false

        for (op in ops) {
            val ctx = contextsByRom[op.romId] ?: run {
                Logger.debug(TAG, "states: no game context for romId=${op.romId} (known=${contextsByRom.keys})")
                null
            } ?: continue
            Logger.debug(TAG, "states: ${op.action} romId=${op.romId} slot=${op.slot} channel=${op.channel} emulator=${op.emulator} asset=${op.assetId} reason='${op.reason}'")
            if (!emulatorMatches(op.emulator, ctx)) {
                Logger.debug(TAG, "states: skipping ${op.action} for romId=${op.romId}, emulator ${op.emulator} is not ${ctx.serverEmulator}")
                continue
            }
            val local = unitRows(ctx, ownerUserId)
                .firstOrNull { it.slotNumber == op.slot && channelKey(it.channelName) == channelKey(op.channel) }

            val action = when (op.action) {
                "conflict" -> {
                    val serverAt = op.serverUpdatedAt?.let { SaveSyncApiClient.parseTimestampOrNull(it) }
                    val localAt = local?.cachedAt
                    if (localAt != null && (serverAt == null || localAt.isAfter(serverAt))) "upload" else "download"
                }
                else -> op.action
            }

            when (action) {
                "upload" -> {
                    val row = local ?: run {
                        Logger.debug(TAG, "states: nothing local to upload for slot ${op.slot}")
                        null
                    } ?: continue
                    if (op.action == "conflict") {
                        // Local is the newer snapshot; say so explicitly, or the guard that
                        // protects the other device's version would refuse it again.
                        val romBaseName = ctx.game.localPath?.let { File(it).nameWithoutExtension } ?: ctx.game.platformSlug
                        val result = manager.uploadStateToRomM(row, ctx.rommId, romBaseName, api, overwrite = true)
                        if (result is StateCacheManager.StateCloudResult.Success) applied++
                        else Logger.warn(TAG, "states: conflict upload for slot ${op.slot} failed: $result")
                    } else if (manager.queueStateForUpload(row.id, ctx.game.id, ctx.rommId, ctx.localEmulatorId)) {
                        queuedUpload = true
                        applied++
                    }
                }
                "download" -> {
                    val assetId = op.assetId ?: continue
                    val fileName = op.fileName ?: continue
                    val result = manager.downloadStateFromRomM(
                        rommStateId = assetId,
                        fileName = fileName,
                        api = api,
                        gameId = ctx.game.id,
                        platformSlug = ctx.game.platformSlug,
                        emulatorId = ctx.localEmulatorId,
                        coreId = ctx.cacheCoreId,
                        serverState = RomMState(
                            id = assetId,
                            romId = op.romId,
                            userId = 0,
                            emulator = op.emulator,
                            fileName = fileName,
                            fileSizeBytes = op.serverSize ?: 0,
                            downloadPath = "/api/states/$assetId/content",
                            updatedAt = op.serverUpdatedAt ?: Instant.now().toString(),
                            channel = op.channel,
                            stateSlot = op.slot,
                            contentHash = op.serverHash,
                        ),
                    )
                    if (result is StateCacheManager.StateCloudResult.Success) {
                        applied++
                        retireSiblings(ctx, ownerUserId, op.slot, op.channel, assetId)
                    } else {
                        Logger.warn(TAG, "states: download of asset $assetId failed: $result")
                    }
                }
                "no_op" -> {
                    // Identical bytes on both sides: make the server's hash the base so the next
                    // pass compares like with like (rows written before this protocol hold MD5).
                    val row = local ?: continue
                    val serverHash = op.serverHash ?: continue
                    if (row.lastUploadedHash != serverHash || row.rommSaveId != op.assetId) {
                        stateCacheDao.updateSyncState(
                            id = row.id,
                            rommSaveId = op.assetId ?: row.rommSaveId,
                            syncStatus = StateCacheEntity.STATUS_SYNCED,
                            serverUpdatedAt = op.serverUpdatedAt?.let { SaveSyncApiClient.parseTimestampOrNull(it) }?.toEpochMilli()
                                ?: row.serverUpdatedAt?.toEpochMilli(),
                            lastUploadedHash = serverHash
                        )
                    }
                }
                else -> Unit
            }
        }
        if (queuedUpload) manager.processPendingStateUploads()
        Logger.info(TAG, "states: applied $applied of ${ops.size} operations")
        return applied
    }

    /**
     * After a download the unit has one authoritative row; older rows for the same slot that
     * only differ in channel spelling or core id would otherwise keep reporting stale bases.
     */
    private suspend fun retireSiblings(ctx: GameContext, ownerUserId: Long?, slot: Int, channel: String, assetId: Long) {
        val rows = stateCacheDao.getByGameAndEmulator(ctx.game.id, ctx.localEmulatorId, ownerUserId)
            .filter { it.slotNumber == slot && channelKey(it.channelName) == channelKey(channel) }
        val keep = rows.filter { it.rommSaveId == assetId }.maxByOrNull { it.cachedAt } ?: return
        for (row in rows) {
            if (row.id == keep.id || row.isLocked) continue
            stateCacheManager.get().deleteState(row.id)
            Logger.debug(TAG, "states: retired duplicate cache row ${row.id} for slot $slot")
        }
    }

    private fun emulatorMatches(label: String?, ctx: GameContext): Boolean =
        label == null || label == ctx.serverEmulator ||
            // Rows written before states carried the core label were labelled with the host.
            label == ctx.localEmulatorId || label == EmulatorRegistry.BUILTIN_ID

    private fun channelKey(channel: String?): String =
        if (SaveSyncApiClient.isAutosaveChannel(channel)) SaveSyncApiClient.AUTOSAVE_SLOT_NAME else channel!!

    private fun RomMReconcileOperation.toReconcileOperation(): ReconcileOperation = ReconcileOperation(
        action = when (action) {
            "upload" -> ReconcileAction.UPLOAD
            "download" -> ReconcileAction.DOWNLOAD
            "conflict" -> ReconcileAction.CONFLICT
            else -> ReconcileAction.NO_OP
        },
        romId = romId,
        saveId = assetId,
        fileName = fileName ?: "",
        slot = channel,
        emulator = emulator,
        reason = reason,
        serverUpdatedAt = serverUpdatedAt,
        serverContentHash = serverHash,
    )

    private fun sha256OfFile(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
