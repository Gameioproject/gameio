package com.nendo.argosy.ui.screens.gamedetail.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.GAME_COMMENT_MAX_LENGTH
import com.nendo.argosy.data.remote.romm.GAME_COMMENT_REPORT_MAX_LENGTH
import com.nendo.argosy.data.remote.romm.GameComment
import com.nendo.argosy.data.remote.romm.GameCommentWrite
import com.nendo.argosy.data.repository.GameCommentsException
import com.nendo.argosy.data.repository.GameCommentsFailure
import com.nendo.argosy.data.repository.GameCommentsRepository
import com.nendo.argosy.data.repository.GameCommentsSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val COMMENTS_HEADER_ACTIONS = 5

@HiltViewModel
class GameCommentsViewModel @Inject constructor(private val repository: GameCommentsRepository) : ViewModel() {
    private val _state = MutableStateFlow(GameCommentsState())
    val state = _state.asStateFlow()
    private var session: GameCommentsSession? = null
    private var loadJob: Job? = null
    private var generation = 0
    private val avatarRequests = mutableSetOf<Long>()
    private val avatarSlots = Semaphore(4)
    private val _scrollPages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollPages = _scrollPages.asSharedFlow()
    fun scrollPage(direction: Int) { _scrollPages.tryEmit(direction) }
    fun loadAvatar(author: com.nendo.argosy.data.remote.romm.GameCommentAuthor?) {
        val connection = session ?: return
        if (author?.avatarUrl == null || author.id in _state.value.avatars || !avatarRequests.add(author.id)) return
        val revision = generation
        viewModelScope.launch {
            try {
                val bytes = avatarSlots.withPermit { repository.avatar(connection, author.id) } ?: return@launch
                if (revision == generation) _state.update { it.copy(avatars = (it.avatars.entries.toList().takeLast(15).associate { entry -> entry.toPair() }) + (author.id to bytes)) }
            } catch (error: Exception) { if (error is CancellationException) throw error }
            finally { avatarRequests.remove(author.id) }
        }
    }

    fun open(igdbId: Long?, title: String) {
        if (_state.value.igdbId == igdbId && _state.value.title == title && session?.let(repository::isCurrentSession) == true) return
        generation++
        viewModelScope.coroutineContext.cancelChildren()
        session = null
        avatarRequests.clear()
        _state.value = GameCommentsState(igdbId = igdbId, title = title)
        refresh()
    }

    fun refresh() {
        if (_state.value.mutating) return
        val gameId = _state.value.igdbId ?: return
        generation++
        val revision = generation
        loadJob?.cancel()
        _state.update { it.copy(loading = true, loadingMore = false, error = null, notice = null) }
        loadJob = viewModelScope.launch {
            try {
                val currentSession = repository.openSession()
                val page = repository.comments(currentSession, gameId, _state.value.sort)
                if (revision != generation) return@launch
                session = currentSession
                _state.update { it.copy(
                    loading = false, items = page.items, total = page.total,
                    nextOffset = page.offset + page.items.size, replies = emptyMap(),
                    userId = currentSession.user.id, username = currentSession.user.username,
                    isAdmin = currentSession.user.role == "admin", serverUrl = currentSession.serverUrl,
                    avatars = if (it.userId == currentSession.user.id && it.serverUrl == currentSession.serverUrl) it.avatars else emptyMap(),
                    draft = if (it.userId == null || it.userId == currentSession.user.id && it.serverUrl == currentSession.serverUrl) it.draft else CommentDraft(),
                    focusIndex = it.focusIndex.coerceAtMost(COMMENTS_HEADER_ACTIONS + page.items.size - 1)
                ) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == generation) failure(error)
            }
        }
    }

    fun setSort(sort: String) {
        if (sort !in listOf("top", "newest") || sort == _state.value.sort || _state.value.mutating) return
        _state.update { it.copy(sort = sort, focusIndex = if (sort == "top") 1 else 2) }
        refresh()
    }

