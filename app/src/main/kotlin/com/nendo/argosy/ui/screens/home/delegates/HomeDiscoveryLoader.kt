package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.catalog.ShelfRepository
import com.nendo.argosy.data.preferences.HomeLibraryFilter
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.ui.common.toHomeGameUi
import com.nendo.argosy.ui.screens.home.DiscoveryData
import com.nendo.argosy.ui.screens.home.ExplorePage
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class HomeDiscoveryLoader @Inject constructor(
    private val games: GameRepository,
    private val platforms: PlatformRepository,
    private val shelves: ShelfRepository,
    private val downloads: DownloadFileStatusRepository
) {
    val connectionState get() = shelves.connectionState

    suspend fun genrePage(
        genre: String, page: Int, platformId: Long?, filter: HomeLibraryFilter
    ): ExplorePage = withContext(Dispatchers.IO) {
        val pageSize = 12
        val followed = if (shelves.isCatalogOnly()) platforms.getSyncEnabledPlatforms()
            else platforms.getPlatformsWithGames()
        val shown = followed.filter { platformId == null || it.id == platformId }
        val names = shown.associate { it.id to it.getDisplayName() }
        val ids = if (shelves.isCatalogOnly() && filter != HomeLibraryFilter.LIBRARY) {
            shelves.genrePage(genre, shown.map { it.slug }, page * pageSize, pageSize,
                owned = false)
        } else {
            games.getExploreGenrePage(genre, shown.map { it.id },
                playableOnly = filter == HomeLibraryFilter.LIBRARY,
                offset = page * pageSize, limit = pageSize).map { it.id }
        }
        val hidden = games.getHiddenGameIds()
        val byId = games.getByIds(ids).associateBy { it.id }
        val mapped = ids.filterNot { it in hidden }.mapNotNull { byId[it] }
            .filter { it.platformId in names }
            .map { it.toHomeGameUi(downloads, names[it.platformId]) }
        ExplorePage(mapped, hasMore = ids.size == pageSize)
    }

    suspend fun load(platformId: Long?, filter: HomeLibraryFilter): DiscoveryData = withContext(Dispatchers.IO) {
        val followed = if (shelves.isCatalogOnly()) platforms.getSyncEnabledPlatforms()
            else platforms.getPlatformsWithGames()
        val shown = followed.filter { platformId == null || it.id == platformId }
        val names = followed.associate { it.id to it.getDisplayName() }
        val playableIds = games.observePlayableList().first().map { it.id }
        val playable = games.getByIds(playableIds).filter { entity ->
            entity.platformId in names && downloads.isContentAvailable(entity)
        }.map { it.toHomeGameUi(downloads, names[it.platformId]) }
        val hidden = games.getHiddenGameIds()
        try {
            val shelf = shelves.definitions(strict = true).firstOrNull { it.key == "top-rated" }
            val ids = if (shelf != null && shown.isNotEmpty()) {
                shelves.gamesFor(shelf, shown.map { it.slug }, strict = true,
                    owned = false)
            } else {
                games.observeAllList().first().filter { it.platformId in shown.map { p -> p.id } }
                    .sortedByDescending { it.rating }.take(40).map { it.id }
            }
            val byId = games.getByIds(ids).associateBy { it.id }
            val ranked = ids.filterNot { it in hidden }.mapNotNull { byId[it] }
                .map { it.toHomeGameUi(downloads, names[it.platformId]) }
            DiscoveryData(platformId, ranked, playable, loading = false, filter = filter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("DiscoveryHome", "Catalog load failed: ${e.message}")
            val cached = games.observeAllList().first()
                .filter { it.platformId in shown.map { p -> p.id } }
                .sortedByDescending { it.rating }.take(40).map { it.id }
            DiscoveryData(platformId, games.getByIds(cached)
                .sortedByDescending { it.rating }
                .map { it.toHomeGameUi(downloads, names[it.platformId]) },
                playable, loading = false, failed = true, filter = filter)
        }
    }
}
