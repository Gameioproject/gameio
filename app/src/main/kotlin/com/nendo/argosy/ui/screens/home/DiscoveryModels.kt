package com.nendo.argosy.ui.screens.home

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.HomeLibraryFilter
import com.nendo.argosy.domain.model.HomeLayoutKind

enum class DiscoveryFeed(@StringRes val labelRes: Int) {
    EXPLORE(R.string.discovery_explore),
    LIBRARY(R.string.discovery_library),
    FAVORITES(R.string.discovery_favorites)
}

data class DiscoveryFocus(
    val zone: Int = HERO,
    val control: Int = 0,
    val heroIndex: Int = 0,
    val feed: DiscoveryFeed = DiscoveryFeed.EXPLORE,
    val rowIndexes: Map<Int, Int> = emptyMap()
) {
    companion object {
        const val TOOLS = -3
        const val PLATFORMS = -2
        const val ACTIONS = -1
        const val HERO = 0
        const val FEEDS = 1
        const val FIRST_ROW = 2
    }
}

data class DiscoveryData(
    val platformId: Long? = null,
    val topRated: List<HomeGameUi> = emptyList(),
    val library: List<HomeGameUi> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val filter: HomeLibraryFilter = HomeLibraryFilter.ALL
)

data class DiscoverySection(
    val key: String,
    @StringRes val titleRes: Int,
    val games: List<HomeGameUi>,
    @StringRes val emptyRes: Int,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val continuation: Boolean = false
)

val HomeUiState.isDiscoveryHome: Boolean
    get() = layoutKind == HomeLayoutKind.CAROUSEL &&
        (currentRow == HomeRow.Continue || currentRow is HomeRow.Platform)

fun HomeUiState.discoveryGames(games: List<HomeGameUi>): List<HomeGameUi> {
    val allowed = platforms.map { it.id }.toSet()
    return games.filter { game ->
        !game.isHidden && game.platformId in allowed &&
            (currentPlatform == null || game.platformId == currentPlatform?.id) &&
            when (libraryFilter) {
                HomeLibraryFilter.ALL -> true
                HomeLibraryFilter.DOWNLOADABLE -> game.isPlayable || (game.fileSizeBytes ?: 0) > 0
                HomeLibraryFilter.LIBRARY -> game.isPlayable
            }
    }.distinctBy { it.id }
}

val HomeUiState.discoveryRecent: List<HomeGameUi>
    get() = discoveryGames(recentGames).filter { it.lastPlayedAt != null }

val HomeUiState.discoveryHero: List<HomeGameUi>
    get() = (discoveryRecent + discoveryGames(discoveryData.library) + discoveryTopRated)
        .distinctBy { it.id }.ifEmpty {
            discoveryGames(platformItems.mapNotNull { (it as? HomeRowItem.Game)?.game })
        }.take(12)

val HomeUiState.discoveryHeroGame: HomeGameUi?
    get() = discoveryHero.getOrNull(
        if (discoveryFocus.zone == DiscoveryFocus.HERO) focusedGameIndex else discoveryFocus.heroIndex
    ) ?: discoveryHero.firstOrNull()

val HomeUiState.discoveryTopRated: List<HomeGameUi>
    get() = if (libraryFilter == HomeLibraryFilter.LIBRARY) {
        discoveryGames(discoveryData.library).filter { it.rating != null }.sortedByDescending { it.rating }
    } else if (discoveryData.platformId == currentPlatform?.id && discoveryData.filter == libraryFilter) {
        discoveryGames(discoveryData.topRated)
    } else emptyList()

val HomeUiState.discoverySections: List<DiscoverySection>
    get() {
        val favorites = DiscoverySection(
            "favorites", R.string.discovery_favorites, discoveryGames(favoriteGames),
            R.string.discovery_empty_favorites
        )
        return when (discoveryFocus.feed) {
            DiscoveryFeed.FAVORITES -> listOf(favorites)
            DiscoveryFeed.LIBRARY -> listOf(
                DiscoverySection("library", R.string.discovery_on_device,
                    discoveryGames(discoveryData.library), R.string.discovery_empty_library)
            )
            DiscoveryFeed.EXPLORE -> listOf(
                DiscoverySection("top-rated", R.string.discovery_top_rated, discoveryTopRated,
                    R.string.discovery_empty_catalog, discoveryData.loading,
                    discoveryData.failed),
                DiscoverySection("recommended", R.string.discovery_recommended,
                    discoveryGames(recommendedGames), R.string.discovery_empty_recommended),
                favorites
            ) + explore.rows.map { it.copy(games = discoveryGames(it.games)) } + DiscoverySection(
                "explore-tail", R.string.explore_keep_going, emptyList(),
                if (explore.exhausted) R.string.explore_end else R.string.explore_more,
                loading = explore.loading, failed = explore.failed
            )
        }
    }

val HomeUiState.discoveryItems: List<HomeRowItem>
    get() = when {
        discoveryFocus.zone >= DiscoveryFocus.FIRST_ROW ->
            discoverySections.getOrNull(discoveryFocus.zone - DiscoveryFocus.FIRST_ROW)
                ?.games.orEmpty().map { HomeRowItem.Game(it) }
        else -> discoveryHero.map { HomeRowItem.Game(it) }
    }

object DiscoveryNavigation {
    fun vertical(state: HomeUiState, delta: Int): HomeUiState {
        val focus = state.discoveryFocus
        val zone = (focus.zone + delta).coerceIn(
            DiscoveryFocus.TOOLS, DiscoveryFocus.FIRST_ROW + state.discoverySections.lastIndex
        )
        val indexes = focus.rowIndexes + (focus.zone to state.focusedGameIndex)
        val heroIndex = if (focus.zone == DiscoveryFocus.HERO) state.focusedGameIndex else focus.heroIndex
        val updated = state.copy(discoveryFocus = focus.copy(
            zone = zone, control = 0, heroIndex = heroIndex, rowIndexes = indexes
        ))
        return updated.copy(focusedGameIndex = (indexes[zone] ?: 0)
            .coerceIn(0, updated.currentItems.lastIndex.coerceAtLeast(0)))
    }

    fun horizontal(state: HomeUiState, delta: Int): HomeUiState {
        val count = when (state.discoveryFocus.zone) {
            DiscoveryFocus.TOOLS, DiscoveryFocus.ACTIONS -> 3
            DiscoveryFocus.PLATFORMS -> state.availableRows.size
            DiscoveryFocus.FEEDS -> DiscoveryFeed.entries.size + 1
            else -> return state.copy(focusedGameIndex = (state.focusedGameIndex + delta)
                .coerceIn(0, state.currentItems.lastIndex.coerceAtLeast(0)))
        }
        return state.copy(discoveryFocus = state.discoveryFocus.copy(
            control = (state.discoveryFocus.control + delta).coerceIn(0, (count - 1).coerceAtLeast(0))
        ))
    }

    fun selectFeed(state: HomeUiState, feed: DiscoveryFeed): HomeUiState = state.copy(
        discoveryFocus = state.discoveryFocus.copy(feed = feed, zone = DiscoveryFocus.FEEDS,
            control = feed.ordinal, rowIndexes = emptyMap(),
            heroIndex = if (state.discoveryFocus.zone == DiscoveryFocus.HERO) state.focusedGameIndex
                else state.discoveryFocus.heroIndex), focusedGameIndex = 0
    )
}
