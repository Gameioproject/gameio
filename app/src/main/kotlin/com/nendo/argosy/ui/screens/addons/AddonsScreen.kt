package com.nendo.argosy.ui.screens.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.messageRes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.filebrowser.FileBrowserMode
import com.nendo.argosy.ui.filebrowser.FileBrowserScreen
import com.nendo.argosy.ui.filebrowser.FileFilter
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.screens.firstrun.SetupBackdrop
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun AddonsScreen(
    onExit: () -> Unit,
    setup: Boolean = false,
    viewModel: AddonsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actions = remember(state.addons, state.failure) { state.actions }
    val handler = remember(viewModel, onExit) { AddonsInputHandler(viewModel, onExit) }
    ModalInputEffect(active = true, handler = handler)
    val listState = rememberLazyListState()
    FocusedScroll(listState, state.focusedIndex)

    SetupBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(stringResource(R.string.addons_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(if (setup) R.string.addons_setup_description else R.string.addons_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.unreadableCount > 0) Text(
                stringResource(R.string.addons_unreadable_warning),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
            )
            state.importedName?.let {
                Text(stringResource(R.string.addons_imported, it), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            }
            state.failure?.let {
                Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(actions, key = { _, action -> action.toString() }) { index, action ->
                    val addon = when (action) {
                        is AddonAction.Toggle -> state.addons.find { it.manifest.id == action.addonId }
                        is AddonAction.Reimport -> state.addons.find { it.manifest.id == action.addonId }
                        is AddonAction.Remove -> state.addons.find { it.manifest.id == action.addonId }
                        else -> null
                    }
                    val title = when (action) {
                        AddonAction.Import -> stringResource(R.string.addons_import)
                        AddonAction.Account -> stringResource(R.string.addons_account)
                        AddonAction.Exit -> stringResource(if (setup) R.string.addons_setup_continue else R.string.addons_back)
                        AddonAction.Reload -> stringResource(R.string.addons_reload)
                        is AddonAction.Toggle -> addon?.manifest?.name.orEmpty()
                        is AddonAction.Reimport -> stringResource(R.string.addons_reimport, addon?.manifest?.name.orEmpty())
                        is AddonAction.Remove -> stringResource(R.string.addons_remove, addon?.manifest?.name.orEmpty())
                    }
                    val subtitle = when (action) {
                        AddonAction.Import -> stringResource(R.string.addons_import_hint)
                        AddonAction.Account -> stringResource(when {
                            state.accountNeedsReset -> R.string.addons_account_attention
                            state.hasAccount -> R.string.addons_account_connected
                            else -> R.string.addons_account_optional
                        })
                        AddonAction.Exit -> if (setup) stringResource(R.string.addons_local_hint) else ""
                        AddonAction.Reload -> ""
                        is AddonAction.Toggle -> stringResource(
                            if (addon?.enabled == true) R.string.addons_enabled else R.string.addons_disabled,
                            addon?.manifest?.version.orEmpty(), addon?.manifest?.allowedHosts?.joinToString(", ").orEmpty()
                        )
                        is AddonAction.Reimport -> stringResource(R.string.addons_reimport_hint)
                        is AddonAction.Remove -> stringResource(R.string.addons_remove_hint)
                    }
                    val icon = when (action) {
                        AddonAction.Import -> Icons.Default.Add
                        AddonAction.Account -> Icons.Default.Key
                        AddonAction.Exit -> Icons.Default.CheckCircle
                        AddonAction.Reload, is AddonAction.Reimport -> Icons.Default.Refresh
                        is AddonAction.Toggle -> if (addon?.enabled == true) Icons.Default.ToggleOn else Icons.Default.ToggleOff
                        is AddonAction.Remove -> Icons.Default.Delete
                    }
                    if (action is AddonAction.Toggle) {
                        SwitchPreference(
                            icon = icon, title = title, subtitle = subtitle,
                            isEnabled = addon?.enabled == true,
                            isFocused = !state.modalOpen && !state.busy && index == state.focusedIndex,
                            onToggle = { viewModel.activate(action, onExit) }
                        )
                    } else {
                        ActionPreference(
                            icon = icon, title = title, subtitle = subtitle,
                            isFocused = !state.modalOpen && index == state.focusedIndex,
                            isEnabled = !state.busy,
                            isDangerous = action is AddonAction.Remove,
                            onClick = { viewModel.activate(action, onExit) }
                        )
                    }
                }
                if (state.addons.isEmpty() && !state.busy) {
                    item(key = "empty") {
                        Text(stringResource(R.string.addons_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.pickerOpen) {
            FileBrowserScreen(
                mode = FileBrowserMode.FILE_SELECTION,
                title = stringResource(R.string.addons_file_title),
                fileFilter = remember { FileFilter(extensions = setOf("json")) },
                onPathSelected = viewModel::importFile,
                onDismiss = viewModel::closePicker
            )
        }
        state.removeId?.let { id ->
            val name = state.addons.find { it.manifest.id == id }?.manifest?.name.orEmpty()
            ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(R.string.addons_remove, name),
                message = stringResource(R.string.addons_remove_confirm),
                confirmLabel = stringResource(R.string.addons_remove_confirm_button),
                onConfirm = viewModel::confirmRemove,
                onDismiss = viewModel::cancelRemove,
                destructive = true
            )
        }
        if (state.accountOpen) AddonAccountModal(state, viewModel)
    }
}
