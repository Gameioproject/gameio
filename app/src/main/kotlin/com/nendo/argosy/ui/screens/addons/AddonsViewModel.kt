package com.nendo.argosy.ui.screens.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.addon.AddonDebridResolver
import com.nendo.argosy.data.addon.AddonException
import com.nendo.argosy.data.addon.AddonFailure
import com.nendo.argosy.data.addon.AddonRepository
import com.nendo.argosy.data.addon.InstalledAddon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddonAction {
    data object Import : AddonAction
    data object Account : AddonAction
    data object Exit : AddonAction
    data object Reload : AddonAction
    data class Toggle(val addonId: String) : AddonAction
    data class Reimport(val addonId: String) : AddonAction
    data class Remove(val addonId: String) : AddonAction
}

data class AddonsUiState(
    val addons: List<InstalledAddon> = emptyList(),
    val unreadableCount: Int = 0,
    val focusedIndex: Int = 0,
    val busy: Boolean = false,
    val failure: AddonFailure? = null,
    val importedName: String? = null,
    val pickerOpen: Boolean = false,
    val reimportId: String? = null,
    val removeId: String? = null,
    val accountOpen: Boolean = false,
    val hasAccount: Boolean = false,
    val accountNeedsReset: Boolean = false,
    val token: String = "",
    val tokenFocus: Int = 0,
    val tokenKeyboard: Boolean = false,
    val tokenBusy: Boolean = false,
    val tokenFailure: AddonFailure? = null,
    val tokenPageFailed: Boolean = false
) {
    val actions: List<AddonAction> get() = buildList {
        add(AddonAction.Import)
        add(AddonAction.Account)
        add(AddonAction.Exit)
        addons.forEach {
            add(AddonAction.Toggle(it.manifest.id))
            add(AddonAction.Reimport(it.manifest.id))
            add(AddonAction.Remove(it.manifest.id))
        }
        if (failure != null) add(AddonAction.Reload)
    }
    val modalOpen: Boolean get() = pickerOpen || removeId != null || accountOpen
}

