package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.screens.home.delegates.HomeDiscoveryLoader
import com.nendo.argosy.ui.screens.home.delegates.HomeExploreDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePagingTest {
    private fun game(id: Long) = HomeGameUi(
        id = id, title = "Game $id", platformId = 1, platformSlug = "snes",
        platformDisplayName = "SNES", coverPath = null, backgroundPath = null,
        developer = null, releaseYear = null, genre = "Sport", isFavorite = false,
        isDownloaded = true, lastPlayedAt = null
    )

    @Test fun `later rounds add fresh games with stable distinct section keys`() {
        var state = ExploreState().append(ExplorePage(listOf(game(1), game(1)), true))
        state = state.copy(cursor = ExploreGenre.entries.size)
            .append(ExplorePage(listOf(game(1), game(2)), false))
        assertEquals(listOf(1L, 2L), state.rows.flatMap { it.games }.map { it.id })
        assertEquals(2, state.rows.map { it.key }.distinct().size)
        assertTrue(state.rows.last().continuation)
        assertTrue(ExploreGenre.SPORTS in state.finishedGenres)
    }

    @Test fun `empty catalog terminates without adding empty genre shelves`() {
        var state = ExploreState()
        repeat(ExploreGenre.entries.size) { state = state.append(ExplorePage(emptyList(), false)) }
        assertTrue(state.exhausted)
        assertTrue(state.rows.isEmpty())
    }

    @Test fun `concurrent scroll requests load only one batch`() = runTest {
        val state = MutableStateFlow(HomeUiState(discoveryData = DiscoveryData(loading = false)))
        val loader = mockk<HomeDiscoveryLoader>()
        coEvery { loader.genrePage(any(), any(), any(), any()) } returns ExplorePage(listOf(game(1)), true)
        val delegate = HomeExploreDelegate(state, loader, this)
        delegate.loadMore()
        delegate.loadMore()
        runCurrent()
        assertEquals(3, state.value.explore.rows.size)
        coVerify(exactly = 3) { loader.genrePage(any(), any(), any(), any()) }
    }

    @Test fun `retry keeps completed rows and retries the failed cursor`() = runTest {
        val state = MutableStateFlow(HomeUiState(discoveryData = DiscoveryData(loading = false)))
        val loader = mockk<HomeDiscoveryLoader>()
        coEvery { loader.genrePage(any(), any(), any(), any()) } returns ExplorePage(listOf(game(1)), true)
        coEvery { loader.genrePage("Adventure", 0, null, any()) } throws IllegalStateException("offline")
        val delegate = HomeExploreDelegate(state, loader, this)
        delegate.loadMore()
        runCurrent()
        assertEquals(1, state.value.explore.rows.size)
        assertEquals(1, state.value.explore.cursor)
        assertTrue(state.value.explore.failed)
        delegate.loadMore()
        runCurrent()
        coVerify(exactly = 1) { loader.genrePage("Adventure", 0, null, any()) }
        coEvery { loader.genrePage("Adventure", 0, null, any()) } returns ExplorePage(listOf(game(2)), true)
        delegate.loadMore(retry = true)
        runCurrent()
        assertFalse(state.value.explore.failed)
        assertEquals(4, state.value.explore.rows.size)
        assertEquals(4, state.value.explore.rows.map { it.key }.distinct().size)
    }

    @Test fun `reset cancels the previous platform request`() = runTest {
        val state = MutableStateFlow(HomeUiState(discoveryData = DiscoveryData(loading = false)))
        val loader = mockk<HomeDiscoveryLoader>()
        val pending = CompletableDeferred<ExplorePage>()
        coEvery { loader.genrePage(any(), any(), any(), any()) } coAnswers { pending.await() }
        val delegate = HomeExploreDelegate(state, loader, this)
        delegate.loadMore()
        runCurrent()
        delegate.reset()
        pending.complete(ExplorePage(listOf(game(99)), true))
        runCurrent()
        assertEquals(ExploreState(), state.value.explore)
    }
}
