package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.preferences.HomeLibraryFilter
import org.junit.Assert.*
import org.junit.Test

class DiscoveryNavigationTest {
    private fun game(id: Long, platform: Long = 1, installed: Boolean = true) = HomeGameUi(
        id = id, title = "Game $id", platformId = platform, platformSlug = "snes",
        platformDisplayName = "SNES", coverPath = null, backgroundPath = null,
        developer = null, releaseYear = null, genre = null, isFavorite = false,
        isDownloaded = installed, lastPlayedAt = 100L
    )

    private fun state() = HomeUiState(
        platforms = listOf(HomePlatformUi(1, "snes", "SNES", "SNES", "SNES", null),
            HomePlatformUi(2, "n64", "N64", "N64", "N64", null)),
        recentGames = listOf(game(1), game(2)),
        discoveryData = DiscoveryData(topRated = listOf(game(3), game(4)), loading = false),
        libraryFilter = HomeLibraryFilter.ALL,
        focusedGameIndex = 1
    )

    @Test fun `down enters feed then ranking without changing platform`() {
        val initial = state()
        val feeds = DiscoveryNavigation.vertical(initial, 1)
        val ranking = DiscoveryNavigation.vertical(feeds, 1)
        assertEquals(HomeRow.Continue, ranking.currentRow)
        assertEquals(DiscoveryFocus.FIRST_ROW, ranking.discoveryFocus.zone)
        assertEquals(3L, ranking.focusedGame?.id)
        val restored = DiscoveryNavigation.vertical(DiscoveryNavigation.vertical(ranking, -1), -1)
        assertEquals(2L, restored.focusedGame?.id)
    }

    @Test fun `home always exists and picks is not a platform page`() {
        val empty = HomeUiState()
        assertEquals(HomeRow.Continue, empty.availableRows.first())
        assertFalse(state().availableRows.contains(HomeRow.Recommendations))
        assertFalse(state().availableRows.contains(HomeRow.Favorites))
    }

    @Test fun `platform and local filters apply to every discovery source`() {
        val initial = state().copy(currentRow = HomeRow.Platform(0), libraryFilter = HomeLibraryFilter.LIBRARY)
        assertEquals(listOf(1L), initial.discoveryGames(listOf(
            game(1), game(2, platform = 2), game(3, installed = false), game(4).copy(isHidden = true)
        )).map { it.id })
    }

    @Test fun `empty rows remain navigable and focus does not wrap to tools`() {
        var current = HomeUiState()
        repeat(30) { current = DiscoveryNavigation.vertical(current, 1) }
        assertEquals(DiscoveryFocus.FIRST_ROW + current.discoverySections.lastIndex, current.discoveryFocus.zone)
        assertEquals(0, current.focusedGameIndex)
        assertNull(current.focusedGame)
    }

    @Test fun `changing feed resets lower row focus`() {
        val ranked = state().copy(discoveryFocus = DiscoveryFocus(zone = 4), focusedGameIndex = 12)
        val updated = DiscoveryNavigation.selectFeed(ranked, DiscoveryFeed.FAVORITES)
        assertEquals(DiscoveryFocus.FEEDS, updated.discoveryFocus.zone)
        assertEquals(0, updated.focusedGameIndex)
        assertEquals("favorites", updated.discoverySections.single().key)
    }
    @Test fun `explore omits empty personalized rows after applying platform filter`() {
        val empty = state()
        assertEquals(listOf("top-rated", "explore-tail"), empty.discoverySections.map { it.key })
        val populated = empty.copy(recommendedGames = listOf(game(7)), favoriteGames = listOf(game(8)))
        assertEquals(listOf("top-rated", "recommended", "favorites", "explore-tail"),
            populated.discoverySections.map { it.key })
        val filtered = populated.copy(currentRow = HomeRow.Platform(1))
        assertEquals(listOf("top-rated", "explore-tail"), filtered.discoverySections.map { it.key })
    }

    @Test fun `removing an earlier empty row preserves focused section and game`() {
        val previous = state().copy(
            recommendedGames = listOf(game(7)), favoriteGames = listOf(game(8), game(9)),
            discoveryFocus = DiscoveryFocus(zone = DiscoveryFocus.FIRST_ROW + 2,
                rowIndexes = mapOf(DiscoveryFocus.FIRST_ROW + 2 to 1)), focusedGameIndex = 1
        )
        val updated = DiscoveryNavigation.reconcileSections(previous, previous.copy(recommendedGames = emptyList()))
        assertEquals(DiscoveryFocus.FIRST_ROW + 1, updated.discoveryFocus.zone)
        assertEquals(9L, updated.focusedGame?.id)
        assertEquals(1, updated.discoveryFocus.rowIndexes[DiscoveryFocus.FIRST_ROW + 1])
    }

    @Test fun `removing the last favorite selects the next visible section safely`() {
        val previous = state().copy(favoriteGames = listOf(game(8)),
            discoveryFocus = DiscoveryFocus(zone = DiscoveryFocus.FIRST_ROW + 1), focusedGameIndex = 0)
        val updated = DiscoveryNavigation.reconcileSections(previous, previous.copy(favoriteGames = emptyList()))
        assertEquals("explore-tail", updated.discoverySections[updated.discoveryFocus.zone - DiscoveryFocus.FIRST_ROW].key)
        assertEquals(0, updated.focusedGameIndex)
        assertNull(updated.focusedGame)
    }

}
