package com.nendo.argosy.ui.screens.firstrun

import android.Manifest
import android.os.Build
import android.os.Environment
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.nendo.argosy.ui.common.messageRes
import com.nendo.argosy.R
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.SignInResult
import com.nendo.argosy.data.remote.romm.DEFAULT_SERVER_URL
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.formatCoreDownloadError
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.components.PLATFORM_HEADER_COUNT
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SEARCH
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SORT
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.PlatformFilterLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FirstRunStep {
    WELCOME,
    ROMM_LOGIN,
    ROMM_SUCCESS,
    PERMISSIONS,
    ADDONS,
    ROM_PATH,
    IMAGE_CACHE,
    PLATFORM_SELECT,
    CORE_PROMPT,
    CORE_DOWNLOAD,
    FAVORITES,
    COMPLETE
}

data class CoreDownloadState(
    val coreId: String,
    val displayName: String,
    val platforms: Set<String>,
    val status: CoreDownloadStatus = CoreDownloadStatus.PENDING,
    val errorMessage: String? = null
)

enum class CoreDownloadStatus {
    PENDING, DOWNLOADING, COMPLETE, FAILED
}

/**
 * A connection failure to show on the login steps. Either app-authored text, held as
 * [textRes] plus an optional [arg] so the render site resolves it in the user's locale,
 * or [serverMessage], which the server supplied and the app cannot translate.
 */
data class FirstRunError(
    @StringRes val textRes: Int? = null,
    val arg: String? = null,
    val serverMessage: String? = null
)

data class FirstRunUiState(
    val currentStep: FirstRunStep = FirstRunStep.WELCOME,
    val focusedIndex: Int = 0,
    val rommUsername: String = "",
    val rommPassword: String = "",
    val isConnecting: Boolean = false,
    val connectionError: FirstRunError? = null,
    val rommGameCount: Int = 0,
    val rommPlatformCount: Int = 0,
    val romStoragePath: String? = null,
    val folderSelected: Boolean = false,
    val launchFolderPicker: Boolean = false,
    val imageCachePath: String? = null,
    val imageCacheFolderSelected: Boolean = false,
    val launchImageCachePicker: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val rommFocusField: Int? = null,
    val keyboardField: Int? = null,
    val platforms: List<PlatformEntity> = emptyList(),
    val platformsAll: List<PlatformEntity> = emptyList(),
    val platformFilterSortMode: PlatformFilterLogic.SortMode = PlatformFilterLogic.SortMode.DEFAULT,
    val platformFilterMode: PlatformFilterLogic.FilterMode = PlatformFilterLogic.FilterMode.ALL,
    val platformFilterSearchQuery: String = "",
    val platformHeaderFocused: Boolean = false,
    val platformHeaderIndex: Int = 0,
    val platformSearchActive: Boolean = false,
    val platformSortMenuOpen: Boolean = false,
    val platformSortMenuIndex: Int = 0,
    val platformButtonFocus: Int = 1,
    val missingCoreCount: Int = 0,
    val coreDownloads: List<CoreDownloadState> = emptyList(),
    val coreDownloadComplete: Boolean = false
)

