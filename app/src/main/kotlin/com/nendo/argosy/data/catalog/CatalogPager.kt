package com.nendo.argosy.data.catalog

import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMLibrarySyncService
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CatalogPager"
private const val PAGE_SIZE = 100

/** Smallest request worth a round trip; a Home rail is exactly this many. */
private const val MIN_PAGE = 20

/** How far past the last visible row to keep loaded, so scrolling does not stall on a request. */
private const val PREFETCH_ROWS = 200

/**
 * Feeds a catalog-only platform into the local store one page at a time, as far as the UI has
 * actually scrolled. Rows land in Room, so the screens observing it grow on their own; this only
 * decides when to ask the server for more.
 */
@Singleton
class CatalogPager @Inject constructor(
    private val librarySync: dagger.Lazy<RomMLibrarySyncService>,
    private val connectionManager: RomMConnectionManager,
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository
) {
    private val mutex = Mutex()

    /** Rows known to be stored per platform; seeded from Room the first time a platform is asked for. */
    private val loaded = mutableMapOf<Long, Int>()

    /** The server's count, which is the point at which paging stops. */
    private val totals = mutableMapOf<Long, Int>()

    /** Platforms whose last request came back short, meaning the server has nothing more. */
    private val exhausted = mutableSetOf<Long>()

    /** The owned (downloadable) subset is paged separately; its offsets are its own. */
    private val ownedLoaded = mutableMapOf<Long, Int>()
    private val ownedExhausted = mutableSetOf<Long>()

    /** The A-Z index per platform; it only changes when the server's catalog does. */
    private val sectionCache =
        mutableMapOf<Long, List<com.nendo.argosy.data.remote.romm.RomMNameSection>>()

    fun isCatalogOnly(): Boolean = connectionManager.getCapabilities().catalogOnly

    private val _settledPlatforms = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Platforms the pager has been through at least once, whatever it found. A screen reading an
     * empty list out of Room uses this to tell "the first page is still on its way" from "there is
     * nothing here", and only the second deserves an empty state.
     */
    val settledPlatforms: StateFlow<Set<Long>> = _settledPlatforms.asStateFlow()

    private fun markSettled(platformId: Long) {
        if (platformId !in _settledPlatforms.value) _settledPlatforms.update { it + platformId }
    }

    /**
     * Make sure the platform has rows stored through [index] plus [prefetch] more, so scrolling
     * does not stall on a request. Safe to call on every scroll frame: it returns immediately once
     * the window is already covered.
     *
     * A caller that is about to paint passes `prefetch = 0` and asks for exactly the rows it will
     * show: the first paint then waits on one small request rather than on a 300-row window, and
     * the next caller (the library, a scroll) tops the window up.
     */
    suspend fun ensureThrough(platformId: Long, index: Int, prefetch: Int = PREFETCH_ROWS): Boolean {
        if (!isCatalogOnly()) {
            markSettled(platformId)
            return false
        }
        if (platformId in exhausted) return false

        val target = index + prefetch
        // Cheap pre-check outside the lock so scrolling does not serialise on it.
        loaded[platformId]?.let { if (it >= target) return false }

        return mutex.withLock {
            try {
                if (platformId in exhausted) return@withLock false

                var have = loaded.getOrPut(platformId) { gameRepository.countByPlatform(platformId) }
                val total = totals.getOrPut(platformId) {
                    platformRepository.getById(platformId)?.gameCount ?: Int.MAX_VALUE
                }
                if (have >= target || have >= total) return@withLock false

                var wrote = 0
                while (have < target && have < total) {
                    // Ask for the shortfall, not a whole page: on a slow link the row's first
                    // paint is bounded by this one response and the writes it brings. A floor
                    // keeps a nearly covered window from costing a round trip for a row or two.
                    val ask = (target - have).coerceIn(MIN_PAGE, PAGE_SIZE)
                    val fetched = librarySync.get().fetchCatalogPage(
                        platformId = platformId,
                        limit = ask,
                        offset = have
                    )
                    if (fetched <= 0) {
                        exhausted.add(platformId)
                        break
                    }
                    have += fetched
                    wrote += fetched
                    loaded[platformId] = have
                    if (fetched < ask) {
                        exhausted.add(platformId)
                        break
                    }
                }
                if (wrote > 0) Logger.info(TAG, "platform $platformId now holds $have rows (of $total)")
                wrote > 0
            } finally {
                markSettled(platformId)
            }
        }
    }

    /**
     * Ask the server for a query's matches and store them, so the caller can read results back out
     * of Room. Returns how many rows landed. [platformId] null searches the whole catalog.
     */
    suspend fun searchServer(query: String, platformId: Long?, limit: Int = 100): Int {
        if (!isCatalogOnly()) return 0
        return librarySync.get().fetchCatalogSearch(query, platformId, limit)
    }

    /**
     * The platform's A-Z index, straight from the server, so the sidebar can offer every letter
     * rather than only the ones paged in so far. Cached per platform.
     */
    suspend fun sections(platformId: Long): List<com.nendo.argosy.data.remote.romm.RomMNameSection> {
        if (!isCatalogOnly()) return emptyList()
        sectionCache[platformId]?.let { return it }
        val fetched = librarySync.get().fetchCatalogSections(platformId)
        if (fetched.isNotEmpty()) sectionCache[platformId] = fetched
        return fetched
    }

    /**
     * Load the rows a letter covers, plus the neighbouring letters, so arriving at a jump target
     * does not land on an empty grid and scrolling either way is already warm.
     */
    suspend fun ensureSection(platformId: Long, label: String): Boolean {
        if (!isCatalogOnly()) return false
        val all = sections(platformId)
        val index = all.indexOfFirst { it.label == label }
        if (index < 0) return false

        val from = all[(index - 1).coerceAtLeast(0)].offset
        val last = all[(index + 1).coerceAtMost(all.lastIndex)]
        val through = last.offset + last.count
        return fetchRange(platformId, from, through)
    }

    /** Fill a specific window of a platform, independent of how far the UI has scrolled. */
    private suspend fun fetchRange(platformId: Long, from: Int, through: Int): Boolean =
        mutex.withLock {
            val total = totals.getOrPut(platformId) {
                platformRepository.getById(platformId)?.gameCount ?: Int.MAX_VALUE
            }
            var offset = from
            val end = through.coerceAtMost(total)
            var wrote = 0
            while (offset < end) {
                val fetched = librarySync.get().fetchCatalogPage(
                    platformId = platformId,
                    limit = PAGE_SIZE,
                    offset = offset
                )
                if (fetched <= 0) break
                offset += fetched
                wrote += fetched
                if (fetched < PAGE_SIZE) break
            }
            // A jump fills a window, so the contiguous-from-zero count is no longer a safe
            // assumption; re-read it from the store rather than adding to it.
            if (wrote > 0) {
                loaded[platformId] = gameRepository.countByPlatform(platformId)
                Logger.info(TAG, "platform $platformId filled $from..$end (+$wrote)")
            }
            markSettled(platformId)
            wrote > 0
        }

    /**
     * Pull the platform's downloadable games (the server's owned subset) through [want] rows. The
     * owned games sit anywhere in name order, so paging the whole catalog would only ever surface
     * the few that happen to fall inside the scrolled window; this asks for exactly that subset.
     */
    suspend fun ensureAvailable(platformId: Long, want: Int): Boolean {
        if (!isCatalogOnly() || platformId in ownedExhausted) {
            markSettled(platformId)
            return false
        }
        val have = ownedLoaded[platformId] ?: 0
        if (have >= want) return false
        return mutex.withLock {
            var offset = ownedLoaded[platformId] ?: 0
            var wrote = 0
            while (offset < want) {
                val fetched = librarySync.get().fetchCatalogPage(
                    platformId = platformId,
                    limit = PAGE_SIZE,
                    offset = offset,
                    ownedOnly = true
                )
                if (fetched <= 0) {
                    ownedExhausted.add(platformId)
                    break
                }
                offset += fetched
                wrote += fetched
                ownedLoaded[platformId] = offset
                if (fetched < PAGE_SIZE) {
                    ownedExhausted.add(platformId)
                    break
                }
            }
            if (wrote > 0) Logger.info(TAG, "platform $platformId owned subset now $offset rows")
            markSettled(platformId)
            wrote > 0
        }
    }

    /** Forget what is cached, so a resync or a server change starts paging afresh. */
    fun reset() {
        loaded.clear()
        totals.clear()
        exhausted.clear()
        ownedLoaded.clear()
        ownedExhausted.clear()
        sectionCache.clear()
        _settledPlatforms.value = emptySet()
    }
}
