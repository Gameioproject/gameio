package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.util.Logger
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CatalogSaveSync"

/**
 * Planner for catalog-only servers, which speak the classic saves protocol through the
 * asset-store shim but have no negotiate session. The plan is computed client-side from a
 * per-rom listing: absent on server uploads, identical hashes no-op, and anything else is a
 * conflict for the auto-resolver, which already carries the rules for local-vs-server age.
 * Server saves with no matching local row become downloads; the effect applier skips them
 * safely when the game has no local ROM yet.
 *
 * Games on disk with no local save at all never enter the inventory, so they are covered by a
 * second pass over one listing of everything the account holds on the server. That is the
 * shape of a fresh sign-in (signing out removes the account's saves) and of a second device;
 * without it the server's saves stayed unseen until a launch or the six-hourly scan.
 */
@Singleton
class CatalogSaveSyncStrategy @Inject constructor(
    private val apiClient: RomMApiClient,
    private val gameDao: GameDao,
    private val saveSyncApiClient: Lazy<SaveSyncApiClient>,
) : SaveSyncStrategy {

    private suspend fun canonicalEmulator(romId: Long, raw: String?): String? {
        val game = gameDao.getByRommId(romId) ?: return raw
        val client = saveSyncApiClient.get()
        val resolvedId = if (raw.isNullOrBlank() || raw == "default") {
            client.resolveEmulatorForGame(game) ?: return raw
        } else {
            raw
        }
        val core = client.resolveCoreForGame(game, resolvedId)
        return EmulatorRegistry.toServerEmulator(resolvedId, core)
    }

    override suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan {
        val api = apiClient.api ?: return ReconcilePlan.EMPTY
        val byRom = localInventory.groupBy { it.romId }
        val operations = mutableListOf<ReconcileOperation>()
        var romsQueried = 0

        for ((romId, locals) in byRom) {
            val response = try {
                api.getSavesByRom(romId)
            } catch (e: Exception) {
                Logger.warn(TAG, "plan: listing failed for romId=$romId: ${e.message}")
                continue
            }
            if (!response.isSuccessful) {
                Logger.warn(TAG, "plan: listing for romId=$romId returned ${response.code()}")
                continue
            }
            romsQueried++
            val servers = response.body().orEmpty()
            val matchedServerIds = mutableSetOf<Long>()

            val canonicalLocals = locals
                .map { it to canonicalEmulator(romId, it.emulator) }
                .sortedByDescending { (local, _) -> local.contentHash != null }
                .distinctBy { (local, canonical) -> canonical to local.slot }

            for ((local, canonical) in canonicalLocals) {
                val match = servers.firstOrNull {
                    (it.emulator ?: "") == (canonical ?: "") && it.slot == local.slot
                }
                if (match == null) {
                    operations.add(
                        ReconcileOperation(
                            action = ReconcileAction.UPLOAD,
                            romId = romId,
                            fileName = local.fileName,
                            slot = local.slot,
                            emulator = canonical,
                            reason = "not on server",
                        )
                    )
                    continue
                }
                matchedServerIds.add(match.id)
                if (match.contentHash != null && match.contentHash == local.contentHash) {
                    operations.add(
                        ReconcileOperation(
                            action = ReconcileAction.NO_OP,
                            romId = romId,
                            saveId = match.id,
                            fileName = local.fileName,
                            slot = local.slot,
                            emulator = canonical,
                            reason = "hashes equal",
                        )
                    )
                } else {
                    operations.add(
                        ReconcileOperation(
                            action = ReconcileAction.CONFLICT,
                            romId = romId,
                            saveId = match.id,
                            fileName = match.fileName,
                            slot = local.slot,
                            emulator = canonical,
                            reason = "local and server differ",
                            serverUpdatedAt = match.updatedAt,
                            serverContentHash = match.contentHash,
                        )
                    )
                }
            }

            for (server in servers) {
                if (server.id in matchedServerIds) continue
                operations.add(
                    ReconcileOperation(
                        action = ReconcileAction.DOWNLOAD,
                        romId = romId,
                        saveId = server.id,
                        fileName = server.fileName,
                        slot = server.slot,
                        emulator = server.emulator,
                        reason = "server only",
                        serverUpdatedAt = server.updatedAt,
                        serverContentHash = server.contentHash,
                    )
                )
            }
        }

        operations.addAll(serverOnlyForGamesWithoutSaves(api, byRom.keys))

        val plan = ReconcilePlan(sessionId = null, operations = operations)
        Logger.info(
            TAG,
            "plan: roms=$romsQueried up=${plan.uploadCount} down=${plan.downloadCount} " +
                "conflict=${plan.conflictCount} noop=${plan.noOpCount}"
        )
        return plan
    }

    /**
     * Download operations for server saves of downloaded games that have no local save row.
     * One request covers every such game: the account's whole save list, filtered to the roms
     * on disk that the per-rom pass above did not already ask about.
     */
    private suspend fun serverOnlyForGamesWithoutSaves(
        api: RomMApi,
        alreadyQueried: Set<Long>
    ): List<ReconcileOperation> {
        val onDisk = gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())
            .mapNotNull { it.rommId }
            .filter { it > 0 && it !in alreadyQueried }
            .toSet()
        if (onDisk.isEmpty()) return emptyList()

        val response = try {
            api.getAllSaves()
        } catch (e: Exception) {
            Logger.warn(TAG, "plan: listing the account's saves failed: ${e.message}")
            return emptyList()
        }
        if (!response.isSuccessful) {
            Logger.warn(TAG, "plan: listing the account's saves returned ${response.code()}")
            return emptyList()
        }

        val found = response.body().orEmpty().filter { it.romId in onDisk }
        if (found.isNotEmpty()) {
            Logger.info(TAG, "plan: ${found.size} server saves for ${found.map { it.romId }.toSet().size} downloaded games with no local save")
        }
        return found.map { server ->
            ReconcileOperation(
                action = ReconcileAction.DOWNLOAD,
                romId = server.romId,
                saveId = server.id,
                fileName = server.fileName,
                slot = server.slot,
                emulator = server.emulator,
                reason = "server only, no local save",
                serverUpdatedAt = server.updatedAt,
                serverContentHash = server.contentHash,
            )
        }
    }
}
