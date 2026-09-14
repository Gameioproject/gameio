package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource

internal fun GameEntity.attachLocalCatalogMetadata(rom: RomMRom, coverUrl: String?): GameEntity =
    withRomMetadata(rom).copy(
        rommId = rom.id,
        rommFileName = rommFileName ?: rom.fileName,
        igdbId = rom.igdbId,
        raId = rom.raId ?: raId,
        source = GameSource.ROMM_SYNCED,
        coverPath = coverPath ?: coverUrl,
        backgroundPath = backgroundPath ?: rom.backgroundUrls.firstOrNull() ?: rom.screenshotUrls.firstOrNull(),
        fileSizeBytes = fileSizeBytes,
        crcHash = crcHash,
        md5Hash = md5Hash,
        sha1Hash = sha1Hash,
        raHash = raHash
    )
