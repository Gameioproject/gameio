package com.nendo.argosy.domain.usecase.recommendation

import com.nendo.argosy.data.catalog.ShelfRepository
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.PlatformRepository
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FavoriteRecommendationTest {
    private val games = mockk<GameDao>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val platforms = mockk<PlatformRepository>()
    private val syncPreferences = mockk<SyncPreferencesRepository>()
    private val shelves = mockk<ShelfRepository>(relaxed = true)
    private lateinit var useCase: GenerateRecommendationsUseCase

    @Before
    fun setup() {
        every { preferences.preferences } returns flowOf(UserPreferences())
        coEvery { syncPreferences.getRommUserId() } returns 8L
        coEvery { games.getPlayedGames(8L) } returns emptyList()
        coEvery { games.getFavorites(8L) } returns emptyList()
        coEvery { games.getUnplayedInstalledGames(8L) } returns emptyList()
        coEvery { platforms.getSyncEnabledPlatforms() } returns listOf(
            PlatformEntity(id = 1, slug = "snes", name = "Super Nintendo", shortName = "SNES", romExtensions = "smc")
        )
        every { shelves.isCatalogOnly() } returns true
        useCase = GenerateRecommendationsUseCase(games, preferences, platforms, syncPreferences, Lazy { shelves })
    }

    @Test
    fun `favorite only profile generates related games without invented play history`() = runTest {
        val favorite = game(1, favorite = true)
        coEvery { games.getFavorites(8L) } returns listOf(favorite)
        coEvery { games.getUnplayedUndownloadedGames(8L) } returns listOf(favorite, game(2))
        val ids = useCase()
        assertEquals(listOf(2L), ids)
        coVerify { shelves.seedGenre("Adventure", any(), any()) }
        coVerify { preferences.setRecommendations(listOf(2L), any()) }
        assertEquals(0, favorite.playCount)
        assertNull(favorite.lastPlayed)
    }

    @Test
    fun `no favorites or played games leaves recommendations empty`() = runTest {
        assertTrue(useCase().isEmpty())
        coVerify(exactly = 0) { shelves.seedGenre(any(), any(), any()) }
        coVerify(exactly = 0) { preferences.setRecommendations(any(), any()) }
    }

    @Test
    fun `favorite candidates never repeat inside recommendation row`() = runTest {
        coEvery { games.getFavorites(8L) } returns listOf(game(1, favorite = true))
        coEvery { games.getUnplayedUndownloadedGames(8L) } returns listOf(game(1, favorite = true))
        assertTrue(useCase().isEmpty())
    }

    @Test
    fun `skipping optional platform following still uses favorite taste`() = runTest {
        coEvery { platforms.getSyncEnabledPlatforms() } returns emptyList()
        coEvery { games.getFavorites(8L) } returns listOf(game(1, favorite = true))
        coEvery { games.getUnplayedUndownloadedGames(8L) } returns listOf(game(2))
        assertEquals(listOf(2L), useCase())
    }

    private fun game(id: Long, favorite: Boolean = false) = GameEntity(
        id = id, platformId = 1, platformSlug = "snes", title = "Game $id", sortTitle = "Game $id",
        localPath = null, rommId = id, igdbId = id, source = GameSource.ROMM_REMOTE,
        isFavorite = favorite, genre = "Adventure", rating = 90f
    )
}
