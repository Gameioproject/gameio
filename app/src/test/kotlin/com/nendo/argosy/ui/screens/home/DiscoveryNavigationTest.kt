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
}
