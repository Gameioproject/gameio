package com.nendo.argosy.data.download

import android.content.Context
import com.nendo.argosy.data.addon.*
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import io.mockk.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AddonDownloadOwnershipTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>(relaxed = true)
    private val games = mockk<GameDao>(relaxed = true)
    private val queue = mockk<DownloadQueueDao>(relaxed = true)
    private val platforms = mockk<PlatformDao>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>()
    private val romm = mockk<com.nendo.argosy.data.remote.romm.RomMRepository>(relaxed = true)
    private val format = AddonFormat(Moshi.Builder().build())
    private val storage = AddonDownloadStorage()
    private lateinit var platform: File
    private lateinit var staging: RomStagingManager
    private lateinit var downloads: DownloadManager
    private var linkedPath: String? = null
    private val game = GameEntity(id = 7, platformId = 18, platformSlug = "nes", title = "Game", sortTitle = "game",
        localPath = null, rommId = 7000018, igdbId = 7, source = com.nendo.argosy.data.model.GameSource.ROMM_REMOTE)

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        linkedPath = null
        platform = temporary.newFolder("nes")
        every { context.filesDir } returns temporary.newFolder("private")
        every { context.getExternalFilesDir(null) } returns temporary.newFolder("external")
        every { preferences.userPreferences } returns flowOf(UserPreferences(romStoragePath = temporary.root.path, maxConcurrentDownloads = 0))
        coEvery { platforms.getAllBySlug("nes") } returns listOf(PlatformEntity(
            id = 18, slug = "nes", name = "NES", shortName = "NES", romExtensions = "nes,zip", customRomPath = platform.path
        ))
        coEvery { games.getById(7) } answers { game.copy(localPath = linkedPath) }
        coEvery { games.updateLocalPath(7, any(), any(), any()) } answers { linkedPath = secondArg(); Unit }
        coEvery { queue.insert(any()) } returns 99L
        staging = spyk(RomStagingManager(context))
        every { staging.availableBytes(any()) } returns null
        downloads = DownloadManager(
            context, games, mockk(relaxed = true), mockk(relaxed = true), queue, platforms,
            romm, preferences, mockk(relaxed = true), mockk(relaxed = true),
            dagger.Lazy { mockk<DownloadThermalManager>(relaxed = true) },
            dagger.Lazy { mockk<com.nendo.argosy.data.steam.SteamContentManager>(relaxed = true) },
            dagger.Lazy { mockk<MediaDownloadManager>(relaxed = true) }, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), staging, mockk(relaxed = true), AddonFileVerifier(format), storage
        )
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `extraction installs into the platform folder where other frontends look`() = runTest {
        runCurrent()
        val entry = entry().copy(isMultiFileRom = true)
        coEvery { queue.getByGameId(7) } returns entry
        zip(File(platform, "Game.zip"), "new game")
        val result = downloads.retryExtraction(7)
        assertTrue(result.toString(), result is DownloadManager.ExtractionResult.Success)
        assertEquals("new game", File(linkedPath!!).readText())
        assertTrue(
            "installed outside the platform folder: $linkedPath",
            linkedPath!!.startsWith(platform.path + File.separator)
        )
        assertFalse(File(platform, ".gameio-addons").exists())
    }

    @Test fun `staged move lands the game in the platform folder`() = runTest {
        runCurrent()
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val area = staging.open(StagingManifest(11, 7, "Game", "Game.zip", platform.path, StagingPhase.MOVING, "Game.nes"))
        File(area.outputDir, "Game.nes").writeText("new game")
        val result = downloads.retryExtraction(7)
        assertTrue(result.toString(), result is DownloadManager.ExtractionResult.Success)
        assertEquals(File(platform, "Game.nes").path, linkedPath)
        assertEquals("new game", File(linkedPath!!).readText())
    }

    @Test fun `staging for another game is still refused`() = runTest {
        runCurrent()
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val area = staging.open(StagingManifest(11, 8, "Other", "Other.zip", platform.path, StagingPhase.MOVING, "Other.nes"))
        File(area.outputDir, "Other.nes").writeText("someone else")
        val result = downloads.retryExtraction(7)
        assertEquals(DownloadManager.ExtractionResult.Failure(DownloadFailureReason.Addon(AddonFailure.STORAGE)), result)
        assertTrue(area.root.exists())
        assertNull(linkedPath)
    }

    @Test fun `redownload deletes this games transfer and leaves other games alone`() = runTest {
        runCurrent()
        val otherGame = File(platform, "Other.zip").apply { writeText("keep other game") }
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val owned = File(platform, "Game.zip").apply { writeText("owned invalid archive") }
        assertEquals(owned, downloads.getDownloadPath(entry))
        downloads.deleteFileAndRedownload(7)
        assertFalse(owned.exists())
        assertEquals("keep other game", otherGame.readText())
    }

    @Test fun `same source retry retains queue id partial bytes and staging`() = runTest {
        runCurrent()
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val directory = storage.directory(platform, 7, entry.addonSourceJson!!)
        val partial = File(directory, "Game.zip.tmp").apply { writeText("partial edition") }
        downloads.enqueueDownload(7, 7000018, "Game.zip", "Game", "nes", null, addonSourceJson = entry.addonSourceJson)
        assertEquals(11L, downloads.state.value.queue.single().id)
        assertEquals("partial edition", partial.readText())
        coVerify(exactly = 0) { queue.deleteByGameId(any()) }
        coVerify(exactly = 0) { queue.insert(any()) }
    }

    @Test fun `addon replacing legacy row transfers into the platform folder`() = runTest {
        runCurrent()
        val otherPartial = File(platform, "Other.zip.tmp").apply { writeText("another transfer") }
        coEvery { queue.getByGameId(7) } returns entry().copy(addonSourceJson = null, tempFilePath = File(platform, "Game.zip.tmp").path)
        downloads.enqueueDownload(7, 7000018, "Game.zip", "Game", "nes", null, addonSourceJson = entry().addonSourceJson)
        assertEquals("another transfer", otherPartial.readText())
        coVerify {
            queue.insert(
                match {
                    it.addonSourceJson != null &&
                        it.tempFilePath == File(platform, "Game.zip.tmp").path
                }
            )
        }
    }

    @Test fun `legacy force redownload after cutover preserves global files and partials`() = runTest {
        runCurrent()
        every { romm.usesAddonSources() } returns true
        val archive = File(platform, "Game.zip").apply { writeText("existing local archive") }
        val partial = File(platform, "Game.nes.partial").apply { writeText("other partial") }
        val entry = entry().copy(addonSourceJson = null)
        coEvery { queue.getByGameId(7) } returns entry
        val area = staging.open(StagingManifest(11, 7, "Game", "Game.zip", platform.path, StagingPhase.MOVING, "Game.nes"))
        File(area.outputDir, "Game.nes").writeText("retired stage")
        downloads.deleteFileAndRedownload(7)
        assertEquals("existing local archive", archive.readText())
        assertEquals("other partial", partial.readText())
        assertFalse(area.root.exists())
    }

    @Test fun `legacy extraction after cutover cannot adopt a same named local archive`() = runTest {
        runCurrent()
        every { romm.usesAddonSources() } returns true
        val archive = File(platform, "Game.zip").apply { writeText("existing local archive") }
        coEvery { queue.getByGameId(7) } returns entry().copy(addonSourceJson = null)
        assertEquals(DownloadManager.ExtractionResult.Failure(DownloadFailureReason.Addon(AddonFailure.NO_ADDONS)),
            downloads.retryExtraction(7))
        assertNull(linkedPath)
        assertEquals("existing local archive", archive.readText())
    }

    private fun entry() = DownloadQueueEntity(
        id = 11, gameId = 7, rommId = 7000018, fileName = "Game.zip", gameTitle = "Game", platformSlug = "nes",
        coverPath = null, bytesDownloaded = 0, totalBytes = 0, state = "FAILED", errorReason = null, tempFilePath = null,
        addonSourceJson = format.encodeMatch(AddonSourceMatch("test", "Test", AddonSource(
            "one", "http", "Game.zip", AddonLocator(url = "https://example.org/Game.zip")
        ), "a".repeat(64), "7:nes"))
    )

    private fun zip(file: File, content: String) {
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("Game.nes"))
            it.write(content.toByteArray())
            it.closeEntry()
        }
    }
}
