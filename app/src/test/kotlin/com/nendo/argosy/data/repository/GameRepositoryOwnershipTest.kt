package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.LocalFileOwner
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GameRepositoryOwnershipTest {
    @get:Rule val temporary = TemporaryFolder()
    private val games = mockk<GameDao>(relaxed = true)
    private val platforms = mockk<PlatformDao>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val access = mockk<FileAccessLayer>()
    private lateinit var repository: GameRepository
    private lateinit var file: File
    private val game = GameEntity(id = 7, platformId = 18, platformSlug = "nes", title = "Game", sortTitle = "game",
        localPath = null, rommId = 7000018, rommFileName = "Game.nes", igdbId = 7, source = GameSource.ROMM_REMOTE)

    @Before fun setup() {
        val root = temporary.newFolder("nes")
        file = File(root, "Game.nes").apply { writeText("existing game") }
        val platform = PlatformEntity(18, "nes", name = "NES", shortName = "NES", romExtensions = "nes", customRomPath = root.path)
        coEvery { games.getById(7) } returns game
        coEvery { platforms.getById(18) } returns platform
        coEvery { platforms.getAllPlatforms() } returns listOf(platform)
        every { preferences.userPreferences } returns flowOf(UserPreferences(romStoragePath = temporary.root.path))
        every { access.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { access.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        repository = GameRepository(mockk<Context>(), games, mockk(), mockk(), mockk(), platforms, mockk(), mockk(),
            preferences, access, mockk(), mockk(relaxed = true), mockk(), CatalogIdentityLock())
    }

    @Test fun `single game discovery refuses a basename owned by another catalog game`() = runBlocking {
        coEvery { games.getLocalFileOwners() } returns listOf(LocalFileOwner(8, file.path))
        assertFalse(repository.validateAndDiscoverGame(7))
        coVerify(exactly = 0) { games.updateLocalPath(any(), any(), any(), any()) }
        assertEquals("existing game", file.readText())
    }

    @Test fun `canonical aliases retain foreign variant or disc ownership`() = runBlocking {
        val alias = File(temporary.root, "disc-alias.nes")
        Files.createSymbolicLink(alias.toPath(), file.toPath())
        coEvery { games.getLocalFileOwners() } returns listOf(LocalFileOwner(8, alias.path))
        assertFalse(repository.validateAndDiscoverGame(7))
        coVerify(exactly = 0) { games.updateLocalPath(any(), any(), any(), any()) }
    }

    @Test fun `a game cannot claim a file inside another games folder`() = runBlocking {
        coEvery { games.getLocalFileOwners() } returns listOf(LocalFileOwner(8, file.parentFile!!.path))
        assertFalse(repository.validateAndDiscoverGame(7))
        coVerify(exactly = 0) { games.updateLocalPath(any(), any(), any(), any()) }
    }

    @Test fun `a restored file already owned by the same game can be attached`() = runBlocking {
        coEvery { games.getLocalFileOwners() } returns listOf(LocalFileOwner(7, file.path))
        assertTrue(repository.validateAndDiscoverGame(7))
        coVerify(exactly = 1) { games.updateLocalPath(7, file.path, GameSource.ROMM_SYNCED, any()) }
    }
}
