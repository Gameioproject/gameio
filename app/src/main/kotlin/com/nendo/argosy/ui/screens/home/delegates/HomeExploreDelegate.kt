package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.*
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeExploreDelegate(
    private val state: MutableStateFlow<HomeUiState>,
    private val loader: HomeDiscoveryLoader,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private var generation = 0

    fun reset() {
        generation++
        job?.cancel()
        state.update { it.copy(explore = ExploreState()) }
    }

    fun loadMore(retry: Boolean = false) {
        val current = state.value
        if (!current.isDiscoveryHome || current.discoveryFocus.feed != DiscoveryFeed.EXPLORE ||
            current.discoveryData.loading || current.explore.exhausted ||
            (current.explore.failed && !retry) || job?.isActive == true) return
        val requestGeneration = generation
        val platformId = current.currentPlatform?.id
        val filter = current.libraryFilter
        state.update { it.copy(explore = it.explore.copy(loading = true, failed = false)) }
        job = scope.launch {
            try {
                var next = current.explore.copy(loading = true, failed = false)
                val target = next.rows.size + 3
                var attempts = 0
                while (next.rows.size < target && !next.exhausted && attempts < ExploreGenre.entries.size) {
                    attempts++
                    next = if (next.genre in next.finishedGenres) next.copy(cursor = next.cursor + 1)
                    else next.append(loader.genrePage(next.genre.token, next.page, platformId, filter))
                    if (requestGeneration != generation) return@launch
                    state.update { it.copy(explore = next) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn("Explore", "Genre page failed: ${e.message}")
                if (requestGeneration == generation) {
                    state.update { it.copy(explore = it.explore.copy(failed = true)) }
                }
            } finally {
                if (requestGeneration == generation) {
                    state.update { it.copy(explore = it.explore.copy(loading = false)) }
                }
            }
        }
    }
}
