package com.nendo.argosy.ui.screens.gamedetail.comments

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.GameComment
import com.nendo.argosy.data.remote.romm.GameCommentAuthor
import com.nendo.argosy.data.remote.romm.GameCommentReport
import com.nendo.argosy.data.repository.GameCommentsFailure

enum class CommentsPanel { CONVERSATION, ACTIONS, COMPOSE, OPTIONS, BLOCKS, MODERATION, REPORT_DETAIL, CONFIRM_DELETE, CONFIRM_BLOCK, CONFIRM_REMOVE }
enum class CommentComposeKind { NEW, REPLY, EDIT, REPORT }

data class CommentRepliesState(
    val items: List<GameComment> = emptyList(),
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val total: Int = 0,
    val nextOffset: Int = 0,
    val error: GameCommentsFailure? = null
)

data class CommentDraft(
    val kind: CommentComposeKind = CommentComposeKind.NEW,
    val commentId: Long? = null,
    val text: String = "",
    val spoiler: Boolean = false
)

data class GameCommentsState(
    val igdbId: Long? = null,
    val title: String = "",
    val username: String = "",
    val userId: Long? = null,
    val serverUrl: String = "",
    val isAdmin: Boolean = false,
    val avatars: Map<Long, ByteArray> = emptyMap(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val mutating: Boolean = false,
    val error: GameCommentsFailure? = null,
    val retryAfterSeconds: Int? = null,
    @StringRes val notice: Int? = null,
    val items: List<GameComment> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val sort: String = "top",
    val replies: Map<Long, CommentRepliesState> = emptyMap(),
    val revealedSpoilers: Set<Long> = emptySet(),
    val panel: CommentsPanel = CommentsPanel.CONVERSATION,
    val focusIndex: Int = 0,
    val panelFocusIndex: Int = 0,
    val selectedComment: GameComment? = null,
    val selectedRootDeleted: Boolean = false,
    val draft: CommentDraft = CommentDraft(),
    val keyboardVisible: Boolean = false,
    val blockedAuthors: List<GameCommentAuthor> = emptyList(),
    val reports: List<GameCommentReport> = emptyList(),
    val reportTotal: Int = 0,
    val reportNextOffset: Int = 0,
    val selectedReport: GameCommentReport? = null
)

sealed interface CommentsEntry {
    val key: String
    data class Comment(val comment: GameComment, val reply: Boolean = false, val rootDeleted: Boolean = false) : CommentsEntry { override val key = "comment:${comment.id}" }
    data class Replies(val parentId: Long, val count: Int, val expanded: Boolean) : CommentsEntry { override val key = "replies:$parentId" }
    data class MoreReplies(val parentId: Long, val loading: Boolean, val error: GameCommentsFailure?) : CommentsEntry { override val key = "more:$parentId" }
    data object More : CommentsEntry { override val key = "more" }
}

fun GameCommentsState.entries(): List<CommentsEntry> = buildList {
    items.forEach { comment ->
        add(CommentsEntry.Comment(comment))
        if (comment.replyCount > 0 || replies[comment.id]?.items?.isNotEmpty() == true) {
            val thread = replies[comment.id]
            add(CommentsEntry.Replies(comment.id, comment.replyCount, thread?.expanded == true))
            if (thread?.expanded == true) {
                thread.items.forEach { add(CommentsEntry.Comment(it, reply = true, rootDeleted = comment.deleted)) }
                if (thread.loading || thread.error != null || thread.nextOffset < thread.total) {
                    add(CommentsEntry.MoreReplies(comment.id, thread.loading, thread.error))
                }
            }
        }
    }
    if (nextOffset < total) add(CommentsEntry.More)
}

enum class CommentAction(@StringRes val labelRes: Int) {
    LIKE(R.string.comments_like), UNLIKE(R.string.comments_unlike), REPLY(R.string.comments_reply),
    EDIT(R.string.comments_edit), DELETE(R.string.comments_delete), REPORT(R.string.comments_report), BLOCK(R.string.comments_block), REVEAL(R.string.comments_show_spoiler)
}

fun GameCommentsState.actions(): List<CommentAction> = buildList {
    val comment = selectedComment ?: return@buildList
    if (comment.spoiler && comment.id !in revealedSpoilers) add(CommentAction.REVEAL)
    if (!comment.deleted) {
        add(if (comment.liked) CommentAction.UNLIKE else CommentAction.LIKE)
        if (!selectedRootDeleted) add(CommentAction.REPLY)
        if (comment.canEdit) add(CommentAction.EDIT)
        if (comment.canDelete) add(CommentAction.DELETE)
        if (comment.author != null && comment.author.id != userId) {
            add(CommentAction.REPORT)
            add(CommentAction.BLOCK)
        }
    }
}

@get:StringRes
val GameCommentsFailure.messageRes: Int get() = when (this) {
    GameCommentsFailure.NOT_CONNECTED, GameCommentsFailure.SIGN_IN -> R.string.comments_error_sign_in
    GameCommentsFailure.ACCOUNT_CHANGED -> R.string.comments_error_account_changed
    GameCommentsFailure.FORBIDDEN -> R.string.comments_error_permission
    GameCommentsFailure.NOT_FOUND -> R.string.comments_error_not_found
    GameCommentsFailure.RATE_LIMIT -> R.string.comments_error_rate_limit
    GameCommentsFailure.INVALID -> R.string.comments_error_invalid
    GameCommentsFailure.UNAVAILABLE -> R.string.comments_error_unavailable
}
