package com.nendo.argosy.ui.screens.addons

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.nendo.argosy.ui.components.FocusedScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.messageRes
import com.nendo.argosy.ui.components.ConsoleKeyboardOverlay
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.firstrun.SetupPrimaryButton
import com.nendo.argosy.ui.screens.firstrun.SetupSecondaryButton
import com.nendo.argosy.ui.screens.firstrun.SetupTextField
import com.nendo.argosy.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddonAccountModal(state: AddonsUiState, viewModel: AddonsViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.tokenFocus, state.tokenKeyboard, state.hasAccount) {
        if (state.tokenFocus != 0 || state.tokenKeyboard || state.hasAccount) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val paste = { clipboard.getText()?.text?.let(viewModel::setToken); Unit }
    val openTokenPage = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://real-debrid.com/apitoken"))) }
            .onFailure { viewModel.tokenPageUnavailable() }
        Unit
    }
    val handler = remember(viewModel, clipboard, context) {
        AddonAccountInputHandler(viewModel, paste, openTokenPage)
    }
    ModalInputEffect(true, handler)
    ModalScaffold(visible = true, onDismiss = viewModel::closeAccount) {
        BoxWithConstraints {
            Column(
                modifier = Modifier.heightIn(max = maxHeight - Dimens.spacingLg * 2).padding(Dimens.spacingLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(stringResource(R.string.addons_account), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                val listState = rememberLazyListState()
                FocusedScroll(listState, if (state.hasAccount || state.tokenFocus == 0) 1 else 2)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    item(key = "description") {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                            Text(
                                stringResource(if (state.hasAccount) R.string.addons_account_connected_hint else R.string.addons_account_description),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.tokenPageFailed) Text(stringResource(R.string.addons_account_browser_error), color = MaterialTheme.colorScheme.error)
                            if (state.tokenBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.tokenFailure?.let {
                                Text(
                                    stringResource(if (state.accountNeedsReset) R.string.addons_account_storage_error else it.messageRes),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (!state.hasAccount) item(key = "token") {
                        SetupTextField(
                            value = state.token,
                            onValueChange = viewModel::setToken,
                            label = stringResource(R.string.addons_account_token),
                            gamepadFocused = state.tokenFocus == 0 && !state.tokenKeyboard,
                            focusRequester = remember { FocusRequester() },
                            isPassword = true,
                            enabled = !state.tokenBusy,
                            onFocusChanged = { if (it) viewModel.focusTokenField() },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                    item(key = "actions") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            if (state.hasAccount) {
                                SetupSecondaryButton(stringResource(R.string.addons_account_disconnect), state.tokenFocus == 0, viewModel::disconnect, !state.tokenBusy)
                                SetupPrimaryButton(stringResource(R.string.addons_account_done), state.tokenFocus == 1, viewModel::closeAccount)
                            } else {
                                SetupSecondaryButton(stringResource(R.string.addons_account_paste), state.tokenFocus == 1, paste, !state.tokenBusy)
                                SetupSecondaryButton(stringResource(R.string.addons_account_get_token), state.tokenFocus == 2, openTokenPage, !state.tokenBusy)
                                SetupPrimaryButton(stringResource(R.string.addons_account_connect), state.tokenFocus == 3, viewModel::saveToken, state.token.isNotBlank() && !state.tokenBusy)
                                SetupSecondaryButton(stringResource(R.string.addons_account_cancel), state.tokenFocus == 4, viewModel::closeAccount)
                                if (state.accountNeedsReset) SetupSecondaryButton(
                                    stringResource(R.string.addons_account_reset), state.tokenFocus == 5,
                                    viewModel::disconnect, !state.tokenBusy
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.tokenKeyboard) ConsoleKeyboardOverlay(
        query = state.token,
        onQueryChange = viewModel::setToken,
        onDismiss = viewModel::closeTokenKeyboard,
        placeholder = stringResource(R.string.addons_account_token),
        isPassword = true
    )
}

internal class AddonAccountInputHandler(
    private val viewModel: AddonsViewModel,
    private val onPaste: () -> Unit,
    private val onOpenTokenPage: () -> Unit
) : InputHandler {
    override fun onUp(): InputResult { viewModel.moveTokenFocus(-1); return InputResult.HANDLED }
    override fun onDown(): InputResult { viewModel.moveTokenFocus(1); return InputResult.HANDLED }
    override fun onLeft(): InputResult { viewModel.moveTokenFocus(-1); return InputResult.HANDLED }
    override fun onRight(): InputResult { viewModel.moveTokenFocus(1); return InputResult.HANDLED }
    override fun onConfirm(): InputResult {
        val state = viewModel.state.value
        if (state.tokenKeyboard || state.tokenBusy) return InputResult.HANDLED
        if (state.hasAccount) {
            if (state.tokenFocus == 0) viewModel.disconnect() else viewModel.closeAccount()
        } else when (state.tokenFocus) {
            0 -> viewModel.openTokenKeyboard()
            1 -> onPaste()
            2 -> onOpenTokenPage()
            3 -> viewModel.saveToken()
            4 -> viewModel.closeAccount()
            5 -> if (state.accountNeedsReset) viewModel.disconnect()
        }
        return InputResult.HANDLED
    }
    override fun onBack(): InputResult {
        if (viewModel.state.value.tokenKeyboard) viewModel.closeTokenKeyboard() else viewModel.closeAccount()
        return InputResult.HANDLED
    }
    override fun onMenu() = InputResult.HANDLED
    override fun onSelect() = InputResult.HANDLED
    override fun onSecondaryAction() = InputResult.HANDLED
    override fun onContextMenu() = InputResult.HANDLED
    override fun onPrevSection() = InputResult.HANDLED
    override fun onNextSection() = InputResult.HANDLED
    override fun onPrevTrigger() = InputResult.HANDLED
    override fun onNextTrigger() = InputResult.HANDLED
    override fun onLeftStickClick() = InputResult.HANDLED
    override fun onRightStickClick() = InputResult.HANDLED
    override fun onLongConfirm() = InputResult.HANDLED
}
