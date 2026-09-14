package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.addon.AddonException
import com.nendo.argosy.data.addon.AddonFailure
import com.nendo.argosy.data.addon.AddonFormat
import com.nendo.argosy.data.addon.AddonRepository
import com.nendo.argosy.data.addon.AddonSourceMatch
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class DownloadAddonGameUseCase @Inject constructor(
    private val addons: AddonRepository,
    private val format: AddonFormat,
    private val downloads: DownloadManager
) {
    suspend operator fun invoke(game: GameEntity, selected: AddonSourceMatch? = null): DownloadResult {
        val igdbId = game.igdbId ?: return failure(AddonFailure.INVALID_SOURCE)
        val rommId = game.rommId ?: return failure(AddonFailure.INVALID_SOURCE)
        return try {
            val lookup = addons.lookup(igdbId, game.platformSlug)
            val match = if (selected != null) {
                lookup.sources.find { it == selected } ?: return failure(AddonFailure.REMOVED)
            } else {
                lookup.sources.sortedWith(compareBy<AddonSourceMatch>(
                    { it.source.filename != game.rommFileName },
                    { it.source.kind == "torrent" },
                    { regionRank(it.source.region) },
                    { it.source.filename },
                    { it.source.id }
                )).firstOrNull() ?: return failure(when {
                    lookup.enabledAddonCount == 0 -> AddonFailure.NO_ADDONS
                    lookup.failures.isNotEmpty() -> lookup.failures.first().reason
                    else -> AddonFailure.NOT_FOUND
                })
            }
            val extension = match.source.filename.substringAfterLast('.', "").lowercase()
            if (extension in DownloadGameUseCase.INVALID_ROM_EXTENSIONS &&
                !DownloadGameUseCase.isPico8Cart(match.source.filename, game.platformSlug)) {
                return DownloadResult.Error(DownloadGameFailureReason.InvalidFileType(extension))
            }
            downloads.enqueueDownload(
                gameId = game.id,
                rommId = rommId,
                fileName = match.source.filename,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                coverPath = game.coverPath,
                expectedSizeBytes = match.source.size ?: 0L,
                addonSourceJson = format.encodeMatch(match)
            )
            DownloadResult.Queued
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure((e as? AddonException)?.reason ?: AddonFailure.NETWORK)
        }
    }

    private fun failure(reason: AddonFailure) = DownloadResult.Error(DownloadGameFailureReason.Addon(reason))

    private fun regionRank(region: String?): Int {
        val index = REGIONS.indexOfFirst { region?.startsWith(it) == true }
        return if (index < 0) REGIONS.size else index
    }

    companion object {
        private val REGIONS = listOf("USA", "World", "Europe", "Japan")
    }
}
