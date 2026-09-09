package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.util.Log
import com.nendo.argosy.ui.common.messageRes
import com.nendo.argosy.R
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.remote.romm.DEFAULT_SERVER_URL
import com.nendo.argosy.data.remote.romm.SignInResult
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "ServerSettingsDelegate"

class ServerSettingsDelegate @Inject constructor(
    private val romMRepository: RomMRepository,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()


    fun updateState(newState: ServerState) {
        _state.value = newState
    }

    fun checkRommConnection(scope: CoroutineScope) {
        val url = _state.value.rommUrl
        if (url.isBlank()) {
            _state.update { it.copy(connectionStatus = ConnectionStatus.NOT_CONFIGURED) }
            return
        }

        scope.launch {
            _state.update { it.copy(connectionStatus = ConnectionStatus.CHECKING) }
            try {
                val result = romMRepository.getLibrarySummary()
                val status = if (result is RomMResult.Success) {
                    ConnectionStatus.ONLINE
                } else {
                    ConnectionStatus.OFFLINE
                }
                _state.update { it.copy(connectionStatus = status) }
            } catch (e: Exception) {
                Log.e(TAG, "checkRommConnection: failed", e)
                _state.update { it.copy(connectionStatus = ConnectionStatus.OFFLINE) }
            }
        }
    }

    fun startRommConfig(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                rommConfiguring = true,
                rommConfigUrl = DEFAULT_SERVER_URL,
                rommConfigUsername = "",
                rommConfigPassword = "",
                rommConfigError = null
            )
        }
        onFocusReset()
    }

    fun cancelRommConfig(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                rommConfiguring = false,
                rommConfigUrl = "",
                rommConfigUsername = "",
                rommConfigPassword = "",
                rommConfigError = null,
                rommConnecting = false
            )
        }
        onFocusReset()
    }

    fun setRommConfigUrl(url: String) {
        _state.update { it.copy(rommConfigUrl = url) }
    }

    fun setRommConfigUsername(username: String) {
        _state.update { it.copy(rommConfigUsername = username) }
    }

    fun setRommConfigPassword(password: String) {
        _state.update { it.copy(rommConfigPassword = password) }
    }

    fun clearRommFocusField() {
        _state.update { it.copy(rommFocusField = null) }
    }

    fun setRommFocusField(index: Int) {
        _state.update { it.copy(rommFocusField = index) }
    }

    fun requestRommSignOut(scope: CoroutineScope, pendingUploads: suspend () -> Int) {
        scope.launch {
            val pending = pendingUploads()
            _state.update {
                it.copy(showRommSignOutConfirm = true, rommSignOutPendingUploads = pending)
            }
        }
    }

    fun cancelRommSignOut() {
        _state.update { it.copy(showRommSignOutConfirm = false) }
    }

    fun confirmRommSignOut(scope: CoroutineScope, onSignedOut: suspend () -> Unit) {
        if (_state.value.rommSigningOut) return
        scope.launch {
            _state.update { it.copy(showRommSignOutConfirm = false, rommSigningOut = true) }
            try {
                val result = romMRepository.signOut()
                if (result is AccountRemovalResult.SwitchInProgress) {
                    _state.update {
                        it.copy(
                            rommSigningOut = false,
                            rommConfigError = context.getString(
                                R.string.settings_server_delegate_signout_switch_in_progress
                            )
                        )
                    }
                    return@launch
                }
                if (result is AccountRemovalResult.Refused) {
                    _state.update {
                        it.copy(
                            rommSigningOut = false,
                            rommConfigError = context.getString(
                                R.string.settings_server_delegate_signout_refused,
                                result.pending.describe()
                            )
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        rommSigningOut = false,
                        rommUrl = "",
                        rommUsername = "",
                        rommVersion = null,
                        connectionStatus = ConnectionStatus.NOT_CONFIGURED
                    )
                }
                onSignedOut()
            } catch (e: Exception) {
                Log.e(TAG, "confirmRommSignOut: failed", e)
                _state.update { it.copy(rommSigningOut = false, rommConfigError = e.message) }
            }
        }
    }

    /** Probes the entered URL and auto-selects the version-appropriate auth method, mirroring the first-run wizard. */
    fun commitRommUrl(scope: CoroutineScope) {
        val state = _state.value
        if (state.rommConnecting || state.rommConfigUrl.isBlank()) return
        scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }
            when (val result = romMRepository.probeServerVersion(state.rommConfigUrl)) {
                is RomMResult.Success ->
                    _state.update { it.copy(rommConnecting = false, rommConfigError = null) }
                is RomMResult.Error ->
                    _state.update { it.copy(rommConnecting = false, rommConfigError = result.message) }
            }
        }
    }

    fun connectToRomm(scope: CoroutineScope, onSuccess: suspend () -> Unit) {
        val state = _state.value
        if (state.rommConnecting) return
        if (state.rommConfigUrl.isBlank()) return
        if (state.rommConfigUsername.isBlank() || state.rommConfigPassword.isBlank()) {
            _state.update {
                it.copy(rommConfigError = context.getString(R.string.settings_romm_config_credentials_required))
            }
            return
        }

        scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }
            val result = romMRepository.connectWithPassword(
                url = DEFAULT_SERVER_URL,
                username = state.rommConfigUsername,
                password = state.rommConfigPassword
            )
            when (result) {
                is SignInResult.Connected -> {
                    _state.update {
                        it.copy(
                            rommConnecting = false,
                            rommConfiguring = false,
                            connectionStatus = ConnectionStatus.ONLINE,
                            rommUrl = DEFAULT_SERVER_URL,
                            rommUsername = state.rommConfigUsername,
                            // The password only ever existed to mint the token.
                            rommConfigUsername = "",
                            rommConfigPassword = ""
                        )
                    }
                    onSuccess()
                }
                is SignInResult.AddedAccount ->
                    _state.update { it.copy(rommConnecting = false, rommConfiguring = false) }
                is SignInResult.Failed ->
                    _state.update { it.copy(rommConnecting = false, rommConfigError = context.getString(result.messageRes)) }
            }
        }
    }
}
