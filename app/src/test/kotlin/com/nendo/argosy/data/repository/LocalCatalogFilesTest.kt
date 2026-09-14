package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class LocalCatalogFilesTest {
    @Test fun `recognized release tags and archive suffixes do not change catalog identity`() {
        assertEquals("The Legend of Zelda", LocalCatalogFiles.title("Legend of Zelda, The (USA) (Rev 1).nes.zip", setOf("nes", "zip")))
        assertEquals("Final Fantasy VII", LocalCatalogFiles.title("Final Fantasy VII (Europe) (En,Fr,De) (Disc 2).chd", setOf("chd")))
        assertEquals("Game (Special Edition)", LocalCatalogFiles.title("Game (Special Edition) (USA).zip", setOf("zip")))
        assertNotEquals(LocalCatalogFiles.key("Game II"), LocalCatalogFiles.key("Game III"))
    }

    @Test fun `ambiguous or conflicting identities never silently attach a local game`() {
        val game = local(1)
        assertEquals(1L, LocalCatalogFiles.provisionalMatch(listOf(game), "Game", 10)?.id)
        assertNull(LocalCatalogFiles.provisionalMatch(listOf(game, local(2)), "Game", 10))
        assertNull(LocalCatalogFiles.provisionalMatch(listOf(game.copy(igdbId = 11)), "Game", 10))
        assertNull(LocalCatalogFiles.provisionalMatch(listOf(game), "Game II", 10))
        assertNull(LocalCatalogFiles.provisionalMatch(listOf(game), "Game", null))
        assertNull(LocalCatalogFiles.provisionalMatch(listOf(game.copy(rommId = 20)), "Game", 10))
    }

    @Test fun `playlists and cue sheets claim disc components without hiding unrelated games`() {
        val files = listOf(file("Game.m3u"), file("Game 1.cue"), file("track 1.bin"), file("Game 2.chd"), file("Other.chd"))
        val access = mockk<FileAccessLayer>()
        every { access.getInputStream("/roms/Game.m3u") } returns ByteArrayInputStream("\uFEFF#EXTM3U\nGame 1.cue\nGame 2.chd\n".toByteArray())
        every { access.getInputStream("/roms/Game 1.cue") } returns ByteArrayInputStream("FILE \"track 1.bin\" BINARY\n TRACK 01 MODE2/2352".toByteArray())
        val candidates = LocalCatalogFiles.candidates(files, setOf("m3u", "cue", "bin", "chd"), access)
        assertEquals(listOf("Game.m3u", "Other.chd"), candidates.map { it.name })
    }

    @Test fun `gdi quoted and bare track paths follow upstream field layout`() {
        val names = LocalCatalogFiles.referenceNames("gdi", "3\n1 0 4 2352 \"track 01.bin\" 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2352 track03.bin -150")
        assertEquals(listOf("track 01.bin", "track02.raw", "track03.bin"), names)
    }

    @Test fun `unreadable descriptor and files growing beyond limit do not abort scanning`() {
        val access = mockk<FileAccessLayer>()
        every { access.getInputStream("/roms/Game.cue") } throws IOException("removed SD card")
        every { access.getInputStream("/roms/Other.m3u") } returns ByteArrayInputStream(ByteArray(256 * 1024 + 1))
        val files = listOf(file("Game.cue"), file("Other.m3u"), file("track.bin"))
        assertEquals(3, LocalCatalogFiles.candidates(files, setOf("cue", "m3u", "bin"), access).size)
    }

    private fun local(id: Long) = GameEntity(id = id, platformId = 1, platformSlug = "snes", title = "Game",
        sortTitle = "game", localPath = "/roms/Game.sfc", rommId = null, igdbId = null, source = GameSource.LOCAL_ONLY)
    private fun file(name: String) = FileInfo("/roms/$name", name, false, true, 100, 0)
}
