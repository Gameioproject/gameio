package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMLibrarySyncService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFavoritesRepositoryTest {
    private val games = mockk<GameRepository>(relaxed = true)
    private val sync = mockk<RomMLibrarySyncService>()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val repository = SetupFavoritesRepository(games, sync, preferences)

    @Test
    fun `metadata search preserves catalog page order and identity`() = runTest {
        coEvery { sync.fetchRomsByParams(any(), 30, 30, true) } returns listOf(4L, 2L)
        coEvery { games.getByIds(listOf(4L, 2L)) } returns listOf(game(2), game(4))
        assertEquals(listOf(4L, 2L), repository.page(" Mario ", 30, 30).map { it.id })
        coVerify { sync.fetchRomsByParams(mapOf("search_term" to "Mario"), 30, 30, true) }
    }

    @Test
    fun `setup favorite changes invalidate old recommendations before normal sync write`() = runTest {
        repository.setFavorite(4, true)
        coVerifyOrder {
            preferences.clearRecommendations()
            games.updateFavoriteWithSync(4, true)
        }
    }

    private fun game(id: Long) = GameEntity(
        id = id, platformId = 1, title = "Game $id", sortTitle = "Game $id",
        localPath = null, rommId = id, igdbId = id, source = GameSource.ROMM_REMOTE
    )
}
