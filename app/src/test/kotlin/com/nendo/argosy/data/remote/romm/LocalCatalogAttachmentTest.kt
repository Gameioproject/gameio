package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.*
import org.junit.Test

class LocalCatalogAttachmentTest {
    private val rom = RomMRom(id = 700, platformId = 1, platformSlug = "snes", name = "Game", slug = "game",
        fileName = null, filePath = null, fileSize = 0, igdbId = 900, mobyId = null, summary = "Catalog description",
        coverSmall = null, coverLarge = null, regions = listOf("us"), languages = null, revision = null,
        md5Hash = "server-edition-hash")

    @Test fun `catalog attachment preserves local identity and all launcher or player state`() {
        val local = GameEntity(id = 42, platformId = 1, platformSlug = "snes", title = "Game", sortTitle = "game",
            localPath = "/roms/Game (USA).sfc", rommId = null, igdbId = null, source = GameSource.LOCAL_ONLY,
            rommFileName = "Game (USA).sfc", isIdentified = false, saveId = "stable-save", titleId = "local-title",
            titleIdLocked = true, perGameSettingsEnabled = true, perGameControlsEnabled = true,
            activeVariantFileId = 8, lastPlayedFileId = 9, isFavorite = true, playCount = 4,
            fileSizeBytes = 1234, md5Hash = "local-edition-hash", isMultiDisc = true, m3uPath = "/roms/Game.m3u")
        val attached = local.attachLocalCatalogMetadata(rom, "https://example.org/cover.jpg")
        assertEquals(42L, attached.id)
        assertEquals(700L, attached.rommId)
        assertEquals(900L, attached.igdbId)
        assertEquals(GameSource.ROMM_SYNCED, attached.source)
        assertEquals("stable-save", attached.saveId)
        assertEquals("local-title", attached.titleId)
        assertTrue(attached.titleIdLocked && attached.perGameSettingsEnabled && attached.perGameControlsEnabled)
        assertEquals(8L, attached.activeVariantFileId)
        assertEquals(9L, attached.lastPlayedFileId)
        assertTrue(attached.isFavorite && attached.isMultiDisc)
        assertEquals(4, attached.playCount)
        assertEquals(local.m3uPath, attached.m3uPath)
        assertEquals(local.localPath, attached.localPath)
        assertEquals(local.rommFileName, attached.rommFileName)
        assertEquals(1234L, attached.fileSizeBytes)
        assertEquals("local-edition-hash", attached.md5Hash)
        assertTrue(attached.isIdentified)
        assertEquals("Catalog description", attached.description)
        assertEquals("https://example.org/cover.jpg", attached.coverPath)
        assertEquals(attached, attached.attachLocalCatalogMetadata(rom, "https://example.org/cover.jpg"))
    }
}
