package com.nendo.argosy.data.catalog

import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMLibrarySyncService
import com.nendo.argosy.data.remote.romm.RomMShelf
import com.nendo.argosy.util.Logger
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ShelfRepository"
private const val PAGE = 40

/**
 * The server-curated Home shelves. Definitions are fetched once per session; a shelf's games are
 * pulled page by page through the sync path, so a shelf game is an ordinary local game downstream.
 * Order is the server's and is held here, because Room's own sort would re-alphabetize a
 * rating-ordered shelf.
 */
@Singleton
class ShelfRepository @Inject constructor(
    private val apiClient: RomMApiClient,
    private val connectionManager: RomMConnectionManager,
    private val librarySync: Lazy<RomMLibrarySyncService>,
) {
    private val mutex = Mutex()
    private var definitions: List<RomMShelf>? = null
    private val orderedIds = mutableMapOf<String, MutableList<Long>>()
    private val exhausted = mutableSetOf<String>()

    val connectionState get() = connectionManager.connectionState

    fun isCatalogOnly(): Boolean = connectionManager.getCapabilities().catalogOnly

    suspend fun definitions(strict: Boolean = false): List<RomMShelf> {
        if (!isCatalogOnly()) return emptyList()
        definitions?.let { return it }
        return mutex.withLock {
            definitions?.let { return@withLock it }
            val api = apiClient.api ?: if (strict) error("Catalog is disconnected") else return@withLock emptyList()
            val fetched = try {
                val response = api.getShelves()
                if (strict && !response.isSuccessful) throw retrofit2.HttpException(response)
                response.body().orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (strict) throw e
                Logger.warn(TAG, "definitions: ${e.message}")
                emptyList()
            }
            if (fetched.isNotEmpty()) definitions = fetched
            fetched
        }
    }

    /**
     * The shelf's games as local ids in server order, fetching through [through] rows. With
     * [platformSlugs] the server ranks only games on those platforms, so a device that follows a
     * few systems still gets a full row instead of the top of a list it cannot show.
     */
    suspend fun gamesFor(
        shelf: RomMShelf,
        platformSlugs: Collection<String> = emptyList(),
        through: Int = PAGE,
        strict: Boolean = false,
        owned: Boolean = false
    ): List<Long> {
        if (!isCatalogOnly()) return emptyList()
        val slugs = platformSlugs.sorted()
        val key = "${shelf.key}|${slugs.joinToString(",")}|$owned"
        val params = wireParams(shelf.params).toMutableMap()
        if (owned) params["owned"] = "true"
        if (slugs.isNotEmpty()) params["platform_slugs"] = slugs.joinToString(",")
        mutex.withLock {
            val ids = orderedIds.getOrPut(key) { mutableListOf() }
            while (ids.size < through && key !in exhausted) {
                val page = librarySync.get().fetchRomsByParams(
                    params = params,
                    limit = PAGE,
                    offset = ids.size,
                    strict = strict
                )
                if (page.isEmpty()) {
                    exhausted.add(key)
                    break
                }
                ids.addAll(page.filter { it !in ids })
                if (page.size < PAGE) exhausted.add(key)
            }
            return ids.take(through)
        }
    }

    suspend fun genrePage(
        genre: String,
        platformSlugs: Collection<String>,
        offset: Int,
        limit: Int,
        owned: Boolean
    ): List<Long> {
        if (platformSlugs.isEmpty()) return emptyList()
        val params = mutableMapOf(
            "genre" to genre,
            "platform_slugs" to platformSlugs.sorted().joinToString(","),
            "order_by" to "rating",
            "order_dir" to "desc"
        )
        if (owned) params["owned"] = "true"
        return librarySync.get().fetchRomsByParams(params, limit, offset, strict = true)
    }

    /** Sync a slice of a genre into the local store, as recommendation candidates. */
    suspend fun seedGenre(genre: String, minRating: Int = 75, count: Int = 30) {
        if (!isCatalogOnly()) return
        librarySync.get().fetchRomsByParams(
            params = mapOf(
                "genre" to genre,
                "min_rating" to minRating.toString(),
                "order_by" to "rating_count",
                "order_dir" to "desc"
            ),
            limit = count,
            offset = 0
        )
    }

    suspend fun randomLocalId(platformSlugs: Collection<String> = emptyList(), owned: Boolean = false): Long? {
        if (!isCatalogOnly()) return null
        return librarySync.get().fetchRandomRom(platformSlugs, owned)
    }

    fun reset() {
        definitions = null
        orderedIds.clear()
        exhausted.clear()
    }

    private fun wireParams(params: Map<String, Any?>): Map<String, String> =
        params.mapNotNull { (k, v) ->
            val text = when (v) {
                null -> return@mapNotNull null
                is Boolean -> v.toString()
                is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
                else -> v.toString()
            }
            k to text
        }.toMap()
}
