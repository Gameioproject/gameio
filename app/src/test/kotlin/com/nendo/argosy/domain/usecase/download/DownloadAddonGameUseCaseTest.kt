package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.addon.*
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.squareup.moshi.Moshi
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DownloadAddonGameUseCaseTest {
    private val addons = mockk<AddonRepository>()
    private val downloads = mockk<DownloadManager>(relaxed = true)
    private val format = AddonFormat(Moshi.Builder().build())
    private val useCase = DownloadAddonGameUseCase(addons, format, downloads)
    private val game = GameEntity(id = 7, platformId = 19, platformSlug = "snes", title = "Game", sortTitle = "game",
        localPath = null, rommId = 44000019, igdbId = 123, source = GameSource.ROMM_REMOTE, rommFileName = "Game (USA).zip")
    private val match = AddonSourceMatch("test", "Test", AddonSource("one", "http", "Game (USA).zip",
        AddonLocator(url = "https://example.org/game.zip"), size = 10000), "a".repeat(64), "123:snes")

    @Test fun `queued addon preserves game rom and platform identity with source snapshot`() = runTest {
        coEvery { addons.lookup(123, "snes", false) } returns AddonLookupResult(listOf(match), enabledAddonCount = 1)
        assertEquals(DownloadResult.Queued, useCase(game))
        coVerify {
            downloads.enqueueDownload(gameId = 7, rommId = 44000019, fileName = "Game (USA).zip", gameTitle = "Game",
                platformSlug = "snes", coverPath = null, expectedSizeBytes = 10000, isMultiFileRom = false,
                selectedFileIds = null, addonSourceJson = match { format.parseMatch(it!!) == this@DownloadAddonGameUseCaseTest.match })
        }
    }

    @Test fun `missing addon asks import without queuing a legacy server download`() = runTest {
        coEvery { addons.lookup(any(), any(), any()) } returns AddonLookupResult()
        assertEquals(DownloadResult.Error(DownloadGameFailureReason.Addon(AddonFailure.NO_ADDONS)), useCase(game))
        coVerify(exactly = 0) { downloads.enqueueDownload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `expired explicit selection cannot silently choose a different edition`() = runTest {
        coEvery { addons.lookup(any(), any(), any()) } returns AddonLookupResult(listOf(match), enabledAddonCount = 1)
        val stale = match.copy(source = match.source.copy(id = "changed"))
        assertEquals(DownloadResult.Error(DownloadGameFailureReason.Addon(AddonFailure.REMOVED)), useCase(game, stale))
    }

    @Test fun `existing server filename wins default choice to preserve edition`() = runTest {
        val other = match.copy(source = match.source.copy(id = "other", filename = "Game (Europe).zip"))
        coEvery { addons.lookup(any(), any(), any()) } returns AddonLookupResult(listOf(other, match), enabledAddonCount = 1)
        useCase(game)
        coVerify { downloads.enqueueDownload(any(), any(), "Game (USA).zip", any(), any(), any(), any(), any(), any(), any()) }
    }
}
