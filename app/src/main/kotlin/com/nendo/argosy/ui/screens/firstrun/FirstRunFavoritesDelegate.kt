package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SetupFavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val FAVORITES_HEADER_COUNT = 3
private const val PAGE_SIZE = 30
private const val SEARCH_DELAY_MS = 300L

data class FirstRunFavoritesState(
    val query: String = "",
    val games: List<GameEntity> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val platformNames: Map<Long, String> = emptyMap(),
    val focusedIndex: Int = 0,
    val keyboardOpen: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val savingId: Long? = null,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val initialized: Boolean = false,
    val loaded: Boolean = false
) {
    val hasPageAction: Boolean get() = loadFailed || hasMore
    val lastFocusIndex: Int get() = FAVORITES_HEADER_COUNT + games.size - if (hasPageAction) 0 else 1
}

class FirstRunFavoritesDelegate @Inject constructor(
    private val repository: SetupFavoritesRepository,
    private val platforms: PlatformRepository
) {
    private val _state = MutableStateFlow(FirstRunFavoritesState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun enter(scope: CoroutineScope) {
        if (!_state.value.loaded) load(scope, reset = true)
    }

    fun setQuery(query: String, scope: CoroutineScope) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query.take(100), focusedIndex = 0) }
        load(scope, reset = true, debounce = true)
    }

    fun openSearch() = _state.update { it.copy(keyboardOpen = true, focusedIndex = 0) }
    fun closeSearch() = _state.update { it.copy(keyboardOpen = false) }

    fun moveVertical(delta: Int) {
        _state.update {
            if (it.keyboardOpen) return@update it
            val next = when {
                it.focusedIndex < FAVORITES_HEADER_COUNT && delta > 0 ->
                    FAVORITES_HEADER_COUNT.coerceAtMost(it.lastFocusIndex)
                it.focusedIndex < FAVORITES_HEADER_COUNT -> it.focusedIndex
                it.focusedIndex == FAVORITES_HEADER_COUNT && delta < 0 -> 0
                else -> (it.focusedIndex + delta).coerceIn(0, it.lastFocusIndex)
            }
            it.copy(focusedIndex = next)
        }
    }

    fun moveHorizontal(delta: Int) {
        _state.update {
            if (it.keyboardOpen) it else it.copy(
                focusedIndex = if (it.focusedIndex < FAVORITES_HEADER_COUNT) {
                    (it.focusedIndex + delta).mod(FAVORITES_HEADER_COUNT)
                } else if (delta < 0) 0 else it.focusedIndex
            )
        }
    }

    fun confirm(scope: CoroutineScope, onContinue: () -> Unit) {
        val current = _state.value
        if (current.keyboardOpen) return
        when (current.focusedIndex) {
            0 -> openSearch()
            1, 2 -> leave(onContinue)
            else -> {
                val game = current.games.getOrNull(current.focusedIndex - FAVORITES_HEADER_COUNT)
                if (game != null) toggle(game.id, scope) else loadMore(scope)
            }
        }
    }

    fun leave(onContinue: () -> Unit) {
        if (_state.value.savingId != null) return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = false, keyboardOpen = false) }
        onContinue()
    }

    fun toggle(gameId: Long, scope: CoroutineScope) {
        val current = _state.value
        if (current.savingId != null || current.games.none { it.id == gameId }) return
        val selected = gameId !in current.selectedIds
        _state.update { it.copy(savingId = gameId, saveFailed = false) }
        scope.launch {
            try {
                repository.setFavorite(gameId, selected)
                _state.update {
                    it.copy(
                        selectedIds = if (selected) it.selectedIds + gameId else it.selectedIds - gameId,
                        savingId = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(savingId = null, saveFailed = true) }
            }
        }
    }

    fun loadMore(scope: CoroutineScope) {
        if (!_state.value.isLoading) load(scope, reset = _state.value.nextOffset == 0)
    }

    private fun load(scope: CoroutineScope, reset: Boolean, debounce: Boolean = false) {
        loadJob?.cancel()
        val query = _state.value.query
        val offset = if (reset) 0 else _state.value.nextOffset
        _state.update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                games = if (reset) emptyList() else it.games,
                nextOffset = offset,
                hasMore = false,
                focusedIndex = if (reset) 0 else it.focusedIndex
            )
        }
        loadJob = scope.launch {
            try {
                if (debounce) delay(SEARCH_DELAY_MS)
                if (!_state.value.initialized) {
                    val favorites = repository.favorites()
                    val names = platforms.observeAllPlatforms().first().associate { it.id to it.name }
                    _state.update {
                        it.copy(
                            selectedIds = favorites.mapTo(mutableSetOf()) { game -> game.id },
                            games = if (query.isBlank()) favorites else it.games,
                            platformNames = names, initialized = true
                        )
                    }
                }
                val page = repository.page(query, offset, PAGE_SIZE)
                val existingFavorites = if (reset && query.isBlank()) repository.favorites() else emptyList()
                _state.update {
                    val games = (if (reset) existingFavorites + page else it.games + page).distinctBy { game -> game.id }
                    val hasMore = page.size == PAGE_SIZE
                    it.copy(
                        games = games, isLoading = false, loaded = true,
                        nextOffset = offset + page.size, hasMore = hasMore,
                        focusedIndex = it.focusedIndex.coerceAtMost(FAVORITES_HEADER_COUNT + games.size - if (hasMore) 0 else 1)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }
}
