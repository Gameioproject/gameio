package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.runtime.Composable
import com.nendo.argosy.ui.screens.firstrun.RommLoginStep
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel

@Composable
fun RomMConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val server = uiState.server
    RommLoginStep(
        username = server.rommConfigUsername,
        password = server.rommConfigPassword,
        isConnecting = server.rommConnecting,
        error = server.rommConfigError,
        focusedIndex = uiState.focusedIndex,
        rommFocusField = null,
        onUsernameChange = viewModel::setRommConfigUsername,
        onPasswordChange = viewModel::setRommConfigPassword,
        onConnect = viewModel::connectToRomm,
        onClearFocusField = {},
        keyboardField = server.rommFocusField,
        keyboardText = if (server.rommFocusField == 0) server.rommConfigUsername else server.rommConfigPassword,
        onKeyboardTextChange = {
            if (server.rommFocusField == 0) viewModel.setRommConfigUsername(it)
            else viewModel.setRommConfigPassword(it)
        },
        onKeyboardDismiss = viewModel::clearRommFocusField
    )
}