    fun loadMore() {
        val current = _state.value
        val gameId = current.igdbId ?: return
        val connection = session ?: return
        if (current.loading || current.loadingMore || current.nextOffset >= current.total) return
        val revision = generation
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            try {
                val page = repository.comments(connection, gameId, current.sort, current.nextOffset)
                if (revision == generation) _state.update { it.copy(
                    loadingMore = false, items = (it.items + page.items).distinctBy(GameComment::id),
                    total = page.total, nextOffset = page.offset + page.items.size
                ) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == generation) failure(error)
            }
        }
    }

    fun toggleReplies(parentId: Long) {
        val thread = _state.value.replies[parentId]
        if (thread?.expanded == true) {
            _state.update { it.copy(replies = it.replies + (parentId to thread.copy(expanded = false))) }
            clampFocus()
        } else if (thread != null && thread.items.isNotEmpty()) {
            _state.update { it.copy(replies = it.replies + (parentId to thread.copy(expanded = true))) }
        } else loadReplies(parentId)
    }

    fun loadReplies(parentId: Long) {
        val connection = session ?: return
        val thread = _state.value.replies[parentId] ?: CommentRepliesState()
        if (thread.loading) return
        val revision = generation
        _state.update { it.copy(replies = it.replies + (parentId to thread.copy(expanded = true, loading = true, error = null))) }
        viewModelScope.launch {
            try {
                val page = repository.replies(connection, parentId, thread.nextOffset)
                if (revision == generation) _state.update { current ->
                    val latest = current.replies[parentId] ?: return@update current
                    current.copy(replies = current.replies + (parentId to latest.copy(
                        items = (latest.items + page.items).distinctBy(GameComment::id), loading = false,
                        total = page.total, nextOffset = page.offset + page.items.size
                    )))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (revision == generation) _state.update { current ->
                    val latest = current.replies[parentId] ?: return@update current
                    current.copy(replies = current.replies + (parentId to latest.copy(loading = false, error = reason(error))))
                }
            }
        }
    }

    fun openActions(comment: GameComment, rootDeleted: Boolean = false) {
        _state.update { it.copy(selectedComment = comment, selectedRootDeleted = rootDeleted, panel = CommentsPanel.ACTIONS, panelFocusIndex = 0, error = null) }
    }

    fun chooseAction(action: CommentAction) {
        val comment = _state.value.selectedComment ?: return
        when (action) {
            CommentAction.LIKE, CommentAction.UNLIKE -> like(comment)
            CommentAction.REPLY -> compose(CommentComposeKind.REPLY, comment)
            CommentAction.EDIT -> compose(CommentComposeKind.EDIT, comment)
            CommentAction.REPORT -> compose(CommentComposeKind.REPORT, comment)
            CommentAction.DELETE -> panel(CommentsPanel.CONFIRM_DELETE)
            CommentAction.BLOCK -> panel(CommentsPanel.CONFIRM_BLOCK)
            CommentAction.REVEAL -> {
                reveal(comment.id)
                panel(CommentsPanel.CONVERSATION)
            }
        }
    }

    fun reveal(commentId: Long) = _state.update { it.copy(revealedSpoilers = it.revealedSpoilers + commentId) }

    fun like(comment: GameComment) = mutate {
        val updated = repository.like(it, comment.id, !comment.liked)
        replaceComment(updated)
        panel(CommentsPanel.CONVERSATION)
    }

    fun compose(kind: CommentComposeKind = CommentComposeKind.NEW, comment: GameComment? = null) {
        val current = _state.value
        val draft = if (current.draft.kind == kind && current.draft.commentId == comment?.id) current.draft
        else CommentDraft(kind, comment?.id, if (kind == CommentComposeKind.EDIT) comment?.body.orEmpty() else "", if (kind == CommentComposeKind.EDIT) comment?.spoiler == true else false)
        _state.update { it.copy(panel = CommentsPanel.COMPOSE, panelFocusIndex = 0, draft = draft, error = null) }
    }

    fun editDraft(text: String) {
        val limit = if (_state.value.draft.kind == CommentComposeKind.REPORT) GAME_COMMENT_REPORT_MAX_LENGTH else GAME_COMMENT_MAX_LENGTH
        val clean = buildString {
            var index = 0
            while (index < text.length) {
                val point = text.codePointAt(index)
                if (point !in 0xD800..0xDFFF) appendCodePoint(point)
                index += Character.charCount(point)
            }
        }
        val bounded = if (clean.codePointCount(0, clean.length) > limit) clean.substring(0, clean.offsetByCodePoints(0, limit)) else clean
        _state.update { it.copy(draft = it.draft.copy(text = bounded)) }
    }

    fun toggleSpoiler() = _state.update { it.copy(draft = it.draft.copy(spoiler = !it.draft.spoiler)) }
    fun keyboard(visible: Boolean) = _state.update { it.copy(keyboardVisible = visible) }

    fun submit() {
        val draft = _state.value.draft
        val gameId = _state.value.igdbId ?: return
        if (draft.text.isBlank()) return
        mutate { connection ->
            var replyRoot: Long? = null
            when (draft.kind) {
                CommentComposeKind.REPORT -> repository.report(connection, draft.commentId ?: return@mutate, draft.text.trim())
                CommentComposeKind.EDIT -> replaceComment(repository.edit(connection, draft.commentId ?: return@mutate, GameCommentWrite(draft.text.trim(), draft.spoiler)))
                CommentComposeKind.NEW, CommentComposeKind.REPLY -> {
                    val comment = repository.post(connection, gameId, GameCommentWrite(draft.text.trim(), draft.spoiler, draft.commentId))
                    replyRoot = comment.parentId
                    _state.update { current ->
                        if (comment.parentId == null) current.copy(sort = "newest", items = listOf(comment), total = current.total + 1, nextOffset = 1, replies = emptyMap(), focusIndex = COMMENTS_HEADER_ACTIONS)
                        else current.copy(
                            items = current.items.map { if (it.id == comment.parentId) it.copy(replyCount = it.replyCount + 1) else it },
                            replies = current.replies + (comment.parentId to CommentRepliesState(expanded = true))
                        )
                    }
                }
            }
            _state.update { it.copy(panel = CommentsPanel.CONVERSATION, draft = CommentDraft(), keyboardVisible = false, notice = if (draft.kind == CommentComposeKind.REPORT) R.string.comments_report_sent else null) }
            if (draft.kind == CommentComposeKind.NEW) loadMore()
            if (draft.kind == CommentComposeKind.REPLY) {
                replyRoot?.let(::loadReplies)
            }
        }
    }

    fun confirmDelete() {
        val comment = _state.value.selectedComment ?: return
        mutate {
            repository.delete(it, comment.id)
            _state.update { current -> current.copy(panel = CommentsPanel.CONVERSATION) }
        }.invokeOnCompletion { cause -> if (cause == null && _state.value.error == null) refresh() }
    }

    fun confirmBlock() {
        val author = _state.value.selectedComment?.author ?: return
        mutate {
            repository.block(it, author.id)
            _state.update { state -> state.copy(panel = CommentsPanel.CONVERSATION, items = emptyList(), replies = emptyMap(), total = 0, notice = R.string.comments_blocked_notice) }
        }.invokeOnCompletion { cause -> if (cause == null && _state.value.error == null) refresh() }
    }

    fun showBlocks() = auxiliary(CommentsPanel.BLOCKS) { connection ->
        val blocks = repository.blocks(connection)
        _state.update { it.copy(blockedAuthors = blocks) }
    }

    fun unblock(authorId: Long) = mutate {
        repository.unblock(it, authorId)
        _state.update { current -> current.copy(blockedAuthors = current.blockedAuthors.filterNot { it.id == authorId }, notice = R.string.comments_unblocked_notice) }
        clampFocus()
    }

    fun showReports(more: Boolean = false) {
        if (!_state.value.isAdmin) return
        val offset = if (more) _state.value.reportNextOffset else 0
        auxiliary(CommentsPanel.MODERATION) { connection ->
            val page = repository.reports(connection, offset)
            _state.update { it.copy(reports = (if (more) it.reports + page.items else page.items).distinctBy { report -> report.id }, reportTotal = page.total, reportNextOffset = page.offset + page.items.size) }
        }
    }

    fun selectReport(reportId: Long) {
        val report = _state.value.reports.firstOrNull { it.id == reportId } ?: return
        _state.update { it.copy(selectedReport = report, panel = CommentsPanel.REPORT_DETAIL, panelFocusIndex = 0, error = null) }
    }

    fun moderate(remove: Boolean) {
        val report = _state.value.selectedReport ?: return
        mutate {
            repository.moderate(it, report.id, remove)
            _state.update { current -> current.copy(reports = current.reports.filterNot { if (remove) it.commentId == report.commentId else it.id == report.id }, panel = CommentsPanel.MODERATION, panelFocusIndex = 0, selectedReport = null, notice = R.string.comments_report_resolved) }
        }.invokeOnCompletion { cause -> if (cause == null && _state.value.error == null) showReports() }
    }

    fun panel(panel: CommentsPanel) = _state.update { it.copy(panel = panel, panelFocusIndex = 0, error = null) }

    fun back(): Boolean {
        val current = _state.value
        if (current.keyboardVisible) { keyboard(false); return true }
        when (current.panel) {
            CommentsPanel.CONVERSATION -> return false
            CommentsPanel.CONFIRM_DELETE, CommentsPanel.CONFIRM_BLOCK -> panel(CommentsPanel.ACTIONS)
            CommentsPanel.CONFIRM_REMOVE -> panel(CommentsPanel.REPORT_DETAIL)
            CommentsPanel.REPORT_DETAIL -> panel(CommentsPanel.MODERATION)
            CommentsPanel.BLOCKS, CommentsPanel.MODERATION -> { panel(CommentsPanel.CONVERSATION); refresh() }
            else -> panel(CommentsPanel.CONVERSATION)
        }
        return true
    }

    fun activate() {
        val current = _state.value
        if (current.mutating) return
        val index = current.activeFocus()
        when (current.panel) {
            CommentsPanel.CONVERSATION -> when (index) {
                0 -> compose()
                1 -> setSort("top")
                2 -> setSort("newest")
                3 -> panel(CommentsPanel.OPTIONS)
                4 -> refresh()
                else -> current.entries().getOrNull(index - COMMENTS_HEADER_ACTIONS)?.let(::activateEntry)
            }
            CommentsPanel.ACTIONS -> current.actions().getOrNull(index)?.let(::chooseAction) ?: back()
            CommentsPanel.COMPOSE -> when {
                index == 0 -> keyboard(true)
                index == current.focusCount() - 1 -> back()
                index == current.focusCount() - 2 -> submit()
                else -> toggleSpoiler()
            }
            CommentsPanel.OPTIONS -> when {
                index == 0 -> showBlocks()
                current.isAdmin && index == 1 -> showReports()
                else -> back()
            }
            CommentsPanel.BLOCKS -> when {
                index == 0 -> showBlocks()
                index <= current.blockedAuthors.size -> unblock(current.blockedAuthors[index - 1].id)
                else -> back()
            }
            CommentsPanel.MODERATION -> when {
                index == 0 -> showReports()
                index <= current.reports.size -> selectReport(current.reports[index - 1].id)
                index == current.reports.size + 1 && current.reportNextOffset < current.reportTotal -> showReports(more = true)
                else -> back()
            }
            CommentsPanel.REPORT_DETAIL -> when (index) {
                0 -> moderate(false)
                1 -> panel(CommentsPanel.CONFIRM_REMOVE)
                else -> back()
            }
            CommentsPanel.CONFIRM_DELETE -> if (index == 1) confirmDelete() else back()
            CommentsPanel.CONFIRM_BLOCK -> if (index == 1) confirmBlock() else back()
            CommentsPanel.CONFIRM_REMOVE -> if (index == 1) moderate(true) else back()
        }
    }

    fun activateEntry(entry: CommentsEntry) {
        when (entry) {
            is CommentsEntry.Comment -> openActions(entry.comment, entry.rootDeleted)
            is CommentsEntry.Replies -> toggleReplies(entry.parentId)
            is CommentsEntry.MoreReplies -> loadReplies(entry.parentId)
            CommentsEntry.More -> loadMore()
        }
    }

    fun focus(index: Int) = _state.update { if (it.panel == CommentsPanel.CONVERSATION) it.copy(focusIndex = index) else it.copy(panelFocusIndex = index) }
    fun moveVertical(delta: Int) {
        val current = _state.value
        if (current.panel != CommentsPanel.CONVERSATION) { move(delta); return }
        if (current.focusIndex < COMMENTS_HEADER_ACTIONS && delta > 0 && current.entries().isNotEmpty()) focus(COMMENTS_HEADER_ACTIONS)
        else if (current.focusIndex == COMMENTS_HEADER_ACTIONS && delta < 0) focus(0)
        else move(delta)
    }

    fun moveHorizontal(delta: Int) {
        val current = _state.value
        if (current.panel != CommentsPanel.CONVERSATION) move(delta)
        else if (current.focusIndex < COMMENTS_HEADER_ACTIONS) focus((current.focusIndex + delta).mod(COMMENTS_HEADER_ACTIONS))
    }

    fun move(delta: Int) {
        val count = _state.value.focusCount()
        if (count > 0) focus((_state.value.activeFocus() + delta).mod(count))
    }

    private fun clampFocus() {
        val current = _state.value
        focus(current.activeFocus().coerceIn(0, (current.focusCount() - 1).coerceAtLeast(0)))
    }

    private fun replaceComment(comment: GameComment) = _state.update { current -> current.copy(
        items = current.items.map { if (it.id == comment.id) comment else it },
        replies = current.replies.mapValues { (_, thread) -> thread.copy(items = thread.items.map { if (it.id == comment.id) comment else it }) },
        selectedComment = if (current.selectedComment?.id == comment.id) comment else current.selectedComment
    ) }

    private fun auxiliary(panel: CommentsPanel, action: suspend (GameCommentsSession) -> Unit) {
        if (_state.value.mutating || _state.value.loading) return
        val revision = generation
        panel(panel)
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try { action(session ?: repository.openSession().also { session = it }) }
            catch (error: Exception) { if (error is CancellationException) throw error; if (revision == generation) failure(error) }
            finally { if (revision == generation) _state.update { it.copy(loading = false) } }
        }
    }

    private fun mutate(action: suspend (GameCommentsSession) -> Unit): Job = viewModelScope.launch {
        if (_state.value.mutating) return@launch
        val revision = generation
        _state.update { it.copy(mutating = true, error = null, notice = null) }
        try { action(session ?: throw GameCommentsException(GameCommentsFailure.NOT_CONNECTED)) }
        catch (error: Exception) { if (error is CancellationException) throw error; if (revision == generation) failure(error) }
        finally { if (revision == generation) _state.update { it.copy(mutating = false) } }
    }

    private fun failure(error: Exception) {
        val failure = reason(error)
        if (failure == GameCommentsFailure.ACCOUNT_CHANGED) session = null
        _state.update { current ->
            val safe = if (failure == GameCommentsFailure.ACCOUNT_CHANGED) current.copy(items = emptyList(), replies = emptyMap(), reports = emptyList(), blockedAuthors = emptyList(), draft = CommentDraft(), selectedComment = null, selectedReport = null, userId = null, username = "", isAdmin = false, avatars = emptyMap(), panel = CommentsPanel.CONVERSATION) else current
            safe.copy(error = failure, retryAfterSeconds = (error as? GameCommentsException)?.retryAfterSeconds, loading = false, loadingMore = false)
        }
    }

    private fun reason(error: Exception) = (error as? GameCommentsException)?.reason ?: GameCommentsFailure.UNAVAILABLE
}

internal fun GameCommentsState.activeFocus() = if (panel == CommentsPanel.CONVERSATION) focusIndex else panelFocusIndex
internal fun GameCommentsState.focusCount(): Int = when (panel) {
    CommentsPanel.CONVERSATION -> COMMENTS_HEADER_ACTIONS + entries().size
    CommentsPanel.ACTIONS -> actions().size + 1
    CommentsPanel.COMPOSE -> if (draft.kind == CommentComposeKind.REPORT) 3 else 4
    CommentsPanel.OPTIONS -> if (isAdmin) 3 else 2
    CommentsPanel.BLOCKS -> blockedAuthors.size + 2
    CommentsPanel.MODERATION -> reports.size + 2 + if (reportNextOffset < reportTotal) 1 else 0
    CommentsPanel.REPORT_DETAIL -> 3
    CommentsPanel.CONFIRM_DELETE, CommentsPanel.CONFIRM_BLOCK, CommentsPanel.CONFIRM_REMOVE -> 2
}
