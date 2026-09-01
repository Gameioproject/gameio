package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val FIELD_URL = 0
private const val FIELD_USERNAME = 1
private const val FIELD_PASSWORD = 2
private const val ACTION_SIGN_IN = 3
private const val ACTION_CANCEL = 4

/**
 * Server address plus the username and password the server owner handed out.
 *
 * The password is sent once to mint a long-lived token and is never stored on the device.
 */
@Composable
fun RomMConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    var wasUrlFocused by remember { mutableStateOf(false) }
    val urlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val canSubmit = !uiState.server.rommConnecting &&
        uiState.server.rommConfigUrl.isNotBlank() &&
        uiState.server.rommConfigUsername.isNotBlank() &&
        uiState.server.rommConfigPassword.isNotBlank()

    LaunchedEffect(uiState.server.rommFocusField) {
        when (uiState.server.rommFocusField) {
            FIELD_URL -> urlFocusRequester.requestFocus()
            FIELD_USERNAME -> usernameFocusRequester.requestFocus()
            FIELD_PASSWORD -> passwordFocusRequester.requestFocus()
        }
        if (uiState.server.rommFocusField != null) {
            viewModel.clearRommFocusField()
        }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        OutlinedTextField(
            value = uiState.server.rommConfigUrl,
            onValueChange = { viewModel.setRommConfigUrl(it) },
            label = { Text(stringResource(R.string.settings_romm_config_server_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_romm_config_server_url_placeholder)) },
            singleLine = true,
            shape = inputShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { usernameFocusRequester.requestFocus() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(urlFocusRequester)
                .onFocusChanged { fs ->
                    // Probing on blur is what turns a bad address into "can't reach that server"
                    // instead of a confusing "wrong password" after the credentials are typed.
                    if (wasUrlFocused && !fs.isFocused && uiState.server.rommConfigUrl.isNotBlank()) {
                        viewModel.commitRommUrl()
                    }
                    wasUrlFocused = fs.isFocused
                }
                .then(
                    if (uiState.focusedIndex == FIELD_URL)
                        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                    else Modifier
                )
        )

        OutlinedTextField(
            value = uiState.server.rommConfigUsername,
            onValueChange = { viewModel.setRommConfigUsername(it) },
            label = { Text(stringResource(R.string.settings_romm_config_username_label)) },
            singleLine = true,
            shape = inputShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(usernameFocusRequester)
                .then(
                    if (uiState.focusedIndex == FIELD_USERNAME)
                        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                    else Modifier
                )
        )

        OutlinedTextField(
            value = uiState.server.rommConfigPassword,
            onValueChange = { viewModel.setRommConfigPassword(it) },
            label = { Text(stringResource(R.string.settings_romm_config_password_label)) },
            singleLine = true,
            shape = inputShape,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (canSubmit) {
                        keyboard?.hide()
                        viewModel.connectToRomm()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .then(
                    if (uiState.focusedIndex == FIELD_PASSWORD)
                        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                    else Modifier
                )
        )

        if (uiState.server.rommConfigError != null) {
            Text(
                text = uiState.server.rommConfigError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = if (uiState.server.rommConnecting) {
                stringResource(R.string.settings_romm_config_connect_connecting_title)
            } else {
                stringResource(R.string.settings_romm_config_sign_in_title)
            },
            subtitle = stringResource(R.string.settings_romm_config_sign_in_subtitle),
            isFocused = uiState.focusedIndex == ACTION_SIGN_IN,
            onClick = { viewModel.connectToRomm() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_romm_config_cancel_title),
            subtitle = stringResource(R.string.settings_romm_config_cancel_subtitle),
            isFocused = uiState.focusedIndex == ACTION_CANCEL,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}