@HiltViewModel
@Suppress("TooManyFunctions")
class FirstRunViewModel @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val platformRepository: PlatformRepository,
    private val permissionHelper: PermissionHelper,
    private val coreManager: LibretroCoreManager,
    private val gamepadInputHandler: GamepadInputHandler,
    private val hapticManager: HapticFeedbackManager,
    val favoritesDelegate: FirstRunFavoritesDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FirstRunUiState(
            currentStep = FirstRunStep.ROMM_LOGIN,
            focusedIndex = 0
        )
    )
    val uiState: StateFlow<FirstRunUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.PERMISSIONS
                FirstRunStep.PERMISSIONS -> FirstRunStep.ADDONS
                FirstRunStep.ADDONS -> FirstRunStep.ROM_PATH
                FirstRunStep.ROM_PATH -> FirstRunStep.IMAGE_CACHE
                // The catalog lists every system the server knows, so following is always a choice.
                FirstRunStep.IMAGE_CACHE -> FirstRunStep.PLATFORM_SELECT
                FirstRunStep.PLATFORM_SELECT -> FirstRunStep.CORE_PROMPT
                FirstRunStep.CORE_PROMPT -> FirstRunStep.CORE_DOWNLOAD
                FirstRunStep.CORE_DOWNLOAD -> FirstRunStep.FAVORITES
                FirstRunStep.FAVORITES -> FirstRunStep.COMPLETE
                FirstRunStep.COMPLETE -> FirstRunStep.COMPLETE
            }
            val initialFocus = if (nextStep == FirstRunStep.IMAGE_CACHE) 1 else 0
            state.copy(currentStep = nextStep, focusedIndex = initialFocus)
        }
        when (_uiState.value.currentStep) {
            FirstRunStep.PLATFORM_SELECT -> loadPlatformsForSelection()
            FirstRunStep.CORE_PROMPT -> checkMissingCores()
            FirstRunStep.CORE_DOWNLOAD -> prepareCoreDownloads()
            FirstRunStep.FAVORITES -> favoritesDelegate.enter(viewModelScope)
            else -> {}
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.PERMISSIONS -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.ADDONS -> FirstRunStep.PERMISSIONS
                FirstRunStep.ROM_PATH -> FirstRunStep.ADDONS
                FirstRunStep.IMAGE_CACHE -> FirstRunStep.ROM_PATH
                FirstRunStep.PLATFORM_SELECT -> FirstRunStep.IMAGE_CACHE
                FirstRunStep.CORE_PROMPT -> FirstRunStep.PLATFORM_SELECT
                FirstRunStep.CORE_DOWNLOAD -> FirstRunStep.CORE_PROMPT
                FirstRunStep.FAVORITES -> FirstRunStep.CORE_PROMPT
                FirstRunStep.COMPLETE -> FirstRunStep.FAVORITES
            }
            val initialFocus = if (prevStep == FirstRunStep.IMAGE_CACHE) 1 else 0
            if (prevStep == state.currentStep) {
                state
            } else {
                state.copy(currentStep = prevStep, focusedIndex = initialFocus)
            }
        }
        if (_uiState.value.currentStep == FirstRunStep.FAVORITES) favoritesDelegate.enter(viewModelScope)
    }

    private fun loadPlatformsForSelection() {
        viewModelScope.launch {
            when (val result = romMRepository.fetchAndStorePlatforms(defaultSyncEnabled = false)) {
                is RomMResult.Success -> {
                    val allPlatforms = result.data
                    val filtered = PlatformFilterLogic.filterAndSortPlatformEntities(
                        items = allPlatforms,
                        searchQuery = _uiState.value.platformFilterSearchQuery,
                        filterMode = _uiState.value.platformFilterMode,
                        sortMode = _uiState.value.platformFilterSortMode
                    )
                    _uiState.update { it.copy(platformsAll = allPlatforms, platforms = filtered) }
                }
                is RomMResult.Error -> {
                    val platforms = platformRepository.observeAllPlatforms().first()
                    _uiState.update { it.copy(platformsAll = platforms, platforms = platforms) }
                }
            }
        }
    }

    private fun checkMissingCores() {
        val enabledPlatforms = _uiState.value.platforms
            .filter { it.syncEnabled }
            .map { it.slug }
            .toSet()

        val missingCores = coreManager.getMissingCoresForPlatforms(enabledPlatforms)
        _uiState.update { it.copy(missingCoreCount = missingCores.size) }
    }

    fun skipCorePrompt() {
        val hadMissingCores = _uiState.value.missingCoreCount > 0
        _uiState.update { state ->
            state.copy(currentStep = FirstRunStep.FAVORITES, focusedIndex = 0)
        }
        favoritesDelegate.enter(viewModelScope)
        if (hadMissingCores) {
            viewModelScope.launch {
                preferencesRepository.setBuiltinLibretroEnabled(false)
            }
        }
    }

    private fun prepareCoreDownloads() {
        val enabledPlatforms = _uiState.value.platforms
            .filter { it.syncEnabled }
            .map { it.slug }
            .toSet()

        val missingCores = coreManager.getMissingCoresForPlatforms(enabledPlatforms)
        val coreDownloads = missingCores.map { core ->
            CoreDownloadState(
                coreId = core.coreId,
                displayName = core.displayName,
                platforms = core.platforms
            )
        }

        _uiState.update {
            it.copy(
                coreDownloads = coreDownloads,
                coreDownloadComplete = coreDownloads.isEmpty()
            )
        }

        if (coreDownloads.isNotEmpty()) {
            startCoreDownloads()
        }
    }

    private fun startCoreDownloads() {
        viewModelScope.launch {
            val cores = _uiState.value.coreDownloads.filter { it.status == CoreDownloadStatus.PENDING }
            for (core in cores) {
                _uiState.update { state ->
                    state.copy(
                        coreDownloads = state.coreDownloads.map {
                            if (it.coreId == core.coreId) it.copy(status = CoreDownloadStatus.DOWNLOADING)
                            else it
                        }
                    )
                }

                val result = coreManager.downloadCoreById(core.coreId)
                val newStatus = if (result.isSuccess) CoreDownloadStatus.COMPLETE else CoreDownloadStatus.FAILED
                val errorMessage = result.exceptionOrNull()?.let { formatCoreDownloadError(it) }

                _uiState.update { state ->
                    val updatedCores = state.coreDownloads.map {
                        if (it.coreId == core.coreId) it.copy(status = newStatus, errorMessage = errorMessage)
                        else it
                    }
                    val allComplete = updatedCores.all { it.status == CoreDownloadStatus.COMPLETE || it.status == CoreDownloadStatus.FAILED }
                    if (allComplete) gamepadInputHandler.blockInputFor(300)
                    state.copy(
                        coreDownloads = updatedCores,
                        coreDownloadComplete = allComplete
                    )
                }
            }
        }
    }

    fun retryCoreDownload(coreId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    coreDownloads = state.coreDownloads.map {
                        if (it.coreId == coreId) it.copy(status = CoreDownloadStatus.DOWNLOADING, errorMessage = null)
                        else it
                    },
                    coreDownloadComplete = false
                )
            }

            val result = coreManager.downloadCoreById(coreId)
            val newStatus = if (result.isSuccess) CoreDownloadStatus.COMPLETE else CoreDownloadStatus.FAILED
            val errorMessage = result.exceptionOrNull()?.let { formatCoreDownloadError(it) }

            _uiState.update { state ->
                val updatedCores = state.coreDownloads.map {
                    if (it.coreId == coreId) it.copy(status = newStatus, errorMessage = errorMessage)
                    else it
                }
                val allComplete = updatedCores.all { it.status == CoreDownloadStatus.COMPLETE || it.status == CoreDownloadStatus.FAILED }
                if (allComplete) gamepadInputHandler.blockInputFor(300)
                state.copy(
                    coreDownloads = updatedCores,
                    coreDownloadComplete = allComplete
                )
            }
        }
    }

    fun skipCoreDownloads() {
        nextStep()
    }

    fun togglePlatform(platformId: Long) {
        viewModelScope.launch {
            val currentAll = _uiState.value.platformsAll
            val platformIndex = currentAll.indexOfFirst { it.id == platformId }
            if (platformIndex == -1) return@launch

            val platform = currentAll[platformIndex]
            val newEnabled = !platform.syncEnabled
            platformRepository.updateSyncEnabled(platformId, newEnabled)

            val updatedAll = currentAll.toMutableList()
            updatedAll[platformIndex] = platform.copy(syncEnabled = newEnabled)

            _uiState.update { it.copy(platformsAll = updatedAll) }
            applyPlatformFilters()
        }
    }

    fun toggleAllPlatforms() {
        viewModelScope.launch {
            val currentAll = _uiState.value.platformsAll
            val allEnabled = currentAll.all { it.syncEnabled }
            val newState = !allEnabled

            currentAll.forEach { platform ->
                platformRepository.updateSyncEnabled(platform.id, newState)
            }

            val updatedAll = currentAll.map { it.copy(syncEnabled = newState) }

            _uiState.update { it.copy(platformsAll = updatedAll) }
            applyPlatformFilters()
        }
    }

    fun setPlatformFilterSortMode(mode: PlatformFilterLogic.SortMode) {
        _uiState.update { it.copy(platformFilterSortMode = mode) }
        applyPlatformFilters(resetFocus = true)
    }

    fun cyclePlatformFilterMode() {
        _uiState.update {
            val nextMode = when (it.platformFilterMode) {
                PlatformFilterLogic.FilterMode.ALL -> PlatformFilterLogic.FilterMode.HAS_GAMES
                PlatformFilterLogic.FilterMode.HAS_GAMES -> PlatformFilterLogic.FilterMode.ENABLED
                PlatformFilterLogic.FilterMode.ENABLED -> PlatformFilterLogic.FilterMode.ALL
            }
            it.copy(platformFilterMode = nextMode)
        }
        applyPlatformFilters(resetFocus = true)
    }

    fun setPlatformFilterSearchQuery(query: String) {
        _uiState.update { it.copy(platformFilterSearchQuery = query) }
        applyPlatformFilters(resetFocus = true)
    }

    fun openPlatformSearch() = _uiState.update {
        it.copy(platformHeaderFocused = true, platformHeaderIndex = PLATFORM_HEADER_SEARCH, platformSearchActive = true)
    }

    fun closePlatformSearch() = _uiState.update { it.copy(platformSearchActive = false) }

    fun openPlatformSortMenu() = _uiState.update {
        it.copy(
            platformHeaderFocused = true,
            platformHeaderIndex = PLATFORM_HEADER_SORT,
            platformSortMenuOpen = true,
            platformSortMenuIndex = PlatformFilterLogic.SortMode.entries.indexOf(it.platformFilterSortMode).coerceAtLeast(0)
        )
    }

    fun closePlatformSortMenu() = _uiState.update { it.copy(platformSortMenuOpen = false) }

    private fun applyPlatformFilters(resetFocus: Boolean = false) {
        val state = _uiState.value
        val allPlatforms = state.platformsAll

        val filtered = PlatformFilterLogic.filterAndSortPlatformEntities(
            items = allPlatforms,
            searchQuery = state.platformFilterSearchQuery,
            filterMode = state.platformFilterMode,
            sortMode = state.platformFilterSortMode
        )

        _uiState.update {
            it.copy(
                platforms = filtered,
                focusedIndex = if (resetFocus) 0 else it.focusedIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun getMaxFocusIndex(): Int {
        val state = _uiState.value
        return when (state.currentStep) {
            FirstRunStep.WELCOME -> 0
            FirstRunStep.ROMM_LOGIN -> 2
            FirstRunStep.ROMM_SUCCESS -> 0
            FirstRunStep.PERMISSIONS -> 4
            FirstRunStep.ADDONS -> 0
            FirstRunStep.ROM_PATH -> if (state.folderSelected) 1 else 0
            FirstRunStep.IMAGE_CACHE -> 1
            FirstRunStep.PLATFORM_SELECT -> state.platforms.size
            FirstRunStep.CORE_PROMPT -> 1
            FirstRunStep.CORE_DOWNLOAD -> 1
            FirstRunStep.FAVORITES -> 0
            FirstRunStep.COMPLETE -> 0
        }
    }

    fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.currentStep == FirstRunStep.FAVORITES) {
            favoritesDelegate.moveVertical(delta)
            return true
        }
        if (state.currentStep == FirstRunStep.PLATFORM_SELECT) {
            when {
                state.platformSortMenuOpen -> {
                    _uiState.update {
                        it.copy(platformSortMenuIndex = (it.platformSortMenuIndex + delta).mod(PlatformFilterLogic.SortMode.entries.size))
                    }
                    return true
                }
                state.platformSearchActive -> return true
                state.platformHeaderFocused -> {
                    if (delta > 0) _uiState.update { it.copy(platformHeaderFocused = false, focusedIndex = 0) }
                    return true
                }
                delta < 0 && state.focusedIndex == 0 -> {
                    _uiState.update { it.copy(platformHeaderFocused = true) }
                    return true
                }
            }
        }
        val maxIndex = getMaxFocusIndex()
        val newIndex = (state.focusedIndex + delta).coerceIn(0, maxIndex)
        if (newIndex == state.focusedIndex) {
            if (delta != 0) hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return false
        }
        _uiState.update { it.copy(focusedIndex = newIndex) }
        return true
    }

    fun setFocusedIndex(index: Int) {
        _uiState.update { it.copy(focusedIndex = index) }
    }

    fun moveButtonFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.currentStep == FirstRunStep.FAVORITES) {
            favoritesDelegate.moveHorizontal(delta)
            return true
        }
        if (state.currentStep != FirstRunStep.PLATFORM_SELECT) return false
        if (state.platformHeaderFocused && !state.platformSortMenuOpen && !state.platformSearchActive) {
            _uiState.update { it.copy(platformHeaderIndex = (it.platformHeaderIndex + delta).mod(PLATFORM_HEADER_COUNT)) }
            return true
        }
        if (state.focusedIndex < state.platforms.size) return false
        val newIndex = (state.platformButtonFocus + delta).coerceIn(0, 1)
        if (newIndex == state.platformButtonFocus) {
            if (delta != 0) hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return false
        }
        _uiState.update { it.copy(platformButtonFocus = newIndex) }
        return true
    }

    fun setRommFocusField(index: Int) {
        _uiState.update { it.copy(rommFocusField = index) }
    }

    fun clearRommFocusField() {
        _uiState.update { it.copy(rommFocusField = null) }
    }

    fun setRommUsername(username: String) {
        _uiState.update { it.copy(rommUsername = username, connectionError = null) }
    }

    fun setRommPassword(password: String) {
        _uiState.update { it.copy(rommPassword = password, connectionError = null) }
    }

    fun openKeyboard(field: Int) {
        _uiState.update { it.copy(keyboardField = field, connectionError = null) }
    }

    fun closeKeyboard() {
        _uiState.update { it.copy(keyboardField = null) }
    }

    /** Text for the field the console keyboard is editing. */
    fun keyboardText(): String {
        val state = _uiState.value
        return when (state.keyboardField) {
            0 -> state.rommUsername
            1 -> state.rommPassword
            else -> ""
        }
    }

    fun onKeyboardTextChange(value: String) {
        when (_uiState.value.keyboardField) {
            0 -> setRommUsername(value)
            1 -> setRommPassword(value)
        }
    }

    fun connectToRomm() {
        val state = _uiState.value
        if (state.isConnecting) return
        if (state.rommUsername.isBlank() || state.rommPassword.isBlank()) {
            _uiState.update {
                it.copy(connectionError = FirstRunError(textRes = R.string.firstrun_romm_credentials_required))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            val result = romMRepository.connectWithPassword(
                url = DEFAULT_SERVER_URL,
                username = state.rommUsername,
                password = state.rommPassword
            )
            when (result) {
                is SignInResult.Connected, is SignInResult.AddedAccount -> {
                    // The password existed only to mint the token; it does not outlive this call.
                    _uiState.update { it.copy(rommPassword = "") }
                    onAuthSuccess()
                }
                is SignInResult.Failed -> _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = FirstRunError(textRes = result.messageRes)
                    )
                }
            }
        }
    }

    private suspend fun onAuthSuccess() {
        when (val summary = romMRepository.getLibrarySummary()) {
            is RomMResult.Success -> {
                val (platformCount, gameCount) = summary.data
                gamepadInputHandler.blockInputFor(300)
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        currentStep = FirstRunStep.ROMM_SUCCESS,
                        focusedIndex = 0,
                        rommGameCount = gameCount,
                        rommPlatformCount = platformCount
                    )
                }
            }
            is RomMResult.Error -> {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = FirstRunError(
                            textRes = R.string.firstrun_romm_success_error_library_fetch,
                            arg = summary.message
                        )
                    )
                }
            }
        }
    }

    fun openFolderPicker() {
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun clearFolderPickerFlag() {
        _uiState.update { it.copy(launchFolderPicker = false) }
    }

    fun setStoragePath(path: String) {
        _uiState.update {
            it.copy(
                romStoragePath = path,
                folderSelected = true
            )
        }
    }

    fun proceedFromRomPath() {
        val state = _uiState.value
        if (state.folderSelected) {
            nextStep()
        }
    }

    fun openImageCachePicker() {
        _uiState.update { it.copy(launchImageCachePicker = true) }
    }

    fun clearImageCachePickerFlag() {
        _uiState.update { it.copy(launchImageCachePicker = false) }
    }

    fun setImageCachePath(path: String) {
        _uiState.update {
            it.copy(
                imageCachePath = path,
                imageCacheFolderSelected = true
            )
        }
    }

    fun skipImageCachePath() {
        _uiState.update { it.copy(imageCacheFolderSelected = false, imageCachePath = null) }
        nextStep()
    }

    fun proceedFromImageCache() {
        nextStep()
    }

    fun refreshAllPermissions() {
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasOverlay = Settings.canDrawOverlays(application)
        val hasUsageStats = permissionHelper.hasUsageStatsPermission(application)

        _uiState.update {
            it.copy(
                hasStoragePermission = hasStorage,
                hasNotificationPermission = hasNotification,
                hasOverlayPermission = hasOverlay,
                hasUsageStatsPermission = hasUsageStats
            )
        }
    }

    fun proceedFromPermissions() {
        if (!_uiState.value.hasStoragePermission) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return
        }
        nextStep()
    }

    fun completeSetup(onDone: () -> Unit = {}) {
        val state = _uiState.value

        viewModelScope.launch {
            if (state.folderSelected) {
                state.romStoragePath?.let { path ->
                    preferencesRepository.setRomStoragePath(path)
                }
                if (state.imageCacheFolderSelected) {
                    state.imageCachePath?.let { path ->
                        preferencesRepository.setImageCachePath(path)
                    }
                }
            }
            preferencesRepository.setSaveSyncEnabled(true)
            preferencesRepository.setFirstRunComplete()
            onDone()
        }
    }

    fun handleConfirm(
        onRequestStorage: () -> Unit,
        onRequestNotifications: () -> Unit,
        onRequestOverlay: () -> Unit,
        onRequestUsageStats: () -> Unit,
        onChooseFolder: () -> Unit,
        onChooseImageCacheFolder: () -> Unit
    ) {
        val state = _uiState.value
        when (state.currentStep) {
            FirstRunStep.WELCOME -> nextStep()
            FirstRunStep.ROMM_LOGIN -> {
                when (state.focusedIndex) {
                    0, 1 -> openKeyboard(state.focusedIndex)
                    2 -> if (!state.isConnecting && canConnect(state)) connectToRomm()
                }
            }
            FirstRunStep.ROMM_SUCCESS -> nextStep()
            FirstRunStep.PERMISSIONS -> {
                when (state.focusedIndex) {
                    0 -> if (!state.hasStoragePermission) onRequestStorage()
                    1 -> if (!state.hasNotificationPermission) onRequestNotifications()
                    2 -> if (!state.hasOverlayPermission) onRequestOverlay()
                    3 -> if (!state.hasUsageStatsPermission) onRequestUsageStats()
                    4 -> proceedFromPermissions()
                }
            }
            FirstRunStep.ROM_PATH -> {
                if (state.folderSelected) {
                    if (state.focusedIndex == 0) proceedFromRomPath()
                    else onChooseFolder()
                } else {
                    onChooseFolder()
                }
            }
            FirstRunStep.IMAGE_CACHE -> {
                if (state.imageCacheFolderSelected) {
                    if (state.focusedIndex == 0) proceedFromImageCache()
                    else onChooseImageCacheFolder()
                } else {
                    if (state.focusedIndex == 0) onChooseImageCacheFolder()
                    else skipImageCachePath()
                }
            }
            FirstRunStep.PLATFORM_SELECT -> when {
                state.platformSortMenuOpen -> {
                    val mode = PlatformFilterLogic.SortMode.entries[state.platformSortMenuIndex]
                    _uiState.update { it.copy(platformSortMenuOpen = false) }
                    setPlatformFilterSortMode(mode)
                }
                state.platformSearchActive -> _uiState.update { it.copy(platformSearchActive = false) }
                state.platformHeaderFocused -> when (state.platformHeaderIndex) {
                    PLATFORM_HEADER_SEARCH -> _uiState.update { it.copy(platformSearchActive = true) }
                    PLATFORM_HEADER_SORT -> _uiState.update {
                        it.copy(
                            platformSortMenuOpen = true,
                            platformSortMenuIndex = PlatformFilterLogic.SortMode.entries
                                .indexOf(it.platformFilterSortMode).coerceAtLeast(0)
                        )
                    }
                    else -> cyclePlatformFilterMode()
                }
                state.focusedIndex >= state.platforms.size -> {
                    if (state.platformButtonFocus == 0) toggleAllPlatforms()
                    else proceedFromPlatformSelect()
                }
                else -> {
                    val platform = state.platforms.getOrNull(state.focusedIndex)
                    if (platform != null) togglePlatform(platform.id)
                }
            }
            FirstRunStep.CORE_PROMPT -> {
                if (state.focusedIndex == 0) nextStep()
                else skipCorePrompt()
            }
            FirstRunStep.CORE_DOWNLOAD -> {
                if (state.focusedIndex == 0 && state.coreDownloadComplete) nextStep()
                else if (state.focusedIndex == 1) skipCoreDownloads()
            }
            FirstRunStep.ADDONS -> {}
            FirstRunStep.FAVORITES -> favoritesDelegate.confirm(viewModelScope, ::nextStep)
            FirstRunStep.COMPLETE -> {}
        }
    }

    fun setFavoriteQuery(query: String) = favoritesDelegate.setQuery(query, viewModelScope)
    fun toggleSetupFavorite(gameId: Long) = favoritesDelegate.toggle(gameId, viewModelScope)
    fun loadMoreSetupFavorites() = favoritesDelegate.loadMore(viewModelScope)
    fun finishFavoriteSelection() = favoritesDelegate.leave(::nextStep)

    private fun canConnect(state: FirstRunUiState): Boolean =
            state.rommUsername.isNotBlank() &&
            state.rommPassword.isNotBlank()

    fun proceedFromPlatformSelect() {
        nextStep()
    }

    fun createInputHandler(
        onComplete: () -> Unit,
        onRequestStorage: () -> Unit,
        onRequestNotifications: () -> Unit,
        onRequestOverlay: () -> Unit,
        onRequestUsageStats: () -> Unit,
        onChooseFolder: () -> Unit,
        onChooseImageCacheFolder: () -> Unit
    ) = FirstRunInputHandler(
        this,
        onComplete,
        onRequestStorage,
        onRequestNotifications,
        onRequestOverlay,
        onRequestUsageStats,
        onChooseFolder,
        onChooseImageCacheFolder
    )
}
