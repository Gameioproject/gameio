package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.platformRomRoots
import com.nendo.argosy.data.remote.romm.RomMLibrarySyncService
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SearchNormalizer
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCatalogDiscovery @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val fileAccess: FileAccessLayer,
    private val catalog: Lazy<RomMLibrarySyncService>,
    private val overlays: GameUserOverlayWriter,
    private val attribution: StorageAttributionRepository,
    private val catalogIdentityLock: CatalogIdentityLock
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var enrichmentJob: Job? = null
    private val attempts = mutableMapOf<Long, Long>()

    suspend fun discover(base: File, onlyPlatformId: Long? = null): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val platforms = platformDao.getAllPlatforms()
            val snapshots = platforms.filter { onlyPlatformId == null || it.id == onlyPlatformId }.mapNotNull { platform ->
                val extensions = platform.romExtensions.split(',').map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }.toSet() + if (M3uManager.supportsM3u(platform.slug)) setOf("m3u") else emptySet()
                if (extensions.isEmpty()) return@mapNotNull null
                val files = LocalCatalogFiles.candidates(list(platformRomRoots(platform, base, platforms)), extensions, fileAccess)
                Snapshot(platform, extensions, files)
            }
            val discovered = catalogIdentityLock.withLock { claim(snapshots) }
            if (discovered > 0) attribution.markDirty(StorageCategory.GAMES)
            scheduleEnrichment()
            discovered
        }
    }

    private suspend fun claim(snapshots: List<Snapshot>): Int {
        val stored = gameDao.getAllGames().toMutableList()
        val ownership = LocalPathOwnership(gameDao.getLocalFileOwners(), fileAccess)
        var discovered = 0
        val owner = overlays.activeOwnerId()
        for ((platform, extensions, files) in snapshots) {
            val previousCount = discovered
            for (file in files) {
                if (ownership.isClaimed(file.path) || !fileAccess.exists(file.path) || !fileAccess.canRead(file.path)) continue
                val title = LocalCatalogFiles.title(file.name, extensions)
                val matches = stored.filter { it.platformId == platform.id && LocalCatalogFiles.key(it.title) == LocalCatalogFiles.key(title) }
                val match = matches.singleOrNull()
                if (match != null) {
                    if (match.localPath == null) {
                        val source = if (match.rommId == null) GameSource.LOCAL_ONLY else GameSource.ROMM_SYNCED
                        gameDao.updateLocalPath(match.id, file.path, source)
                        stored[stored.indexOf(match)] = match.copy(localPath = file.path, source = source)
                    } else {
                        val existing = gameFileDao.getByGameIdAndFileName(match.id, file.name)
                            .firstOrNull { it.filePath == file.path && it.rommFileId == null }
                        if (existing != null) {
                            gameFileDao.updateLocalPath(existing.id, file.path, existing.downloadedAt ?: Instant.now())
                        } else {
                            gameFileDao.insert(GameFileEntity(gameId = match.id,
                                fileName = file.name, filePath = file.path, category = VariantCategory.GAME.key,
                                fileSize = file.size, localPath = file.path, downloadedAt = Instant.now(),
                                isLaunchTarget = true, isMultiDisc = file.extension.equals("m3u", ignoreCase = true),
                                m3uPath = file.path.takeIf { file.extension.equals("m3u", ignoreCase = true) }))
                        }
                    }
                    ownership.claim(file.path, match.id)
                    if (owner != null) overlays.grantMembership(owner, match.id)
                } else {
                    val game = GameEntity(platformId = platform.id, platformSlug = platform.slug,
                        title = title, sortTitle = SearchNormalizer.normalize(title), localPath = file.path,
                        rommId = null, rommFileName = file.name, igdbId = null, source = GameSource.LOCAL_ONLY,
                        fileSizeBytes = file.size, isIdentified = false,
                        isMultiDisc = file.extension.equals("m3u", ignoreCase = true),
                        m3uPath = file.path.takeIf { file.extension.equals("m3u", ignoreCase = true) })
                    val id = gameDao.insert(game)
                    stored += game.copy(id = id)
                    ownership.claim(file.path, id)
                    if (owner != null) overlays.grantMembership(owner, id)
                }
                discovered++
            }
            if (discovered > previousCount) platformDao.updateGameCount(platform.id, gameDao.countByPlatform(platform.id, owner))
        }
        return discovered
    }

    private fun list(roots: List<File>): List<FileInfo> {
        val result = mutableListOf<FileInfo>()
        val pending = ArrayDeque(roots.map { it.path })
        val rootPaths = roots.map { it.path }.toSet()
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val path = pending.removeFirst()
            val identity = runCatching { fileAccess.getTransformedFile(path).canonicalPath }.getOrDefault(path)
            if (!visited.add(identity)) continue
            fileAccess.listFiles(path).orEmpty().forEach { file ->
                if (!file.name.startsWith('.')) {
                    if (file.isDirectory) {
                        val isContentFolder = path !in rootPaths && file.name.lowercase() in
                            (VariantCategory.CATEGORY_FOLDER_NAMES - setOf(VariantCategory.GAME.key, VariantCategory.UNKNOWN.key))
                        if (!isContentFolder) pending.add(file.path)
                    } else result += file
                }
            }
        }
        return result.distinctBy { it.path }
    }

    private data class Snapshot(val platform: PlatformEntity, val extensions: Set<String>, val files: List<FileInfo>)

    private fun scheduleEnrichment() {
        if (enrichmentJob?.isActive == true) return
        enrichmentJob = scope.launch {
            val now = System.currentTimeMillis()
            val pending = gameDao.getGamesWithLocalPath().filter {
                it.source == GameSource.LOCAL_ONLY && it.rommId == null &&
                    now - (attempts[it.id] ?: 0) >= RETRY_INTERVAL_MILLIS
            }
            for (game in pending) {
                attempts[game.id] = System.currentTimeMillis()
                try {
                    withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) {
                        catalog.get().fetchExactLocalCatalogMatch(game.platformId, game.title)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Logger.debug("LocalCatalogDiscovery", "Metadata unavailable for local game ${game.id}")
                }
            }
        }
    }

    companion object {
        private const val RETRY_INTERVAL_MILLIS = 10 * 60 * 1000L
        private const val LOOKUP_TIMEOUT_MILLIS = 8_000L
    }
}
