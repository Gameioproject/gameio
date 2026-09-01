package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.SignInResult
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.sync.AccountSwitchBlocker
import com.nendo.argosy.data.sync.AccountSwitchCoordinator
import com.nendo.argosy.data.sync.AccountSwitchOutcome
import com.nendo.argosy.data.sync.AccountSwitchProgress
import com.nendo.argosy.data.sync.AccountPendingWork
import com.nendo.argosy.data.sync.AccountRemovalService
import com.nendo.argosy.data.sync.UnflushedQueuePolicy
import com.nendo.argosy.ui.screens.settings.AccountSignInState
import com.nendo.argosy.ui.screens.settings.AccountUi
import com.nendo.argosy.ui.screens.settings.AccountsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Drives the accounts list, the add-account pairing loop, switching and removal.
 *
 * Add-account deliberately does not reuse [ServerSettingsDelegate.connectToRomm]: that path is
 * URL-entry-first and models a successful pairing as replacing the single stored connection. A
 * second account on a server this device already knows has no URL step, so pairing starts from
 * the active account's baseUrl and reports its own outcome.
 */
class AccountsSettingsDelegate @Inject constructor(
    private val accountRepository: RomMAccountRepository,
    private val switchCoordinator: AccountSwitchCoordinator,
    private val removalService: AccountRemovalService,
    private val romMRepository: RomMRepository,
    private val gameRepository: GameRepository,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    private var signInJob: Job? = null

    fun start(scope: CoroutineScope) {
        accountRepository.observeAccounts()
            .onEach { rows ->
                val ui = rows
                    .sortedByDescending { it.lastLoginAt }
                    .map { row ->
                        AccountUi(
                            id = row.id,
                            username = row.username.ifBlank {
                                context.getString(R.string.settings_accounts_delegate_fallback_username, row.rommUserId)
                            },
                            serverLabel = serverLabel(row.baseUrl),
                            isActive = row.isActive
                        )
                    }
                _state.update { it.copy(accounts = ui, isLoading = false) }
            }
            .launchIn(scope)

        var seededProgress = false
        switchCoordinator.progress
            .onEach { progress ->
                if (!seededProgress) {
                    seededProgress = true
                    return@onEach
                }
                applyProgress(progress)
            }
            .launchIn(scope)

        switchCoordinator.observeNeedsSync()
            .onEach { rows ->
                val titles = titlesFor(rows.mapNotNull { it.gameId })
                _state.update { it.copy(needsSyncSaveTitles = titles) }
            }
            .launchIn(scope)

        switchCoordinator.observeStatesNeedingSync()
            .onEach { rows ->
                val titles = titlesFor(rows.mapNotNull { it.gameId })
                _state.update { it.copy(needsSyncStateTitles = titles) }
            }
            .launchIn(scope)
    }

    fun moveRowActionFocus(direction: Int, actionCount: Int): Boolean {
        if (actionCount <= 1) return false
        _state.update { it.copy(rowActionIndex = (it.rowActionIndex + direction).mod(actionCount)) }
        return true
    }

    fun resetRowActionFocus() {
        _state.update { it.copy(rowActionIndex = 0) }
    }

    fun setRowActionIndex(index: Int) {
        _state.update { it.copy(rowActionIndex = index.coerceAtLeast(0)) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null, switchBlocker = null, switchFailure = null) }
    }

    fun requestSwitch(accountId: Long) {
        _state.update {
            it.copy(
                exitPromptAccountId = accountId,
                exitPromptIsForAdd = false,
                switchBlocker = null,
                switchFailure = null,
                notice = null
            )
        }
    }

    fun requestAddAccount() {
        val active = _state.value.activeAccount
        if (active == null) {
            _state.update {
                it.copy(notice = context.getString(R.string.settings_accounts_delegate_notice_no_server))
            }
            return
        }
        _state.update {
            it.copy(
                exitPromptAccountId = active.id,
                exitPromptIsForAdd = true,
                switchBlocker = null,
                switchFailure = null,
                notice = null
            )
        }
    }

    fun cancelExitPrompt() {
        _state.update { it.copy(exitPromptAccountId = null, exitPromptIsForAdd = false) }
    }

    /**
     * The user's answer to the prompt asking them to fully exit anything launched outside Argosy.
     * It is passed straight through as `confirmedExternalGamesClosed`; Argosy has no session
     * record for an externally launched game and cannot establish this on its own.
     */
    fun confirmExitPrompt(scope: CoroutineScope) {
        val current = _state.value
        val accountId = current.exitPromptAccountId ?: return
        val isForAdd = current.exitPromptIsForAdd
        _state.update { it.copy(exitPromptAccountId = null, exitPromptIsForAdd = false) }
        if (!isForAdd) {
            runSwitch(scope, accountId)
            return
        }
        scope.launch {
            val blocker = switchCoordinator.checkBlockers(confirmedExternalGamesClosed = true)
            if (blocker != null) {
                _state.update { it.copy(switchBlocker = blockerMessage(blocker)) }
                return@launch
            }
            startSignIn()
        }
    }

    private fun runSwitch(scope: CoroutineScope, accountId: Long) {
        if (_state.value.switchInProgress) return
        scope.launch {
            _state.update {
                it.copy(
                    switchInProgress = true,
                    switchProgressLabel = context.getString(R.string.settings_accounts_delegate_progress_preparing),
                    switchBlocker = null,
                    switchFailure = null,
                    notice = null
                )
            }
            clearSwitchIfIdle(
                switchCoordinator.switchTo(accountId, confirmedExternalGamesClosed = true)
            )
        }
    }

    /**
     * A failed switch leaves the marker set on purpose, which keeps launches blocked until the
     * run is finished. Without this the only way out is a reboot.
     */
    fun retryInterruptedSwitch(scope: CoroutineScope) {
        if (_state.value.isResumingSwitch) return
        scope.launch {
            _state.update {
                it.copy(
                    isResumingSwitch = true,
                    switchInProgress = true,
                    switchFailure = null,
                    switchProgressLabel = context.getString(R.string.settings_accounts_delegate_progress_resuming)
                )
            }
            val result = switchCoordinator.resumeIfInterrupted()
            _state.update { it.copy(isResumingSwitch = false) }
            clearSwitchIfIdle(result)
        }
    }

    /**
     * The coordinator publishes every terminal outcome to its progress flow, so [applyProgress]
     * already owns them. The one case it cannot see is a resume with no marker set, which returns
     * Idle without publishing anything.
     */
    private fun clearSwitchIfIdle(result: AccountSwitchProgress) {
        if (result != AccountSwitchProgress.Idle) return
        _state.update { it.copy(switchInProgress = false, switchProgressLabel = null) }
    }

    private fun applyProgress(progress: AccountSwitchProgress) {
        _state.update { current ->
            when (progress) {
                AccountSwitchProgress.Idle -> current
                AccountSwitchProgress.Preparing ->
                    current.copy(
                        switchInProgress = true,
                        switchProgressLabel = context.getString(R.string.settings_accounts_delegate_progress_preparing)
                    )
                is AccountSwitchProgress.TearingDown -> current.copy(
                    switchInProgress = true,
                    switchProgressLabel = context.getString(
                        R.string.settings_accounts_delegate_progress_archiving,
                        progress.done + 1,
                        progress.total.coerceAtLeast(1)
                    )
                )
                AccountSwitchProgress.SwappingIdentity ->
                    current.copy(
                        switchInProgress = true,
                        switchProgressLabel = context.getString(R.string.settings_accounts_delegate_progress_swapping)
                    )
                is AccountSwitchProgress.Placing -> current.copy(
                    switchInProgress = true,
                    switchProgressLabel = context.getString(
                        R.string.settings_accounts_delegate_progress_restoring,
                        progress.done + 1,
                        progress.total.coerceAtLeast(1)
                    )
                )
                AccountSwitchProgress.Finishing ->
                    current.copy(
                        switchInProgress = true,
                        switchProgressLabel = context.getString(R.string.settings_accounts_delegate_progress_finishing)
                    )
                is AccountSwitchProgress.Completed -> current.copy(
                    switchInProgress = false,
                    switchProgressLabel = null,
                    rowActionIndex = 0,
                    notice = completionNotice(progress.outcome)
                )
                is AccountSwitchProgress.Blocked -> current.copy(
                    switchInProgress = false,
                    switchProgressLabel = null,
                    switchBlocker = blockerMessage(progress.blocker)
                )
                is AccountSwitchProgress.Failed -> current.copy(
                    switchInProgress = false,
                    switchProgressLabel = null,
                    switchFailure = progress.message
                )
            }
        }
    }

    fun requestRemoval(scope: CoroutineScope, accountId: Long) {
        scope.launch {
            val pending = removalService.pendingWork(accountId)
            val isLast = _state.value.accounts.size <= 1
            _state.update {
                it.copy(
                    removalAccountId = accountId,
                    removalPendingSummary = pending?.let { work -> pendingSummary(work) },
                    removalHasPendingWork = pending?.isEmpty == false,
                    removalIsLastAccount = isLast,
                    notice = null
                )
            }
        }
    }

    fun cancelRemoval() {
        _state.update {
            it.copy(
                removalAccountId = null,
                removalPendingSummary = null,
                removalHasPendingWork = false,
                removalIsLastAccount = false
            )
        }
    }

    fun confirmRemoval(scope: CoroutineScope, policy: UnflushedQueuePolicy) {
        val accountId = _state.value.removalAccountId ?: return
        if (_state.value.isRemoving) return
        scope.launch {
            _state.update { it.copy(isRemoving = true) }
            val result = removalService.remove(accountId, policy)
            _state.update { current ->
                current.copy(
                    isRemoving = false,
                    removalAccountId = null,
                    removalPendingSummary = null,
                    removalHasPendingWork = false,
                    removalIsLastAccount = false,
                    rowActionIndex = 0,
                    notice = removalNotice(result)
                )
            }
        }
    }

    /** Opens the add-an-account form. */
    fun startSignIn() {
        signInJob?.cancel()
        signInJob = null
        _state.update { it.copy(signIn = AccountSignInState(active = true), notice = null) }
    }

    fun setSignInUsername(username: String) {
        _state.update { it.copy(signIn = it.signIn.copy(username = username, error = null)) }
    }

    fun setSignInPassword(password: String) {
        _state.update { it.copy(signIn = it.signIn.copy(password = password, error = null)) }
    }

    fun submitSignIn(scope: CoroutineScope) {
        val form = _state.value.signIn
        if (form.connecting) return

        signInJob?.cancel()
        signInJob = scope.launch {
            val baseUrl = accountRepository.activeAccount()?.baseUrl
            if (baseUrl.isNullOrBlank()) {
                failSignIn(context.getString(R.string.settings_accounts_delegate_pairing_error_no_server))
                return@launch
            }
            if (form.username.isBlank() || form.password.isBlank()) {
                failSignIn(context.getString(R.string.settings_romm_config_credentials_required))
                return@launch
            }

            _state.update { it.copy(signIn = it.signIn.copy(connecting = true, error = null)) }

            // The first account on the device becomes the live one; any later one is stored
            // alongside so the session in progress is not yanked out from under the user.
            val activate = accountRepository.accountCount() == 0
            val result = romMRepository.connectWithPassword(
                url = baseUrl,
                username = form.username,
                password = form.password,
                activate = activate
            )
            if (!currentCoroutineContext().isActive) return@launch

            when (result) {
                is SignInResult.AddedAccount -> {
                    val added = accountRepository.accounts().firstOrNull { it.id == result.accountId }
                    _state.update {
                        it.copy(
                            signIn = AccountSignInState(),
                            rowActionIndex = 0,
                            notice = context.getString(
                                R.string.settings_accounts_delegate_notice_added,
                                added?.username?.ifBlank { null }
                                    ?: context.getString(R.string.settings_accounts_delegate_fallback_account_label)
                            )
                        )
                    }
                }
                is SignInResult.Connected -> {
                    val added = accountRepository.activeAccount()
                    _state.update {
                        it.copy(
                            signIn = AccountSignInState(),
                            rowActionIndex = 0,
                            notice = added?.let { row ->
                                context.getString(
                                    R.string.settings_accounts_delegate_notice_signed_in,
                                    row.username.ifBlank {
                                        context.getString(
                                            R.string.settings_accounts_delegate_fallback_username,
                                            row.rommUserId
                                        )
                                    }
                                )
                            } ?: context.getString(R.string.settings_accounts_delegate_notice_account_added)
                        )
                    }
                }
                is SignInResult.Failed -> failSignIn(result.message)
            }
        }
    }

    /**
     * Clears the form as well as stopping the request. A sign-in the user backed out of must not
     * be able to land afterwards and move the device onto another account.
     */
    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        _state.update { it.copy(signIn = AccountSignInState()) }
    }

    private fun failSignIn(message: String) {
        _state.update {
            it.copy(signIn = it.signIn.copy(active = true, connecting = false, error = message))
        }
    }

    private suspend fun titlesFor(gameIds: List<Long>): List<String> {
        val ids = gameIds.distinct()
        if (ids.isEmpty()) return emptyList()
        return gameRepository.getByIds(ids).map { it.title }.sorted()
    }

    private fun serverLabel(baseUrl: String): String = baseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    private fun pendingSummary(work: AccountPendingWork): String? = work.describe()

    private fun removalNotice(result: AccountRemovalResult): String = when (result) {
        AccountRemovalResult.UnknownAccount ->
            context.getString(R.string.settings_accounts_delegate_removal_unknown_account)
        AccountRemovalResult.SwitchInProgress ->
            context.getString(R.string.settings_accounts_delegate_removal_switch_in_progress)
        is AccountRemovalResult.Refused -> context.getString(
            R.string.settings_accounts_delegate_removal_refused_prefix,
            pendingSummary(result.pending)
                ?: context.getString(R.string.settings_accounts_delegate_removal_refused_fallback)
        )
        is AccountRemovalResult.Removed -> buildString {
            append(context.getString(R.string.settings_accounts_delegate_removal_removed))
            if (result.reclaimedArtifacts > 0) {
                append(" ")
                append(
                    context.getString(
                        R.string.settings_accounts_delegate_removal_archived,
                        result.reclaimedArtifacts
                    )
                )
            }
            if (result.abortedArtifacts > 0) {
                append(" ")
                append(
                    context.getString(
                        R.string.settings_accounts_delegate_removal_aborted,
                        result.abortedArtifacts
                    )
                )
            }
            append(" ")
            append(context.getString(R.string.settings_accounts_delegate_removal_registered_note))
        }
    }

    private fun completionNotice(outcome: AccountSwitchOutcome): String = buildString {
        append(context.getString(R.string.settings_accounts_delegate_completion_switched))
        if (outcome.abortedArtifacts > 0 || outcome.abortedStates > 0) {
            append(" ")
            append(
                context.getString(
                    R.string.settings_accounts_delegate_completion_aborted,
                    outcome.abortedArtifacts + outcome.abortedStates
                )
            )
        }
        if (outcome.needsSyncArtifacts > 0 || outcome.needsSyncStates > 0) {
            append(" ")
            append(
                context.getString(
                    R.string.settings_accounts_delegate_completion_needs_sync,
                    outcome.needsSyncArtifacts + outcome.needsSyncStates
                )
            )
        }
    }

    private fun blockerMessage(blocker: AccountSwitchBlocker): String = when (blocker) {
        AccountSwitchBlocker.AlreadySwitching ->
            context.getString(R.string.settings_accounts_delegate_blocker_already_switching)
        AccountSwitchBlocker.ActiveSession ->
            context.getString(R.string.settings_accounts_delegate_blocker_active_session)
        is AccountSwitchBlocker.ExternalGameRecentlyForeground -> context.getString(
            R.string.settings_accounts_delegate_blocker_external_game,
            blocker.packageName,
            blocker.secondsAgo
        )
        AccountSwitchBlocker.ActiveDownloads ->
            context.getString(R.string.settings_accounts_delegate_blocker_active_downloads)
        AccountSwitchBlocker.EmulatorDownload ->
            context.getString(R.string.settings_accounts_delegate_blocker_emulator_download)
        AccountSwitchBlocker.SteamDownload ->
            context.getString(R.string.settings_accounts_delegate_blocker_steam_download)
        AccountSwitchBlocker.MediaDownload ->
            context.getString(R.string.settings_accounts_delegate_blocker_media_download)
        AccountSwitchBlocker.LibrarySyncRunning ->
            context.getString(R.string.settings_accounts_delegate_blocker_library_sync)
        AccountSwitchBlocker.NetplaySession ->
            context.getString(R.string.settings_accounts_delegate_blocker_netplay_session)
        AccountSwitchBlocker.ExitConfirmationRequired ->
            context.getString(R.string.settings_accounts_delegate_blocker_exit_confirmation)
        AccountSwitchBlocker.UnknownAccount ->
            context.getString(R.string.settings_accounts_delegate_blocker_unknown_account)
    }
}
