package com.nendo.argosy.ui.screens.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.GameioMark
import com.nendo.argosy.ui.theme.Dimens

import com.nendo.argosy.ui.theme.generated.ComponentDefaults.ConsoleUi

@Composable
internal fun WelcomeStep(isFocused: Boolean, onGetStarted: () -> Unit) {
    SetupBackdrop {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spacingXl)
        ) {
            GameioMark(tile = false, modifier = Modifier.size(ConsoleUi.brandSizeDp.dp))
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.firstrun_welcome_brand_name),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.firstrun_brand_tagline),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = stringResource(R.string.firstrun_welcome_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXl))
            SetupPrimaryButton(
                text = stringResource(R.string.firstrun_welcome_button_start),
                isFocused = isFocused,
                onClick = onGetStarted
            )
        }
    }
}

@Composable
internal fun RommLoginStep(
    username: String,
    password: String,
    isConnecting: Boolean,
    signUpMode: Boolean = false,
    onToggleSignUp: (() -> Unit)? = null,
    error: String?,
    focusedIndex: Int,
    rommFocusField: Int?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onClearFocusField: () -> Unit,
    keyboardField: Int?,
    keyboardText: String,
    onKeyboardTextChange: (String) -> Unit,
    onKeyboardDismiss: () -> Unit
) {
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager: FocusManager = LocalFocusManager.current
    val keyboard: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current

    val canConnect = username.isNotBlank() && password.isNotBlank()

    LaunchedEffect(rommFocusField) {
        when (rommFocusField) {
            0 -> usernameFocusRequester.requestFocus()
            1 -> passwordFocusRequester.requestFocus()
        }
        if (rommFocusField != null) {
            onClearFocusField()
        }
    }
    LaunchedEffect(focusedIndex) {
        val onAField = focusedIndex <= 1
        if (!onAField) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    SetupBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val twoPane = maxWidth > maxHeight

            val brand: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GameioMark(tile = false, modifier = Modifier.size(if (twoPane) ConsoleUi.brandSizeDp.dp else ConsoleUi.compactBrandSizeDp.dp))
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                    Text(
                        text = stringResource(R.string.firstrun_login_brand_name),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.firstrun_brand_tagline),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    Text(
                        text = stringResource(R.string.firstrun_romm_sign_in_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = ConsoleUi.brandWidthDp.dp)
                    )
                }
            }

            val keyboardOpen = keyboardField != null
            val keyboardPane: @Composable () -> Unit = {
                com.nendo.argosy.ui.components.ConsoleKeyboardOverlay(
                    query = keyboardText,
                    onQueryChange = onKeyboardTextChange,
                    onDismiss = onKeyboardDismiss,
                    placeholder = when (keyboardField) {
                        0 -> stringResource(R.string.settings_romm_config_username_label)
                        else -> stringResource(R.string.settings_romm_config_password_label)
                    },
                    embedded = true,
                    isPassword = keyboardField == 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val form: @Composable () -> Unit = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = ConsoleUi.formWidthDp.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLg))
                        .border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Dimens.radiusLg))
                        .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingLg)
                ) {
                    Text(
                        text = stringResource(
                            if (signUpMode) R.string.firstrun_signup_title
                            else R.string.firstrun_romm_sign_in_button
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.spacingMd)
                    )
                    if (signUpMode) {
                        Text(
                            text = stringResource(R.string.firstrun_signup_test_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.spacingMd)
                        )
                    }
                        SetupTextField(
                            value = username,
                            onValueChange = onUsernameChange,
                            label = stringResource(R.string.settings_romm_config_username_label),
                            gamepadFocused = focusedIndex == 0,
                            focusRequester = usernameFocusRequester,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { passwordFocusRequester.requestFocus() }
                            )
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacingSm))
                        SetupTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            label = stringResource(R.string.settings_romm_config_password_label),
                            gamepadFocused = focusedIndex == 1,
                            focusRequester = passwordFocusRequester,
                            isPassword = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (!isConnecting && canConnect) {
                                        keyboard?.hide()
                                        focusManager.clearFocus()
                                        onConnect()
                                    }
                                }
                            )
                        )
                        SetupError(error)
                        Spacer(modifier = Modifier.height(Dimens.spacingLg))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                            SetupPrimaryButton(
                                text = when {
                                    isConnecting && signUpMode ->
                                        stringResource(R.string.firstrun_signup_working)
                                    isConnecting -> stringResource(R.string.firstrun_romm_sign_in_connecting)
                                    signUpMode -> stringResource(R.string.firstrun_signup_button)
                                    else -> stringResource(R.string.firstrun_romm_sign_in_button)
                                },
                                isFocused = focusedIndex == 2,
                                enabled = !isConnecting && canConnect,
                                onClick = onConnect
                            )
                        }
                        if (onToggleSignUp != null) {
                            Spacer(modifier = Modifier.height(Dimens.spacingSm))
                            SetupSecondaryButton(
                                text = stringResource(
                                    if (signUpMode) R.string.firstrun_signup_have_account
                                    else R.string.firstrun_signup_switch
                                ),
                                isFocused = focusedIndex == 3,
                                enabled = !isConnecting,
                                onClick = onToggleSignUp
                            )
                        }
                }
            }

            if (twoPane) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd)
                ) {
                    Box(
                        modifier = Modifier.weight(if (keyboardOpen) 1.15f else 0.9f),
                        contentAlignment = Alignment.Center
                    ) { if (keyboardOpen) keyboardPane() else brand() }
                    Spacer(modifier = Modifier.width(Dimens.spacingXl))
                    Box(
                        modifier = Modifier.weight(if (keyboardOpen) 0.85f else 1.1f),
                        contentAlignment = Alignment.Center
                    ) { form() }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd)
                ) {
                    if (keyboardOpen) keyboardPane() else brand()
                    Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    form()
                }
            }
        }
    }
}


@Composable
internal fun RommSuccessStep(
    gameCount: Int,
    platformCount: Int,
    isFocused: Boolean,
    onContinue: () -> Unit
) {
    SetupBackdrop {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spacingXl)
        ) {
            GameioMark(tile = false, modifier = Modifier.size(ConsoleUi.brandSizeDp.dp))
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(Dimens.iconLg)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = stringResource(R.string.firstrun_romm_success_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = stringResource(R.string.gameio_login_success_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    R.string.firstrun_romm_success_library,
                    pluralStringResource(R.plurals.firstrun_romm_success_game_count, gameCount, gameCount),
                    pluralStringResource(R.plurals.firstrun_romm_success_platform_count, platformCount, platformCount)
                ),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXl))
            SetupPrimaryButton(
                text = stringResource(R.string.firstrun_romm_success_button_continue),
                isFocused = isFocused,
                onClick = onContinue
            )
        }
    }
}
