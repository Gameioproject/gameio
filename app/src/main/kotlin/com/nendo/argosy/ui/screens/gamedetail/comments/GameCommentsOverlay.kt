package com.nendo.argosy.ui.screens.gamedetail.comments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ConsoleKeyboardOverlay
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun GameCommentsOverlay(
    igdbId: Long?,
    gameTitle: String,
    onDismiss: () -> Unit,
    viewModel: GameCommentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(igdbId, gameTitle) { viewModel.open(igdbId, gameTitle) }
    val dismiss by rememberUpdatedState(onDismiss)
    val back = { if (!viewModel.back()) dismiss() }
    val currentBack by rememberUpdatedState(back)
    val handler = remember(viewModel) { GameCommentsInputHandler(viewModel) { currentBack() } }
    ModalInputEffect(true, handler)
    BackHandler(onBack = back)
    FooterHints(hints = listOf(
        InputButton.A to stringResource(R.string.ui_dual_file_picker_hint_select),
        InputButton.B to stringResource(R.string.comments_back),
        InputButton.LT_RT to stringResource(R.string.gamedetail_footer_section_scroll)
    ))
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickableNoFocus {}) {
        Column(Modifier.fillMaxSize().padding(Dimens.spacingLg), verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.comments_title), style = MaterialTheme.typography.headlineSmall)
                    Text(state.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CommentControl(stringResource(if (state.panel == CommentsPanel.CONVERSATION) R.string.comments_close else R.string.comments_back), false, onClick = back)
            }
            state.error?.let {
                Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                state.retryAfterSeconds?.let { seconds -> Text(pluralStringResource(R.plurals.comments_retry_after, seconds, seconds), style = MaterialTheme.typography.labelMedium) }
            }
            state.notice?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            if (state.loading || state.mutating) Text(stringResource(if (state.mutating) R.string.comments_working else R.string.comments_loading), style = MaterialTheme.typography.labelMedium)
            if (state.panel == CommentsPanel.CONVERSATION) {
                CommentConversation(state, viewModel, Modifier.weight(1f))
            } else {
                CommentPanels(state, viewModel, Modifier.weight(1f))
            }
        }
        if (state.keyboardVisible) {
            ConsoleKeyboardOverlay(
                query = state.draft.text,
                onQueryChange = viewModel::editDraft,
                onDismiss = { viewModel.keyboard(false) },
                placeholder = stringResource(if (state.draft.kind == CommentComposeKind.REPORT) R.string.comments_report_placeholder else R.string.comments_placeholder)
            )
        }
    }
}

@Composable
private fun CommentConversation(state: GameCommentsState, viewModel: GameCommentsViewModel, modifier: Modifier) {
    val entries = remember(state.items, state.replies, state.nextOffset, state.total) { state.entries() }
    val listState = rememberLazyListState()
    val headerState = rememberLazyListState()
    LaunchedEffect(viewModel, listState) { viewModel.scrollPages.collect { direction -> listState.scrollBy(direction * listState.layoutInfo.viewportSize.height * 0.75f) } }
    LaunchedEffect(state.focusIndex, entries.size) {
        if (state.focusIndex < COMMENTS_HEADER_ACTIONS) headerState.animateScrollToItem(state.focusIndex)
        else {
            val index = state.focusIndex - COMMENTS_HEADER_ACTIONS
            if (index < entries.size && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.animateScrollToItem(index)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
        val labels = listOf(R.string.comments_write, R.string.comments_top, R.string.comments_newest, R.string.comments_options, if (state.error != null) R.string.comments_retry else R.string.comments_refresh)
        LazyRow(state = headerState, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                CommentControl(stringResource(label), state.focusIndex == index, selected = index == 1 && state.sort == "top" || index == 2 && state.sort == "newest") {
                    viewModel.focus(index); viewModel.activate()
                }
            }
        }
        if (state.igdbId == null) Text(stringResource(R.string.comments_no_catalog))
        else if (!state.loading && state.items.isEmpty() && state.error == null) Text(stringResource(R.string.comments_empty), style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val focused = state.focusIndex == index + COMMENTS_HEADER_ACTIONS
                val activate = { viewModel.focus(index + COMMENTS_HEADER_ACTIONS); viewModel.activateEntry(entry) }
                when (entry) {
                    is CommentsEntry.Comment -> CommentCard(entry, state, focused, viewModel, activate)
                    is CommentsEntry.Replies -> CommentControl(stringResource(if (entry.expanded) R.string.comments_hide_replies else R.string.comments_show_replies, entry.count), focused, Modifier.fillMaxWidth(), onClick = activate)
                    is CommentsEntry.MoreReplies -> {
                        entry.error?.let { Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error) }
                        CommentControl(stringResource(if (entry.loading) R.string.comments_loading else if (entry.error != null) R.string.comments_retry else R.string.comments_more_replies), focused, Modifier.fillMaxWidth(), onClick = activate)
                    }
                    CommentsEntry.More -> CommentControl(stringResource(if (state.loadingMore) R.string.comments_loading else R.string.comments_more), focused, Modifier.fillMaxWidth(), onClick = activate)
                }
            }
        }
    }
}

@Composable
internal fun CommentControl(text: String, focused: Boolean, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clip(RoundedCornerShape(Dimens.radiusControl))
            .argosyFocusIndicators(focused, FocusIndicators.NavRow, selected = selected)
            .clickableNoFocus(onClick).heightIn(min = Dimens.menuRowHeight)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm))
}
