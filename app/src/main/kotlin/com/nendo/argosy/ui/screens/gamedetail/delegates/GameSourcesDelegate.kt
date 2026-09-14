package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.addon.AddonException
import com.nendo.argosy.data.addon.AddonFailure
import com.nendo.argosy.data.addon.AddonLookupResult
import com.nendo.argosy.data.addon.AddonRepository
import com.nendo.argosy.data.addon.AddonSourceMatch
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameSourcesState(
    val gameId: Long = 0,
    val available: Boolean = false,
    val visible: Boolean = false,
    val loading: Boolean = false,
    val onDevice: Boolean = false,
    val result: AddonLookupResult = AddonLookupResult(),
    val failure: AddonFailure? = null,
    val focusedIndex: Int = 0
) {
    val sourceChoices: List<AddonSourceMatch> get() = if (onDevice) emptyList() else result.sources
    val choiceCount: Int get() = sourceChoices.size + 3
}

class GameSourcesDelegate @Inject constructor(
    private val addons: AddonRepository,
    private val games: GameRepository,
    private val romm: RomMRepository
) {
    private val _state = MutableStateFlow(GameSourcesState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun load(scope: CoroutineScope, gameId: Long, refresh: Boolean = false) {
        job?.cancel()
        val sameGame = _state.value.gameId == gameId
        _state.update {
            GameSourcesState(gameId = gameId, available = romm.usesAddonSources(),
                visible = sameGame && it.visible, loading = true)
        }
        job = scope.launch {
            try {
                val game = games.getById(gameId)
                currentCoroutineContext().ensureActive()
                if (game?.igdbId == null || !romm.usesAddonSources()) {
                    _state.update { it.copy(available = false, visible = false, loading = false) }
                    return@launch
                }
                if (games.validateAndDiscoverGame(gameId)) {
                    currentCoroutineContext().ensureActive()
                    _state.update { it.copy(onDevice = true, loading = false) }
                    return@launch
                }
                val result = addons.lookup(game.igdbId, game.platformSlug, refresh)
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(loading = false, result = result, focusedIndex = 0) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(loading = false, failure = (e as? AddonException)?.reason ?: AddonFailure.NETWORK) }
            }
        }
    }

    fun show() { _state.update { it.copy(visible = true, focusedIndex = 0) } }
    fun dismiss() { _state.update { it.copy(visible = false) } }
    fun move(delta: Int) { _state.update { it.copy(focusedIndex = (it.focusedIndex + delta).mod(it.choiceCount)) } }
    fun focus(index: Int) { _state.update { it.copy(focusedIndex = index.coerceIn(0, it.choiceCount - 1)) } }
}
