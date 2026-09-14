package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.addon.AddonFailure
import com.nendo.argosy.data.addon.AddonLookupFailure
import com.nendo.argosy.data.addon.AddonLookupResult
import com.nendo.argosy.data.addon.AddonRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameSourcesDelegateTest {
    private val addons = mockk<AddonRepository>()
    private val games = mockk<GameRepository>()
    private val romm = mockk<RomMRepository>()
    private val delegate = GameSourcesDelegate(addons, games, romm)
    private val game = GameEntity(id = 1, platformId = 19, platformSlug = "snes", title = "Game", sortTitle = "game",
        localPath = null, rommId = 12300019, igdbId = 123, source = GameSource.ROMM_REMOTE)

    @Before fun setup() {
        every { romm.usesAddonSources() } returns true
        coEvery { games.getById(any()) } returns game
        coEvery { games.validateAndDiscoverGame(any()) } returns false
    }

    @Test fun `local game is playable without looking up any provider`() = runTest {
        coEvery { games.validateAndDiscoverGame(1) } returns true
        delegate.load(this, 1)
        runCurrent()
        assertTrue(delegate.state.value.onDevice)
        assertFalse(delegate.state.value.loading)
        coVerify(exactly = 0) { addons.lookup(any(), any(), any()) }
    }

    @Test fun `outage keeps error distinct from confirmed missing sources`() = runTest {
        coEvery { addons.lookup(any(), any(), any()) } returns AddonLookupResult(
            failures = listOf(AddonLookupFailure("test", AddonFailure.NETWORK)), enabledAddonCount = 1)
        delegate.load(this, 1)
        runCurrent()
        assertFalse(delegate.state.value.result.isConfirmedMissing)
        assertEquals(AddonFailure.NETWORK, delegate.state.value.result.failures.single().reason)
    }

    @Test fun `late cancelled lookup cannot overwrite the next game's state`() = runTest {
        val releaseOld = CompletableDeferred<Unit>()
        coEvery { games.getById(2) } returns game.copy(id = 2, igdbId = 456)
        coEvery { addons.lookup(123, "snes", false) } coAnswers {
            withContext(NonCancellable) { releaseOld.await() }
            AddonLookupResult(enabledAddonCount = 9)
        }
        coEvery { addons.lookup(456, "snes", false) } returns AddonLookupResult(enabledAddonCount = 1)
        delegate.load(this, 1)
        runCurrent()
        delegate.load(this, 2)
        runCurrent()
        releaseOld.complete(Unit)
        runCurrent()
        assertEquals(2L, delegate.state.value.gameId)
        assertEquals(1, delegate.state.value.result.enabledAddonCount)
        assertNull(delegate.state.value.failure)
    }

    @Test fun `retry refreshes sources and keeps open modal and focus in range`() = runTest {
        coEvery { addons.lookup(any(), any(), any()) } returns AddonLookupResult()
        delegate.load(this, 1)
        runCurrent()
        delegate.show()
        delegate.move(-1)
        assertEquals(2, delegate.state.value.focusedIndex)
        delegate.load(this, 1, refresh = true)
        runCurrent()
        assertTrue(delegate.state.value.visible)
        assertEquals(0, delegate.state.value.focusedIndex)
        coVerify { addons.lookup(123, "snes", true) }
    }
}
