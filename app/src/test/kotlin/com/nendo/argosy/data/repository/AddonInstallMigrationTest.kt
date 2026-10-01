package com.nendo.argosy.data.repository

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.GameLocalPathInfo
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Games downloaded before the move install under a hidden per-source folder that no other
 * frontend, and not even this app's own discovery, will look inside.
 */
class AddonInstallMigrationTest {
    @get:Rule val temporary = TemporaryFolder()

    private val games = mockk<GameDao>(relaxed = true)
    private val gameFiles = mockk<GameFileDao>(relaxed = true)
    private val platforms = mockk<PlatformDao>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>()
    private val access = mockk<FileAccessLayer>(relaxed = true)
    private lateinit var platform: File
    private lateinit var repository: GameRepository

    @Before fun setup() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageState() } returns Environment.MEDIA_MOUNTED
        platform = temporary.newFolder("n64")
        val entity = PlatformEntity(
            18, "n64", name = "N64", shortName = "N64", romExtensions = "z64,zip", customRomPath = platform.path
        )
        coEvery { platforms.getById(18) } returns entity
        coEvery { platforms.getAllPlatforms() } returns listOf(entity)
        every { preferences.userPreferences } returns flowOf(UserPreferences(romStoragePath = temporary.root.path))
        repository = GameRepository(
            mockk<Context>(relaxed = true), games, mockk(relaxed = true), mockk(relaxed = true), gameFiles,
            platforms, mockk(relaxed = true), mockk(relaxed = true), preferences, access, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), CatalogIdentityLock()
        )
    }

    @After fun tearDown() = unmockkStatic(Environment::class)

    private fun hiddenInstall(fileName: String, body: String = "rom bytes"): File {
        val content = File(platform, ".gameio-addons/7/abc123/content").apply { mkdirs() }
        File(content.parentFile, ".owner").writeText("1\n7\nabc123\n")
        return File(content, fileName).apply { writeText(body) }
    }

    private fun recorded(path: String) = listOf(
        GameLocalPathInfo(7, 18, "n64", GameSource.ROMM_SYNCED, path)
    )

    @Test fun `a hidden install is carried into the platform folder and repointed`() = runBlocking {
        val rom = hiddenInstall("Zelda.z64")
        coEvery { games.getGamesWithLocalPathInfo() } returns recorded(rom.path)

        assertEquals(1, repository.liftAddonInstallsIntoPlatformFolder())

        val carried = File(platform, "Zelda.z64")
        assertTrue("game should sit in the platform folder", carried.isFile)
        assertEquals("rom bytes", carried.readText())
        assertFalse("hidden snapshot should be gone", File(platform, ".gameio-addons/7/abc123").exists())
        coVerify { games.relocateLocalPath(7, carried.absolutePath) }
    }

    @Test fun `a name already taken in the platform folder is left alone`() = runBlocking {
        val mine = File(platform, "Zelda.z64").apply { writeText("the copy I put here") }
        val rom = hiddenInstall("Zelda.z64", "the download")
        coEvery { games.getGamesWithLocalPathInfo() } returns recorded(rom.path)

        assertEquals(0, repository.liftAddonInstallsIntoPlatformFolder())

        assertEquals("the copy I put here", mine.readText())
        assertTrue("the download should stay where it was", rom.isFile)
        coVerify(exactly = 0) { games.relocateLocalPath(any(), any()) }
    }

    @Test fun `a game already in the platform folder is not touched`() = runBlocking {
        val plain = File(platform, "Mario.z64").apply { writeText("already visible") }
        coEvery { games.getGamesWithLocalPathInfo() } returns recorded(plain.path)

        assertEquals(0, repository.liftAddonInstallsIntoPlatformFolder())

        assertTrue(plain.isFile)
        coVerify(exactly = 0) { games.relocateLocalPath(any(), any()) }
    }
}
