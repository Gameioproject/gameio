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

    @Test fun `extraction cannot overwrite a copied game with identical title and archive name`() = runTest {
        runCurrent()
        val localArchive = File(platform, "Game.zip").apply { writeText("local archive") }
        val localRom = File(platform, "Game/Game.nes").apply { parentFile!!.mkdirs(); writeText("local variant") }
        val entry = entry().copy(isMultiFileRom = true)
        coEvery { queue.getByGameId(7) } returns entry
        val directory = storage.directory(platform, 7, entry.addonSourceJson!!)
        zip(File(directory, "Game.zip"), "new game")
        val result = downloads.retryExtraction(7)
        assertTrue(result.toString(), result is DownloadManager.ExtractionResult.Success)
        assertEquals("local archive", localArchive.readText())
        assertEquals("local variant", localRom.readText())
        assertEquals("new game", File(linkedPath!!).readText())
        assertTrue(linkedPath!!.startsWith(directory.path + File.separator))
    }

    @Test fun `staged move cannot overwrite another games final or partial file`() = runTest {
        runCurrent()
        val local = File(platform, "Game.nes").apply { writeText("local game") }
        val partial = File(platform, "Game.nes.partial").apply { writeText("other transfer") }
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val directory = storage.directory(platform, 7, entry.addonSourceJson!!)
        val area = staging.open(StagingManifest(11, 7, "Game", "Game.zip", directory.path, StagingPhase.MOVING, "Game.nes"))
        File(area.outputDir, "Game.nes").writeText("new game")
        val result = downloads.retryExtraction(7)
        assertTrue(result.toString(), result is DownloadManager.ExtractionResult.Success)
        assertEquals("local game", local.readText())
        assertEquals("other transfer", partial.readText())
        assertEquals("new game", File(linkedPath!!).readText())
    }

    @Test fun `legacy staging is never deployed or cleaned as an addon destination`() = runTest {
        runCurrent()
        val local = File(platform, "Game.nes").apply { writeText("local game") }
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val area = staging.open(StagingManifest(11, 7, "Game", "Game.zip", platform.path, StagingPhase.MOVING, "Game.nes"))
        File(area.outputDir, "Game.nes").writeText("new game")
        val result = downloads.retryExtraction(7)
        assertEquals(DownloadManager.ExtractionResult.Failure(DownloadFailureReason.Addon(AddonFailure.STORAGE)), result)
        assertEquals("local game", local.readText())
        assertTrue(area.root.exists())
        assertNull(linkedPath)
    }

    @Test fun `redownload only deletes the selected owned transfer`() = runTest {
        runCurrent()
        val local = File(platform, "Game.zip").apply { writeText("keep local") }
        val otherPartial = File(platform, "Game.zip.tmp").apply { writeText("keep other") }
        val entry = entry()
        coEvery { queue.getByGameId(7) } returns entry
        val directory = storage.directory(platform, 7, entry.addonSourceJson!!)
        val owned = File(directory, "Game.zip").apply { writeText("owned invalid archive") }
        assertEquals(owned, downloads.getDownloadPath(entry))
        downloads.deleteFileAndRedownload(7)
        assertFalse(owned.exists())
        assertEquals("keep local", local.readText())
        assertEquals("keep other", otherPartial.readText())
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

    @Test fun `addon replacing legacy row leaves unrelated legacy partial untouched`() = runTest {
        runCurrent()
        val legacyPartial = File(platform, "Game.zip.tmp").apply { writeText("legacy partial") }
        coEvery { queue.getByGameId(7) } returns entry().copy(addonSourceJson = null, tempFilePath = legacyPartial.path)
        downloads.enqueueDownload(7, 7000018, "Game.zip", "Game", "nes", null, addonSourceJson = entry().addonSourceJson)
        assertEquals("legacy partial", legacyPartial.readText())
        coVerify { queue.insert(match { it.tempFilePath != legacyPartial.path && it.addonSourceJson != null }) }
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
