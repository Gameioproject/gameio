package com.nendo.argosy.ui.screens.gamedetail.comments

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.remote.romm.*
import com.nendo.argosy.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameCommentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<GameCommentsRepository>()
    private val session = GameCommentsSession(mockk(), RomMUser(7, "Player", true, "admin"), "https://game.test")
    private lateinit var vm: GameCommentsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.isCurrentSession(any()) } returns true
        coEvery { repository.openSession() } returns session
        coEvery { repository.comments(session, 10, any(), any()) } returns page(listOf(comment(1)))
        vm = GameCommentsViewModel(repository)
        vm.open(10, "A game")
        dispatcher.scheduler.advanceUntilIdle()
    }
    @After fun cleanup() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test fun `body limits preserve whole emoji and console deletion removes incomplete pairs`() {
        vm.compose()
        vm.editDraft("a".repeat(1999) + "🎮" + "extra")
        assertEquals("a".repeat(1999) + "🎮", vm.state.value.draft.text)
        vm.editDraft("hello🎮".dropLast(1))
        assertEquals("hello", vm.state.value.draft.text)
    }

    @Test fun `failed post preserves plain text draft and spoiler then retry posts once`() {
        val text = "<b>not html</b>\nThat ending 😭"
        coEvery { repository.post(session, 10, any()) } throws GameCommentsException(GameCommentsFailure.UNAVAILABLE)
        vm.compose(); vm.editDraft(text); vm.toggleSpoiler(); vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(text, vm.state.value.draft.text)
        assertTrue(vm.state.value.draft.spoiler)
        assertEquals(CommentsPanel.COMPOSE, vm.state.value.panel)
        coEvery { repository.post(session, 10, GameCommentWrite(text, true)) } returns comment(2).copy(body = text, spoiler = true)
        coEvery { repository.comments(session, 10, "newest", 1) } returns page(listOf(comment(1)), total = 2, offset = 1)
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.draft.text)
        assertEquals(listOf(2L, 1L), vm.state.value.items.map { it.id })
        assertEquals(2, vm.state.value.nextOffset)
        assertEquals("newest", vm.state.value.sort)
        coVerify(exactly = 2) { repository.post(session, 10, any()) }
    }

    @Test fun `reply to a reply reloads root from first page rather than skipping unseen replies`() {
        coEvery { repository.comments(session, 10, any(), 0) } returns page(listOf(comment(1).copy(replyCount = 30)))
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        val reply = comment(100).copy(parentId = 1)
        vm.openActions(reply); vm.chooseAction(CommentAction.REPLY); vm.editDraft("Same!")
        coEvery { repository.post(session, 10, GameCommentWrite("Same!", false, 100)) } returns comment(131).copy(parentId = 1)
        coEvery { repository.replies(session, 1, 0) } returns page((100L..119L).map { comment(it).copy(parentId = 1) }, total = 31)
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(31, vm.state.value.items.single().replyCount)
        assertEquals(20, vm.state.value.replies[1]?.nextOffset)
        assertEquals(31, vm.state.value.replies[1]?.total)
        coVerify(exactly = 1) { repository.replies(session, 1, 0) }
    }

    @Test fun `deletion reloads offset zero so pagination cannot skip the next thread`() {
        coEvery { repository.delete(session, 1) } returns Unit
        coEvery { repository.comments(session, 10, "top", 0) } returns page(listOf(comment(2)), total = 30)
        vm.openActions(comment(1)); vm.confirmDelete(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
        assertEquals(1, vm.state.value.nextOffset)
        assertEquals(30, vm.state.value.total)
    }

    @Test fun `reopening cached comments after account switch clears old draft and refetches viewer state`() {
        vm.compose(); vm.editDraft("Old account draft")
        every { repository.isCurrentSession(session) } returns false
        val next = GameCommentsSession(mockk(), RomMUser(8, "Other player", true, "user"), "https://game.test")
        coEvery { repository.openSession() } returns next
        coEvery { repository.comments(next, 10, any(), any()) } returns page(emptyList())
        vm.open(10, "A game"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(8L, vm.state.value.userId)
        assertEquals("", vm.state.value.draft.text)
        assertFalse(vm.state.value.isAdmin)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun `account change clears account-specific draft and moderation privileges`() {
        vm.compose(); vm.editDraft("Private draft")
        coEvery { repository.post(session, 10, any()) } throws GameCommentsException(GameCommentsFailure.ACCOUNT_CHANGED)
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.draft.text)
        assertNull(vm.state.value.userId)
        assertFalse(vm.state.value.isAdmin)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun `opening another game cancels old requests and never displays its result`() {
        coEvery { repository.comments(session, 10, any(), any()) } coAnswers { delay(5000); page(listOf(comment(99))) }
        coEvery { repository.comments(session, 20, any(), any()) } returns page(listOf(comment(20).copy(igdbId = 20)))
        vm.refresh(); dispatcher.scheduler.runCurrent()
        vm.open(20, "Other game"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(20L, vm.state.value.igdbId)
        assertEquals(listOf(20L), vm.state.value.items.map { it.id })
    }

    @Test fun `cancelled old auxiliary request cannot clear new game loading indicator`() {
        coEvery { repository.blocks(session) } coAnswers { delay(5000); emptyList() }
        coEvery { repository.comments(session, 20, any(), any()) } coAnswers { delay(1000); page(emptyList()) }
        vm.showBlocks(); dispatcher.scheduler.runCurrent()
        vm.open(20, "Other game"); dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.loading)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertEquals(20L, vm.state.value.igdbId)
    }

    @Test fun `options exposes blocks and admin reports and handles report failures without losing context`() {
        val report = GameCommentReport(1, 1, gameTitle = "A game", igdbId = 10, reporter = GameCommentAuthor(9, "Reporter"), reason = "Spam", bodySnapshot = "Original text", status = "pending", createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z")
        coEvery { repository.blocks(session) } returns listOf(GameCommentAuthor(9, "Blocked"))
        coEvery { repository.unblock(session, 9) } returns Unit
        vm.panel(CommentsPanel.OPTIONS); vm.focus(0); vm.activate(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(CommentsPanel.BLOCKS, vm.state.value.panel)
        vm.focus(1); vm.activate(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.blockedAuthors.isEmpty())
        coEvery { repository.reports(session, 0) } returns GameCommentReportPage(listOf(report), 1, 20, 0)
        vm.panel(CommentsPanel.OPTIONS); vm.focus(1); vm.activate(); dispatcher.scheduler.advanceUntilIdle()
        vm.selectReport(1)
        coEvery { repository.moderate(session, 1, false) } throws GameCommentsException(GameCommentsFailure.UNAVAILABLE)
        vm.moderate(false); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(report, vm.state.value.selectedReport)
        assertEquals(CommentsPanel.REPORT_DETAIL, vm.state.value.panel)
    }

    @Test fun `modal consumes unused buttons and wraps focus inside comments`() {
        var back = false
        val handler = GameCommentsInputHandler(vm) { back = true }
        handler.onUp()
        assertEquals(vm.state.value.focusCount() - 1, vm.state.value.focusIndex)
        val results = listOf(handler.onMenu(), handler.onSecondaryAction(), handler.onContextMenu(), handler.onPrevSection(), handler.onNextSection(), handler.onPrevTrigger(), handler.onNextTrigger(), handler.onSelect(), handler.onLeftStickClick(), handler.onRightStickClick(), handler.onLongConfirm())
        assertTrue(results.all { it.handled })
        assertEquals(vm.state.value.focusCount() - 1, vm.state.value.focusIndex)
        assertTrue(handler.onBack().handled); assertTrue(back)
    }

    @Test fun `deleted root retains replies but replies cannot target that deleted conversation`() {
        val root = comment(1).copy(deleted = true, body = "", author = null, replyCount = 1)
        val reply = comment(2).copy(parentId = 1)
        val state = GameCommentsState(items = listOf(root), replies = mapOf(1L to CommentRepliesState(listOf(reply), expanded = true)), selectedComment = reply, selectedRootDeleted = true, userId = 7)
        val entry = state.entries().filterIsInstance<CommentsEntry.Comment>().last()
        assertTrue(entry.rootDeleted)
        assertFalse(CommentAction.REPLY in state.actions())
        assertTrue(CommentAction.EDIT in state.actions())
    }

    private fun comment(id: Long) = GameComment(id, 10, author = GameCommentAuthor(7, "Player"), body = "Comment $id", createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z", canEdit = true, canDelete = true)
    private fun page(items: List<GameComment>, total: Int = items.size, offset: Int = 0) = GameCommentPage(items, total, 20, offset)
}
