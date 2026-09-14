package com.nendo.argosy.ui.screens.gamedetail.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.GAME_COMMENT_MAX_LENGTH
import com.nendo.argosy.data.remote.romm.GAME_COMMENT_REPORT_MAX_LENGTH
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens

@Composable
internal fun CommentPanels(state: GameCommentsState, viewModel: GameCommentsViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel, listState) { viewModel.scrollPages.collect { direction -> listState.scrollBy(direction * listState.layoutInfo.viewportSize.height * 0.75f) } }
    val labels = when (state.panel) {
        CommentsPanel.ACTIONS -> state.actions().map { stringResource(it.labelRes) } + stringResource(R.string.comments_back)
        CommentsPanel.OPTIONS -> buildList {
            add(stringResource(R.string.comments_blocked_users))
            if (state.isAdmin) add(stringResource(R.string.comments_moderation))
            add(stringResource(R.string.comments_back))
        }
        CommentsPanel.BLOCKS -> listOf(stringResource(R.string.comments_refresh)) + state.blockedAuthors.map { "${it.username} · ${stringResource(R.string.comments_unblock)}" } + stringResource(R.string.comments_back)
        CommentsPanel.MODERATION -> buildList {
            add(stringResource(R.string.comments_refresh))
            state.reports.forEach { add("${it.author?.username.orEmpty()} · ${it.bodySnapshot.take(100)}") }
            if (state.reportNextOffset < state.reportTotal) add(stringResource(R.string.comments_more_reports))
            add(stringResource(R.string.comments_back))
        }
        CommentsPanel.REPORT_DETAIL -> listOf(stringResource(R.string.comments_dismiss_report), stringResource(R.string.comments_remove_comment), stringResource(R.string.comments_back))
        CommentsPanel.CONFIRM_DELETE -> listOf(stringResource(R.string.comments_cancel), stringResource(R.string.comments_delete))
        CommentsPanel.CONFIRM_BLOCK -> listOf(stringResource(R.string.comments_cancel), stringResource(R.string.comments_block))
        CommentsPanel.CONFIRM_REMOVE -> listOf(stringResource(R.string.comments_cancel), stringResource(R.string.comments_remove_comment))
        else -> emptyList()
    }
    LaunchedEffect(state.panel, state.panelFocusIndex) {
        val index = if (state.panel == CommentsPanel.COMPOSE) state.panelFocusIndex else state.panelFocusIndex + 1
        if (index < listState.layoutInfo.totalItemsCount && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.animateScrollToItem(index)
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        if (state.panel == CommentsPanel.COMPOSE) {
            item {
                val limit = if (state.draft.kind == CommentComposeKind.REPORT) GAME_COMMENT_REPORT_MAX_LENGTH else GAME_COMMENT_MAX_LENGTH
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                    Text(state.username, style = MaterialTheme.typography.titleMedium)
                    if (state.draft.kind == CommentComposeKind.REPLY || state.draft.kind == CommentComposeKind.EDIT || state.draft.kind == CommentComposeKind.REPORT) {
                        Text(state.selectedComment?.author?.username.orEmpty(), style = MaterialTheme.typography.labelMedium)
                    }
                    BasicTextField(value = state.draft.text, onValueChange = viewModel::editDraft,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), minLines = 3, maxLines = 6,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.radiusControl))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .argosyFocusIndicators(state.panelFocusIndex == 0, FocusIndicators.Ring).padding(Dimens.spacingMd),
                        decorationBox = { inner -> Box {
                            if (state.draft.text.isEmpty()) Text(stringResource(if (state.draft.kind == CommentComposeKind.REPORT) R.string.comments_report_placeholder else R.string.comments_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        } }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        CommentControl(stringResource(R.string.comments_keyboard), state.panelFocusIndex == 0) { viewModel.focus(0); viewModel.keyboard(true) }
                        Text(stringResource(R.string.comments_characters, state.draft.text.codePointCount(0, state.draft.text.length), limit), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.draft.kind != CommentComposeKind.REPORT) item {
                CommentControl(stringResource(if (state.draft.spoiler) R.string.comments_spoiler_on else R.string.comments_spoiler_off), state.panelFocusIndex == 1, Modifier.fillMaxWidth()) { viewModel.focus(1); viewModel.toggleSpoiler() }
            }
            item {
                val index = state.focusCount() - 2
                CommentControl(stringResource(when (state.draft.kind) {
                    CommentComposeKind.EDIT -> R.string.comments_save_edit
                    CommentComposeKind.REPORT -> R.string.comments_send_report
                    else -> R.string.comments_post
                }), state.panelFocusIndex == index, Modifier.fillMaxWidth()) { viewModel.focus(index); viewModel.submit() }
            }
            item { CommentControl(stringResource(R.string.comments_cancel), state.panelFocusIndex == state.focusCount() - 1, Modifier.fillMaxWidth()) { viewModel.back() } }
        } else {
            item { PanelContext(state) }
            itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                CommentControl(label, state.panelFocusIndex == index, Modifier.fillMaxWidth()) { viewModel.focus(index); viewModel.activate() }
            }
        }
    }
}

@Composable
private fun PanelContext(state: GameCommentsState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        when (state.panel) {
            CommentsPanel.ACTIONS -> {
                Text(state.selectedComment?.author?.username.orEmpty(), style = MaterialTheme.typography.titleMedium)
                val comment = state.selectedComment
                if (comment?.spoiler != true || comment.id in state.revealedSpoilers) Text(comment?.body.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
            CommentsPanel.OPTIONS -> Text(stringResource(R.string.comments_options), style = MaterialTheme.typography.titleMedium)
            CommentsPanel.BLOCKS -> {
                Text(stringResource(R.string.comments_blocked_users), style = MaterialTheme.typography.titleMedium)
                if (!state.loading && state.blockedAuthors.isEmpty()) Text(stringResource(R.string.comments_no_blocks))
            }
            CommentsPanel.MODERATION -> {
                Text(stringResource(R.string.comments_moderation), style = MaterialTheme.typography.titleMedium)
                if (!state.loading && state.reports.isEmpty()) Text(stringResource(R.string.comments_no_reports))
            }
            CommentsPanel.REPORT_DETAIL -> state.selectedReport?.let { report ->
                Text(stringResource(R.string.comments_report_detail), style = MaterialTheme.typography.titleMedium)
                Text(report.gameTitle.ifEmpty { stringResource(R.string.comments_game_reference, report.igdbId) }, style = MaterialTheme.typography.labelLarge)
                Text(report.author?.username ?: stringResource(R.string.comments_deleted_user))
                Text(stringResource(R.string.comments_reported_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(report.bodySnapshot, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.comments_report_by, report.reporter.username), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.comments_report_reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(report.reason)
            }
            CommentsPanel.CONFIRM_DELETE -> Text(stringResource(R.string.comments_delete_confirm))
            CommentsPanel.CONFIRM_BLOCK -> Text(stringResource(R.string.comments_block_confirm, state.selectedComment?.author?.username.orEmpty()))
            CommentsPanel.CONFIRM_REMOVE -> Text(stringResource(R.string.comments_remove_confirm))
            else -> Unit
        }
    }
}