@HiltViewModel
class AddonsViewModel @Inject constructor(
    private val repository: AddonRepository,
    private val debrid: AddonDebridResolver
) : ViewModel() {
    private val _state = MutableStateFlow(AddonsUiState())
    val state = _state.asStateFlow()
    private var tokenJob: Job? = null

    init {
        viewModelScope.launch {
            repository.addons.collect { addons ->
                _state.update { current ->
                    val focusedAction = current.actions.getOrNull(current.focusedIndex)
                    val updated = current.copy(addons = addons)
                    updated.copy(focusedIndex = updated.actions.indexOf(focusedAction).takeIf { it >= 0 }
                        ?: current.focusedIndex.coerceIn(0, updated.actions.lastIndex))
                }
            }
        }
        viewModelScope.launch { repository.unreadableCount.collect { count -> _state.update { it.copy(unreadableCount = count) } } }
        viewModelScope.launch { debrid.hasAccount.collect { connected -> _state.update { it.copy(hasAccount = connected) } } }
        reload()
    }

    fun reload() = operation {
        repository.load()
        try {
            debrid.loadAccount()
            _state.update { it.copy(accountNeedsReset = false) }
        } catch (e: AddonException) {
            if (e.reason == AddonFailure.STORAGE) _state.update { it.copy(accountNeedsReset = true) }
            else throw e
        }
    }

    fun move(delta: Int) = _state.update {
        if (it.modalOpen || it.busy) it
        else it.copy(focusedIndex = (it.focusedIndex + delta).mod(it.actions.size))
    }

    fun activate(action: AddonAction, onExit: () -> Unit) {
        val current = _state.value
        if (current.modalOpen || current.busy) return
        _state.update { it.copy(focusedIndex = it.actions.indexOf(action).coerceAtLeast(0)) }
        when (action) {
            AddonAction.Import -> _state.update { it.copy(pickerOpen = true, reimportId = null, importedName = null) }
            AddonAction.Account -> _state.update { it.copy(accountOpen = true, token = "", tokenFocus = 0, tokenFailure = if (it.accountNeedsReset) AddonFailure.STORAGE else null, tokenPageFailed = false) }
            AddonAction.Exit -> onExit()
            AddonAction.Reload -> reload()
            is AddonAction.Toggle -> current.addons.find { it.manifest.id == action.addonId }?.let { addon ->
                operation { repository.setEnabled(action.addonId, !addon.enabled) }
            }
            is AddonAction.Reimport -> _state.update { it.copy(pickerOpen = true, reimportId = action.addonId, importedName = null) }
            is AddonAction.Remove -> _state.update { it.copy(removeId = action.addonId) }
        }
    }

    fun setFocusedEnabled(enabled: Boolean) {
        val current = _state.value
        if (current.modalOpen || current.busy) return
        val action = current.actions.getOrNull(current.focusedIndex) as? AddonAction.Toggle ?: return
        val addon = current.addons.find { it.manifest.id == action.addonId } ?: return
        if (addon.enabled != enabled) operation { repository.setEnabled(action.addonId, enabled) }
    }

    fun confirm(onExit: () -> Unit) = _state.value.actions.getOrNull(_state.value.focusedIndex)?.let { activate(it, onExit) }
    fun exit(onExit: () -> Unit) { if (!_state.value.busy && !_state.value.modalOpen) onExit() }
    fun closePicker() = _state.update { it.copy(pickerOpen = false, reimportId = null) }

    fun importFile(path: String) {
        val expectedId = _state.value.reimportId
        closePicker()
        operation {
            val addon = repository.importFile(path, expectedId)
            _state.update { it.copy(importedName = addon.manifest.name) }
        }
    }

    fun cancelRemove() = _state.update { it.copy(removeId = null) }
    fun confirmRemove() {
        val id = _state.value.removeId ?: return
        cancelRemove()
        operation { repository.remove(id) }
    }

    fun tokenPageUnavailable() = _state.update { it.copy(tokenPageFailed = true) }
    fun setToken(token: String) = _state.update {
        if (it.tokenBusy) it else it.copy(token = token.trim().take(512), tokenFailure = null)
    }
    fun focusTokenField() = _state.update { it.copy(tokenFocus = 0) }
    fun openTokenKeyboard() = _state.update { it.copy(tokenKeyboard = true, tokenFocus = 0) }
    fun closeTokenKeyboard() = _state.update { it.copy(tokenKeyboard = false) }
    fun moveTokenFocus(delta: Int) = _state.update {
        if (it.tokenKeyboard || it.tokenBusy) it else it.copy(tokenFocus = (it.tokenFocus + delta).mod(if (it.hasAccount) 2 else if (it.accountNeedsReset) 6 else 5))
    }
    fun closeAccount() {
        tokenJob?.cancel()
        _state.update { it.copy(accountOpen = false, token = "", tokenKeyboard = false, tokenBusy = false, tokenFailure = null) }
    }
    fun saveToken() {
        val token = _state.value.token
        if (token.isBlank() || _state.value.tokenBusy) return
        _state.update { it.copy(tokenBusy = true, tokenFailure = null) }
        tokenJob = viewModelScope.launch {
            try {
                debrid.setToken(token)
                _state.update { it.copy(token = "", tokenBusy = false, tokenFocus = 0, accountNeedsReset = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(tokenBusy = false, tokenFailure = (e as? AddonException)?.reason ?: AddonFailure.NETWORK) }
            }
        }
    }
    fun disconnect() {
        tokenJob = viewModelScope.launch {
            try {
                _state.update { it.copy(tokenBusy = true) }
                debrid.disconnect()
                _state.update {
                    it.copy(tokenBusy = false, token = "", tokenFocus = 0, tokenFailure = null,
                        tokenPageFailed = false, accountNeedsReset = false, failure = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(tokenBusy = false, tokenFailure = AddonFailure.STORAGE) }
            }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update {
            val updated = it.copy(busy = true, failure = null, importedName = null)
            updated.copy(focusedIndex = updated.focusedIndex.coerceAtMost(updated.actions.lastIndex))
        }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, failure = (e as? AddonException)?.reason ?: AddonFailure.STORAGE) }
            }
        }
    }
}
