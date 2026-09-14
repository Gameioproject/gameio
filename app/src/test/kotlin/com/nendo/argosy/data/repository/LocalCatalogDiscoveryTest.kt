package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.LocalFileOwner
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMLibrarySyncService
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.storage.StorageAttributionRepository
import dagger.Lazy
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalCatalogDiscoveryTest {
    private val gameDao = mockk<GameDao>()
    private val variantDao = mockk<GameFileDao>()
    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private val access = mockk<FileAccessLayer>()
    private val overlays = mockk<GameUserOverlayWriter>(relaxed = true)
    private val attribution = mockk<StorageAttributionRepository>(relaxed = true)
    private val catalog = mockk<RomMLibrarySyncService>()
    private val games = mutableListOf<GameEntity>()
    private val variants = mutableListOf<GameFileEntity>()
    private val additionalOwners = mutableListOf<LocalFileOwner>()
    private lateinit var root: File
    private lateinit var discovery: LocalCatalogDiscovery
    private val platform = PlatformEntity(1, "snes", name = "Super Nintendo", shortName = "SNES", romExtensions = "sfc,smc,zip")

    @Before fun setup() {
        root = createTempDirectory("local_catalog_test").toFile()
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform)
        coEvery { overlays.activeOwnerId() } returns 7
        coEvery { gameDao.getAllGames() } answers { games.toList() }
        coEvery { gameDao.getLocalFileOwners() } answers {
            games.mapNotNull { game -> game.localPath?.let { LocalFileOwner(game.id, it) } } +
                variants.mapNotNull { variant -> variant.localPath?.let { LocalFileOwner(variant.gameId, it) } } + additionalOwners
        }
        coEvery { gameDao.getGamesWithLocalPath() } returns emptyList()
        coEvery { gameDao.countByPlatform(any(), any()) } answers { games.count { it.platformId == firstArg<Long>() } }
        coEvery { gameDao.insert(any()) } answers {
            val game = firstArg<GameEntity>().copy(id = (games.maxOfOrNull { it.id } ?: 0) + 1)
            games += game
            game.id
        }
        coEvery { gameDao.updateLocalPath(any(), any(), any(), any()) } answers {
            val index = games.indexOfFirst { it.id == firstArg<Long>() }
            games[index] = games[index].copy(localPath = secondArg(), source = thirdArg())
        }
        coEvery { variantDao.getAllWithLocalPath() } answers { variants.filter { it.localPath != null } }
        coEvery { variantDao.getByGameIdAndFileName(any(), any()) } answers {
            variants.filter { it.gameId == firstArg<Long>() && it.fileName == secondArg<String>() }
        }
        coEvery { variantDao.insert(any()) } answers {
            val variant = firstArg<GameFileEntity>().copy(id = (variants.maxOfOrNull { it.id } ?: 0) + 1)
            variants += variant
            variant.id
        }
        coEvery { variantDao.updateLocalPath(any(), any(), any()) } answers {
            val index = variants.indexOfFirst { it.id == firstArg<Long>() }
            variants[index] = variants[index].copy(localPath = secondArg(), downloadedAt = thirdArg())
        }
        every { access.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { access.listFiles(any()) } answers {
            File(firstArg<String>()).listFiles()?.map { FileInfo(it.path, it.name, it.isDirectory, it.isFile, it.length(), it.lastModified()) }
        }
        every { access.exists(any()) } answers { File(firstArg<String>()).exists() }
        every { access.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        every { access.canRead(any()) } answers { File(firstArg<String>()).canRead() }
        every { access.getInputStream(any()) } answers { File(firstArg<String>()).inputStream() }
        discovery = LocalCatalogDiscovery(gameDao, variantDao, platformDao, access, Lazy { catalog }, overlays, attribution, CatalogIdentityLock())
    }

    @After fun cleanup() { root.deleteRecursively() }

    @Test fun `an existing disc cannot become a new local catalog game`() = runBlocking {
        val disc = write("snes/Game (Disc 2).sfc")
        additionalOwners += LocalFileOwner(999, disc.path)
        assertEquals(0, discovery.discover(root))
        assertTrue(games.isEmpty())
        assertTrue(variants.isEmpty())
        assertTrue(disc.exists())
    }

    @Test fun `a variant reached through a storage alias is not adopted twice`() = runBlocking {
        val existing = write("snes/Game.sfc")
        val alias = File(root, "owned.sfc")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), existing.toPath())
        additionalOwners += LocalFileOwner(999, alias.path)
        assertEquals(0, discovery.discover(root))
        assertTrue(games.isEmpty())
        assertTrue(existing.exists())
    }

    @Test fun `copied file outside loaded catalog is immediately playable and concurrent rescan is idempotent`() = runBlocking {
        val file = write("snes/Chrono Trigger (USA).sfc")
        val scans = listOf(async { discovery.discover(root) }, async { discovery.discover(root) })
        assertEquals(1, scans.sumOf { it.await() })
        val game = games.single()
        assertEquals("Chrono Trigger", game.title)
        assertEquals(file.path, game.localPath)
        assertEquals(GameSource.LOCAL_ONLY, game.source)
        assertNull(game.rommId)
        assertFalse(game.isIdentified)
        coVerify(exactly = 1) { overlays.grantMembership(7, game.id) }
        assertEquals(0, discovery.discover(root))
        assertEquals(game.id, games.single().id)
    }

    @Test fun `extra edition attaches to catalog game without changing its base file or active selection`() = runBlocking {
        val base = write("snes/Game (USA).sfc")
        val extra = write("snes/Game (Europe).sfc")
        val existing = game(base.path).copy(rommId = 900, igdbId = 90, saveId = "save-unchanged", activeVariantFileId = 99, isFavorite = true)
        games += existing
        assertEquals(1, discovery.discover(root))
        assertEquals(existing, games.single())
        val variant = variants.single()
        assertEquals(existing.id, variant.gameId)
        assertEquals(extra.path, variant.localPath)
        assertEquals("game", variant.category)
        assertEquals(0L, variant.romId)
        assertNull(variant.rommFileId)
        assertTrue(variant.isLaunchTarget)
        assertEquals(0, discovery.discover(root))
        assertEquals(1, variants.size)
    }

    @Test fun `lost card and unreadable paths never replace the base with another edition`() = runBlocking {
        val extra = write("snes/Game (Europe).sfc")
        val existing = game("/storage/missing-card/Game (USA).sfc")
        games += existing
        assertEquals(1, discovery.discover(root))
        assertEquals(existing.localPath, games.single().localPath)
        assertEquals(extra.path, variants.single().localPath)
        every { access.listFiles(any()) } returns null
        assertEquals(0, discovery.discover(root))
        assertEquals(existing.localPath, games.single().localPath)
        assertEquals(extra.path, variants.single().localPath)
    }

    @Test fun `restored edition reuses its game file row after absence validation`() = runBlocking {
        val base = write("snes/Game (USA).sfc")
        val extra = write("snes/Game (Europe).sfc")
        games += game(base.path)
        variants += GameFileEntity(8, gameId = 42, fileName = extra.name, filePath = extra.path, category = "game", fileSize = 1)
        assertEquals(1, discovery.discover(root))
        assertEquals(8L, variants.single().id)
        assertEquals(extra.path, variants.single().localPath)
    }

    @Test fun `custom platform roots are respected and variant content is not imported as separate games`() = runBlocking {
        val custom = File(root, "custom")
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform.copy(customRomPath = custom.path))
        write("custom/Game/Game.sfc")
        write("custom/Game/translation/Game Translated.sfc")
        write("custom/Game/soundtrack/Theme.sfc")
        write("custom/.hidden/Hidden.sfc")
        write("custom/Other (USA).sfc")
        assertEquals(2, discovery.discover(root))
        assertEquals(setOf("Game", "Other"), games.map { it.title }.toSet())
    }

    @Test fun `playlist is local launch target and its disc files are not imported separately`() = runBlocking {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform.copy(slug = "psx", romExtensions = "chd,cue,bin"))
        val playlist = write("psx/Game.m3u", "Game (Disc 1).chd\nGame (Disc 2).chd")
        write("psx/Game (Disc 1).chd")
        write("psx/Game (Disc 2).chd")
        assertEquals(1, discovery.discover(root))
        assertEquals(playlist.path, games.single().localPath)
        assertEquals(playlist.path, games.single().m3uPath)
        assertTrue(games.single().isMultiDisc)
        assertTrue(variants.isEmpty())
    }

    private fun write(path: String, value: String = "rom") = File(root, path).apply { parentFile?.mkdirs(); writeText(value) }
    private fun game(path: String) = GameEntity(id = 42, platformId = 1, platformSlug = "snes", title = "Game", sortTitle = "game",
        localPath = path, rommId = null, igdbId = null, source = GameSource.LOCAL_ONLY)
}
