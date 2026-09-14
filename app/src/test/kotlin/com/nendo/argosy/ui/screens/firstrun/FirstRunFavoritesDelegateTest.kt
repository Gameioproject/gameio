package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SetupFavoritesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirstRunFavoritesDelegateTest {
    private val repository = mockk<SetupFavoritesRepository>()
    private val platforms = mockk<PlatformRepository>()
    private lateinit var delegate: FirstRunFavoritesDelegate

    @Before
    fun setup() {
        coEvery { repository.favorites() } returns emptyList()
        coEvery { repository.page(any(), any(), any()) } returns listOf(game(1), game(2))
        coEvery { repository.setFavorite(any(), any()) } returns Unit
        every { platforms.observeAllPlatforms() } returns flowOf(emptyList())
        delegate = FirstRunFavoritesDelegate(repository, platforms)
    }

    @Test
    fun `existing favorites remain selected and lead suggestions`() = runTest {
        coEvery { repository.favorites() } returns listOf(game(9, true))
        delegate.enter(this)
        advanceUntilIdle()
        assertEquals(setOf(9L), delegate.state.value.selectedIds)
        assertEquals(listOf(9L, 1L, 2L), delegate.state.value.games.map { it.id })
        coVerify(exactly = 0) { repository.setFavorite(any(), any()) }
    }

    @Test
    fun `selection and deselection write actual favorites`() = runTest {
        delegate.enter(this)
        advanceUntilIdle()
        delegate.toggle(1, this)
        advanceUntilIdle()
        assertEquals(setOf(1L), delegate.state.value.selectedIds)
        coVerify(exactly = 1) { repository.setFavorite(1, true) }
        delegate.toggle(1, this)
        advanceUntilIdle()
        assertTrue(delegate.state.value.selectedIds.isEmpty())
        coVerify(exactly = 1) { repository.setFavorite(1, false) }
    }

    @Test
    fun `failed write preserves selection and remains retryable`() = runTest {
        coEvery { repository.setFavorite(1, true) } throws IllegalStateException("disk")
        delegate.enter(this)
        advanceUntilIdle()
        delegate.toggle(1, this)
        advanceUntilIdle()
        assertTrue(delegate.state.value.saveFailed)
        assertTrue(delegate.state.value.selectedIds.isEmpty())
        assertNull(delegate.state.value.savingId)
        coEvery { repository.setFavorite(1, true) } returns Unit
        delegate.toggle(1, this)
        advanceUntilIdle()
        assertEquals(setOf(1L), delegate.state.value.selectedIds)
        assertFalse(delegate.state.value.saveFailed)
    }

    @Test
    fun `pending local write completes before leaving or double toggle`() = runTest {
        val saved = CompletableDeferred<Unit>()
        coEvery { repository.setFavorite(1, true) } coAnswers { saved.await() }
        delegate.enter(this)
        advanceUntilIdle()
        delegate.toggle(1, this)
        runCurrent()
        var left = false
        delegate.leave { left = true }
        delegate.toggle(1, this)
        assertFalse(left)
        saved.complete(Unit)
        advanceUntilIdle()
        delegate.leave { left = true }
        assertTrue(left)
        coVerify(exactly = 1) { repository.setFavorite(1, true) }
    }

    @Test
    fun `skip cancels an outstanding page with zero selections`() = runTest {
        coEvery { repository.page(any(), any(), any()) } coAnswers { delay(10_000); listOf(game(3)) }
        delegate.enter(this)
        runCurrent()
        var left = false
        delegate.leave { left = true }
        advanceUntilIdle()
        assertTrue(left)
        assertFalse(delegate.state.value.isLoading)
        assertTrue(delegate.state.value.games.isEmpty())
    }

    @Test
    fun `catalog failure retains saved choices and retry loads suggestions`() = runTest {
        coEvery { repository.favorites() } returns listOf(game(9, true))
        coEvery { repository.page(any(), any(), any()) } throws IllegalStateException("offline")
        delegate.enter(this)
        advanceUntilIdle()
        assertTrue(delegate.state.value.loadFailed)
        assertEquals(setOf(9L), delegate.state.value.selectedIds)
        assertEquals(listOf(9L), delegate.state.value.games.map { it.id })
        coEvery { repository.page(any(), any(), any()) } returns listOf(game(1))
        delegate.loadMore(this)
        advanceUntilIdle()
        assertFalse(delegate.state.value.loadFailed)
        assertEquals(listOf(9L, 1L), delegate.state.value.games.map { it.id })
    }

    @Test
    fun `superseded search cannot replace newer results`() = runTest {
        delegate.enter(this)
        advanceUntilIdle()
        coEvery { repository.page("slow", 0, 30) } coAnswers { delay(5_000); listOf(game(4)) }
        coEvery { repository.page("fast", 0, 30) } returns listOf(game(5))
        delegate.setQuery("slow", this)
        advanceTimeBy(300)
        runCurrent()
        delegate.setQuery("fast", this)
        advanceUntilIdle()
        assertEquals("fast", delegate.state.value.query)
        assertEquals(listOf(5L), delegate.state.value.games.map { it.id })
        assertFalse(delegate.state.value.isLoading)
    }

    @Test
    fun `paging uses server offsets while removing duplicate identities`() = runTest {
        coEvery { repository.page("", 0, 30) } returns (1L..30L).map { game(it) }
        coEvery { repository.page("", 30, 30) } returns listOf(game(30), game(31))
        delegate.enter(this)
        advanceUntilIdle()
        assertTrue(delegate.state.value.hasMore)
        delegate.loadMore(this)
        advanceUntilIdle()
        assertEquals(31, delegate.state.value.games.size)
        assertEquals(32, delegate.state.value.nextOffset)
        assertFalse(delegate.state.value.hasMore)
    }

    @Test
    fun `controller reaches rows and skip and keyboard guards navigation`() = runTest {
        delegate.enter(this)
        advanceUntilIdle()
        delegate.moveVertical(1)
        assertEquals(3, delegate.state.value.focusedIndex)
        delegate.confirm(this) { fail("Game selection must not leave setup") }
        advanceUntilIdle()
        assertEquals(setOf(1L), delegate.state.value.selectedIds)
        delegate.moveVertical(-1)
        delegate.moveHorizontal(-1)
        assertEquals(2, delegate.state.value.focusedIndex)
        var left = false
        delegate.confirm(this) { left = true }
        assertTrue(left)
        delegate.openSearch()
        delegate.moveVertical(1)
        delegate.moveHorizontal(1)
        assertEquals(0, delegate.state.value.focusedIndex)
        delegate.confirm(this) { fail("Keyboard must capture input") }
        assertTrue(delegate.state.value.keyboardOpen)
    }

    private fun game(id: Long, favorite: Boolean = false) = GameEntity(
        id = id, platformId = 1, platformSlug = "snes", title = "Game $id", sortTitle = "Game $id",
        localPath = null, rommId = id, igdbId = id, source = GameSource.ROMM_REMOTE, isFavorite = favorite
    )
}
