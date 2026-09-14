package com.nendo.argosy.data.addon

import com.nendo.argosy.data.storage.FileAccessLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepository @Inject constructor(
    private val store: AddonStore,
    private val format: AddonFormat,
    private val http: AddonHttpClient,
    private val cache: AddonShardCache,
    private val files: FileAccessLayer
) {
    val addons = store.addons
    val unreadableCount = store.unreadableCount
    private val requests = Semaphore(3)
    private val shardLocks = List(256) { Mutex() }

    suspend fun load() = store.load()

    suspend fun importFile(path: String, expectedId: String? = null): InstalledAddon = withContext(Dispatchers.IO) {
        val bytes = try {
            files.getInputStream(path)?.use { it.readAddonBytes(AddonFormat.MAX_MANIFEST_BYTES) }
                ?: throw AddonException(AddonFailure.FILE_UNREADABLE)
        } catch (e: IOException) {
            throw AddonException(AddonFailure.FILE_UNREADABLE, e)
        }
        import(bytes, expectedId)
    }

    suspend fun import(bytes: ByteArray, expectedId: String? = null): InstalledAddon = withContext(Dispatchers.IO) {
        val manifest = format.parseManifest(bytes)
        if (expectedId != null && manifest.id != expectedId) throw AddonException(AddonFailure.INVALID_MANIFEST)
        val previous = store.load().find { it.manifest.id == manifest.id }
        val record = store.import(bytes)
        previous?.let { cache.invalidate(format.fingerprint(it.manifest)) }
        record
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = store.setEnabled(id, enabled)

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val record = store.load().find { it.manifest.id == id }
        store.remove(id)
        record?.let { cache.invalidate(format.fingerprint(it.manifest)) }
    }

    suspend fun lookup(igdbId: Long, platformSlug: String, refresh: Boolean = false): AddonLookupResult =
        withContext(Dispatchers.IO) {
            val key = AddonFormat.key(igdbId, platformSlug)
            val enabled = store.load().filter { it.enabled }
            coroutineScope {
                val results = enabled.map { addon -> async { lookupAddon(addon.manifest, key, refresh) } }.awaitAll()
                val current = store.load().filter { it.enabled }.associateBy { it.manifest.id }
                AddonLookupResult(
                    sources = results.flatMap { it.sources }.filter { match ->
                        current[match.addonId]?.let { format.fingerprint(it.manifest) } == match.manifestFingerprint
                    },
                    failures = results.flatMap { it.failures }.filter { it.addonId in current },
                    enabledAddonCount = current.size
                )
            }
        }

    suspend fun manifestFor(match: AddonSourceMatch): AddonManifest {
        return store.load().find { it.enabled && it.manifest.id == match.addonId &&
            format.fingerprint(it.manifest) == match.manifestFingerprint }?.manifest
            ?: throw AddonException(AddonFailure.REMOVED)
    }

    private suspend fun lookupAddon(manifest: AddonManifest, key: String, refresh: Boolean): AddonLookupResult {
        return try {
            val fingerprint = format.fingerprint(manifest)
            val shard = AddonFormat.partition(key)
            val entries = shardLocks[shard.toInt(16)].withLock {
                val cached = if (refresh) null else cachedShard(manifest, fingerprint, shard, key)
                cached ?: requests.withPermit {
                    val bytes = withTimeout(30_000) {
                        http.bytes(format.shardUrl(manifest, key), AddonFormat.MAX_SHARD_BYTES) { url ->
                            runCatching { format.approvedUrl(url.toString(), manifest.allowedHosts) }.isSuccess
                        }
                    }
                    val parsed = format.parseShard(bytes, manifest, shard)
                    cache.write(fingerprint, shard, bytes)
                    parsed
                }
            }
            AddonLookupResult(sources = entries.entries[key].orEmpty().map {
                AddonSourceMatch(manifest.id, manifest.name, it, fingerprint, key)
            })
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            AddonLookupResult(failures = listOf(AddonLookupFailure(manifest.id, AddonFailure.NETWORK)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AddonLookupResult(failures = listOf(AddonLookupFailure(manifest.id,
                (e as? AddonException)?.reason ?: AddonFailure.NETWORK)))
        }
    }

    private fun cachedShard(manifest: AddonManifest, fingerprint: String, shard: String, key: String): AddonShard? {
        val bytes = cache.read(fingerprint, shard, POSITIVE_CACHE_MS) ?: return null
        val parsed = runCatching { format.parseShard(bytes, manifest, shard) }.getOrNull() ?: return null
        if (parsed.entries[key].isNullOrEmpty() && cache.read(fingerprint, shard, NEGATIVE_CACHE_MS) == null) return null
        return parsed
    }

    companion object {
        private const val POSITIVE_CACHE_MS = 24 * 60 * 60 * 1000L
        private const val NEGATIVE_CACHE_MS = 5 * 60 * 1000L
    }
}
