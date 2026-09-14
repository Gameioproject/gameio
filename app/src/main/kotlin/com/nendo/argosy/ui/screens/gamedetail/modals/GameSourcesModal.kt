package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.addon.AddonSourceMatch
import com.nendo.argosy.ui.common.messageRes
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.delegates.GameSourcesDelegate
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatBytes

@Composable
fun GameSourcesModal(
    delegate: GameSourcesDelegate,
    onDownload: (AddonSourceMatch) -> Unit,
    onRetry: () -> Unit,
    onManageAddons: () -> Unit
) {
    val state by delegate.state.collectAsState()
    val currentDownload by rememberUpdatedState(onDownload)
    val currentRetry by rememberUpdatedState(onRetry)
    val currentManage by rememberUpdatedState(onManageAddons)
    val activate: (Int) -> Unit = { index ->
        val current = delegate.state.value
        when {
            index < current.sourceChoices.size -> {
                val match = current.sourceChoices[index]
                delegate.dismiss()
                currentDownload(match)
            }
            index == current.sourceChoices.size -> if (!current.loading) currentRetry()
            index == current.sourceChoices.size + 1 -> { delegate.dismiss(); currentManage() }
            else -> delegate.dismiss()
        }
    }
    val currentActivate by rememberUpdatedState(activate)
    val handler = remember(delegate) {
        object : InputHandler {
            override fun onUp() = InputResult.HANDLED.also { delegate.move(-1) }
            override fun onDown() = InputResult.HANDLED.also { delegate.move(1) }
            override fun onConfirm() = InputResult.HANDLED.also { currentActivate(delegate.state.value.focusedIndex) }
            override fun onBack() = InputResult.HANDLED.also { delegate.dismiss() }
            override fun onLeft() = InputResult.HANDLED
            override fun onRight() = InputResult.HANDLED
            override fun onMenu() = InputResult.HANDLED
            override fun onPrevSection() = InputResult.HANDLED
            override fun onNextSection() = InputResult.HANDLED
            override fun onPrevTrigger() = InputResult.HANDLED
            override fun onNextTrigger() = InputResult.HANDLED
            override fun onSecondaryAction() = InputResult.HANDLED
            override fun onContextMenu() = InputResult.HANDLED
            override fun onSelect() = InputResult.HANDLED
            override fun onLeftStickClick() = InputResult.HANDLED
            override fun onRightStickClick() = InputResult.HANDLED
            override fun onLongConfirm() = InputResult.HANDLED
        }
    }
    ModalInputEffect(state.visible, handler)
    if (!state.visible) return
    val failure = state.failure ?: state.result.failures.firstOrNull()?.reason
    val summary = when {
        state.loading -> stringResource(R.string.game_sources_checking)
        state.onDevice -> stringResource(R.string.game_sources_on_device)
        state.result.enabledAddonCount == 0 && failure == null -> stringResource(R.string.game_sources_import_prompt)
        failure != null -> stringResource(failure.messageRes)
        state.result.isConfirmedMissing -> stringResource(R.string.game_sources_missing)
        else -> stringResource(R.string.game_sources_choose)
    }
    CenteredModal(title = stringResource(R.string.game_sources_title), baseWidth = Dimens.modalWidthLg,
        onDismiss = delegate::dismiss) {
        Text(summary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Dimens.spacingMd))
        val listState = rememberLazyListState()
        FocusedScroll(listState, state.focusedIndex)
        LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            items(state.sourceChoices.size, key = { "${state.sourceChoices[it].addonId}:${state.sourceChoices[it].source.id}" }) { index ->
                val match = state.sourceChoices[index]
                OptionItem(label = match.source.filename, icon = Icons.Default.Download,
                    value = listOfNotNull(match.addonName, match.source.region, match.source.size?.let(::formatBytes)).joinToString(" · "),
                    isFocused = state.focusedIndex == index, onClick = { delegate.focus(index); activate(index) })
            }
            item("retry") {
                OptionItem(label = stringResource(R.string.game_sources_retry), icon = Icons.Default.Refresh,
                    isFocused = state.focusedIndex == state.sourceChoices.size, isEnabled = !state.loading,
                    onClick = { activate(state.sourceChoices.size) })
            }
            item("addons") {
                OptionItem(label = stringResource(R.string.game_sources_manage), icon = Icons.Default.Settings,
                    isFocused = state.focusedIndex == state.sourceChoices.size + 1,
                    onClick = { activate(state.sourceChoices.size + 1) })
            }
            item("close") {
                OptionItem(label = stringResource(R.string.game_sources_close), icon = Icons.Default.Close,
                    isFocused = state.focusedIndex == state.sourceChoices.size + 2, onClick = delegate::dismiss)
            }
        }
    }
}
