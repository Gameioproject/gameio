package com.nendo.argosy.ui.screens.firstrun

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.alpha
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.PermissionCard
import com.nendo.argosy.ui.filebrowser.FileBrowserMode
import com.nendo.argosy.ui.filebrowser.FileBrowserScreen
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun FirstRunScreen(
    onComplete: () -> Unit,
    viewModel: FirstRunViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoritesState by viewModel.favoritesDelegate.state.collectAsState()
    val context = LocalContext.current

    val requestStorage = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    val requestNotifications = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val requestOverlay = {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val requestUsageStats = {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val chooseFolder = { viewModel.openFolderPicker() }
    val chooseImageCacheFolder = { viewModel.openImageCachePicker() }

    var showFileBrowser by remember { mutableStateOf(false) }
    var showImageCacheBrowser by remember { mutableStateOf(false) }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onComplete) {
        viewModel.createInputHandler(
            onComplete = onComplete,
            onRequestStorage = requestStorage,
            onRequestNotifications = requestNotifications,
            onRequestOverlay = requestOverlay,
            onRequestUsageStats = requestUsageStats,
            onChooseFolder = chooseFolder,
            onChooseImageCacheFolder = chooseImageCacheFolder
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            showFileBrowser = true
            viewModel.clearFolderPickerFlag()
        }
    }

    LaunchedEffect(uiState.launchImageCachePicker) {
        if (uiState.launchImageCachePicker) {
            showImageCacheBrowser = true
            viewModel.clearImageCachePickerFlag()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAllPermissions()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = uiState.currentStep,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "wizard_step"
        ) { step ->
            when (step) {
                FirstRunStep.WELCOME -> WelcomeStep(
                    isFocused = true,
                    onGetStarted = { viewModel.nextStep() }
                )
                FirstRunStep.ROMM_LOGIN -> RommLoginStep(
                    username = uiState.rommUsername,
                    password = uiState.rommPassword,
                    isConnecting = uiState.isConnecting,
                    signUpMode = uiState.signUpMode,
                    onToggleSignUp = { viewModel.toggleSignUpMode() },
                    error = firstRunErrorText(uiState.connectionError),
                    focusedIndex = uiState.focusedIndex,
                    rommFocusField = uiState.rommFocusField,
                    onUsernameChange = { viewModel.setRommUsername(it) },
                    onPasswordChange = { viewModel.setRommPassword(it) },
                    onConnect = { viewModel.connectToRomm() },
                    onClearFocusField = { viewModel.clearRommFocusField() },
                    keyboardField = uiState.keyboardField,
                    keyboardText = viewModel.keyboardText(),
                    onKeyboardTextChange = { viewModel.onKeyboardTextChange(it) },
                    onKeyboardDismiss = { viewModel.closeKeyboard() }
                )
                FirstRunStep.ROMM_SUCCESS -> RommSuccessStep(
                    gameCount = uiState.rommGameCount,
                    platformCount = uiState.rommPlatformCount,
                    isFocused = true,
                    onContinue = { viewModel.nextStep() }
                )
                FirstRunStep.PERMISSIONS -> PermissionsStep(
                    hasStorage = uiState.hasStoragePermission,
                    hasNotifications = uiState.hasNotificationPermission,
                    hasOverlay = uiState.hasOverlayPermission,
                    hasUsageStats = uiState.hasUsageStatsPermission,
                    focusedIndex = uiState.focusedIndex,
                    onRequestStorage = requestStorage,
                    onRequestNotifications = requestNotifications,
                    onRequestOverlay = requestOverlay,
                    onRequestUsageStats = requestUsageStats,
                    onContinue = { viewModel.proceedFromPermissions() }
                )
                FirstRunStep.ADDONS -> com.nendo.argosy.ui.screens.addons.AddonsScreen(
                    onExit = viewModel::nextStep,
                    setup = true
                )
                FirstRunStep.ROM_PATH -> RomPathStep(
                    currentPath = uiState.romStoragePath,
                    folderSelected = uiState.folderSelected,
                    focusedIndex = uiState.focusedIndex,
                    onChooseFolder = chooseFolder,
                    onContinue = { viewModel.proceedFromRomPath() }
                )
                FirstRunStep.IMAGE_CACHE -> ImageCacheStep(
                    currentPath = uiState.imageCachePath,
                    folderSelected = uiState.imageCacheFolderSelected,
                    focusedIndex = uiState.focusedIndex,
                    onChooseFolder = chooseImageCacheFolder,
                    onContinue = { viewModel.proceedFromImageCache() },
                    onSkip = { viewModel.skipImageCachePath() }
                )
                FirstRunStep.PLATFORM_SELECT -> PlatformSelectStep(
                    platforms = uiState.platforms,
                    filterMode = uiState.platformFilterMode,
                    searchQuery = uiState.platformFilterSearchQuery,
                    focusedIndex = uiState.focusedIndex,
                    buttonFocusIndex = uiState.platformButtonFocus,
                    headerFocused = uiState.platformHeaderFocused,
                    headerIndex = uiState.platformHeaderIndex,
                    searchActive = uiState.platformSearchActive,
                    sortMenuOpen = uiState.platformSortMenuOpen,
                    sortMenuIndex = uiState.platformSortMenuIndex,
                    onToggle = viewModel::togglePlatform,
                    onToggleAll = viewModel::toggleAllPlatforms,
                    onSortModeChange = {
                        viewModel.setPlatformFilterSortMode(it)
                        viewModel.closePlatformSortMenu()
                    },
                    onFilterModeChange = { viewModel.cyclePlatformFilterMode() },
                    onSearchQueryChange = viewModel::setPlatformFilterSearchQuery,
                    onOpenSearch = viewModel::openPlatformSearch,
                    onCloseSearch = viewModel::closePlatformSearch,
                    onOpenSortMenu = viewModel::openPlatformSortMenu,
                    onCloseSortMenu = viewModel::closePlatformSortMenu,
                    onContinue = viewModel::proceedFromPlatformSelect
                )
                FirstRunStep.CORE_PROMPT -> CorePromptStep(
                    missingCoreCount = uiState.missingCoreCount,
                    focusedIndex = uiState.focusedIndex,
                    onDownload = { viewModel.nextStep() },
                    onSkip = { viewModel.skipCorePrompt() }
                )
                FirstRunStep.CORE_DOWNLOAD -> CoreDownloadStep(
                    coreDownloads = uiState.coreDownloads,
                    isComplete = uiState.coreDownloadComplete,
                    focusedIndex = uiState.focusedIndex,
                    onRetry = { viewModel.retryCoreDownload(it) },
                    onContinue = { viewModel.nextStep() },
                    onSkip = { viewModel.skipCoreDownloads() }
                )
                FirstRunStep.FAVORITES -> FirstRunFavoritesStep(
                    state = favoritesState,
                    onSearch = viewModel.favoritesDelegate::openSearch,
                    onQueryChange = viewModel::setFavoriteQuery,
                    onCloseSearch = viewModel.favoritesDelegate::closeSearch,
                    onToggle = viewModel::toggleSetupFavorite,
                    onPage = viewModel::loadMoreSetupFavorites,
                    onContinue = viewModel::finishFavoriteSelection
                )
                FirstRunStep.COMPLETE -> CompleteStep(
                    gameCount = uiState.rommGameCount,
                    platformCount = uiState.rommPlatformCount,
                    isFocused = true,
                    onStart = {
                        viewModel.completeSetup(onDone = onComplete)
                    }
                )
            }
        }
    }

    if (showFileBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showFileBrowser = false
                viewModel.setStoragePath(path)
            },
            onDismiss = {
                showFileBrowser = false
            }
        )
    }

    if (showImageCacheBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showImageCacheBrowser = false
                viewModel.setImageCachePath(path)
            },
            onDismiss = {
                showImageCacheBrowser = false
            }
        )
    }
}

@Composable
private fun StepColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd),
        content = content
    )
}

@Composable
private fun PermissionsStep(
    hasStorage: Boolean,
    hasNotifications: Boolean,
    hasOverlay: Boolean,
    hasUsageStats: Boolean,
    focusedIndex: Int,
    onRequestStorage: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsageStats: () -> Unit,
    onContinue: () -> Unit
) {
    StepColumn {
        StepHeader(title = stringResource(R.string.firstrun_permissions_title))
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.firstrun_permissions_intro),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.9f)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            PermissionCard(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.firstrun_permissions_storage_title),
                description = stringResource(R.string.firstrun_permissions_storage_description),
                isGranted = hasStorage,
                isFocused = focusedIndex == 0,
                onClick = onRequestStorage
            )
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.firstrun_permissions_notifications_title),
                description = stringResource(R.string.firstrun_permissions_notifications_description),
                isGranted = hasNotifications,
                isFocused = focusedIndex == 1,
                onClick = onRequestNotifications
            )
            PermissionCard(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.firstrun_permissions_overlay_title),
                description = stringResource(R.string.firstrun_permissions_overlay_description),
                isGranted = hasOverlay,
                isFocused = focusedIndex == 2,
                onClick = onRequestOverlay
            )
            PermissionCard(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.firstrun_permissions_usage_title),
                description = stringResource(R.string.firstrun_permissions_usage_description),
                isGranted = hasUsageStats,
                isFocused = focusedIndex == 3,
                onClick = onRequestUsageStats
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        FocusableButton(
            text = stringResource(R.string.firstrun_permissions_button_continue),
            isFocused = focusedIndex == 4,
            enabled = hasStorage,
            onClick = onContinue
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = if (hasStorage) {
                stringResource(R.string.firstrun_permissions_note_optional)
            } else {
                stringResource(R.string.firstrun_permissions_note_storage_required)
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = if (hasStorage) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun RomPathStep(
    currentPath: String?,
    folderSelected: Boolean,
    focusedIndex: Int,
    onChooseFolder: () -> Unit,
    onContinue: () -> Unit
) {
    StepColumn {
        StepHeader(title = stringResource(R.string.firstrun_rom_path_title))
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = stringResource(R.string.firstrun_rom_path_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.9f)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        if (folderSelected && currentPath != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(Dimens.spacingMd)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingMd))
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            FocusableButton(
                text = stringResource(R.string.firstrun_rom_path_button_continue),
                isFocused = focusedIndex == 0,
                onClick = onContinue
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            FocusableOutlinedButton(
                text = stringResource(R.string.firstrun_rom_path_button_change_folder),
                isFocused = focusedIndex == 1,
                onClick = onChooseFolder
            )
        } else {
            FocusableButton(
                text = stringResource(R.string.firstrun_rom_path_button_choose_folder),
                isFocused = focusedIndex == 0,
                icon = Icons.Default.Folder,
                onClick = onChooseFolder
            )
        }
    }
}

/**
 * On-disk name of the folder the app creates inside the chosen image cache location.
 * It is a real directory name, so it is passed into the note as a format argument
 * instead of being written into translatable text.
 */
private const val IMAGE_CACHE_FOLDER_NAME = "argosy_images"

@Composable
private fun ImageCacheStep(
    currentPath: String?,
    folderSelected: Boolean,
    focusedIndex: Int,
    onChooseFolder: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    StepColumn {
        Text(
            text = stringResource(R.string.firstrun_image_cache_optional_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.firstrun_image_cache_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = stringResource(R.string.firstrun_image_cache_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        if (folderSelected && currentPath != null) {
            val displayPath = "${currentPath.substringAfterLast("/")}/$IMAGE_CACHE_FOLDER_NAME"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(Dimens.spacingMd)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingMd))
                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            FocusableButton(
                text = stringResource(R.string.firstrun_image_cache_button_continue),
                isFocused = focusedIndex == 0,
                onClick = onContinue
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            FocusableOutlinedButton(
                text = stringResource(R.string.firstrun_image_cache_button_change_folder),
                isFocused = focusedIndex == 1,
                onClick = onChooseFolder
            )
        } else {
            FocusableButton(
                text = stringResource(R.string.firstrun_image_cache_button_choose_folder),
                isFocused = focusedIndex == 0,
                icon = Icons.Default.Folder,
                onClick = onChooseFolder
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            FocusableOutlinedButton(
                text = stringResource(R.string.firstrun_image_cache_button_use_default),
                isFocused = focusedIndex == 1,
                onClick = onSkip
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.firstrun_image_cache_folder_note, IMAGE_CACHE_FOLDER_NAME),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CorePromptStep(
    missingCoreCount: Int,
    focusedIndex: Int,
    onDownload: () -> Unit,
    onSkip: () -> Unit
) {
    StepColumn {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = stringResource(R.string.firstrun_core_prompt_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        if (missingCoreCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.firstrun_core_prompt_available_count,
                    missingCoreCount,
                    missingCoreCount
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.firstrun_core_prompt_explainer),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.firstrun_core_prompt_all_installed),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        if (missingCoreCount > 0) {
            FocusableButton(
                text = stringResource(R.string.firstrun_core_prompt_button_download),
                isFocused = focusedIndex == 0,
                icon = Icons.Default.Download,
                onClick = onDownload
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
        }

        FocusableOutlinedButton(
            text = if (missingCoreCount > 0) {
                stringResource(R.string.firstrun_core_prompt_button_skip)
            } else {
                stringResource(R.string.firstrun_core_prompt_button_continue)
            },
            isFocused = if (missingCoreCount > 0) focusedIndex == 1 else focusedIndex == 0,
            onClick = onSkip
        )

        if (missingCoreCount > 0) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.firstrun_core_prompt_note_skip),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoreDownloadStep(
    coreDownloads: List<CoreDownloadState>,
    isComplete: Boolean,
    focusedIndex: Int,
    onRetry: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val completeCount = coreDownloads.count { it.status == CoreDownloadStatus.COMPLETE }
    val failedCount = coreDownloads.count { it.status == CoreDownloadStatus.FAILED }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd)
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = stringResource(R.string.firstrun_core_download_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        if (coreDownloads.isEmpty()) {
            Text(
                text = stringResource(R.string.firstrun_core_download_none_needed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            FocusableButton(
                text = stringResource(R.string.firstrun_core_download_button_continue_no_cores),
                isFocused = focusedIndex == 0,
                onClick = onContinue
            )
        } else {
            Text(
                text = if (isComplete) {
                    if (failedCount > 0) {
                        pluralStringResource(
                            R.plurals.firstrun_core_download_partial_count,
                            coreDownloads.size,
                            completeCount,
                            coreDownloads.size
                        )
                    } else {
                        stringResource(R.string.firstrun_core_download_all_done)
                    }
                } else {
                    pluralStringResource(
                        R.plurals.firstrun_core_download_progress_count,
                        coreDownloads.size,
                        completeCount,
                        coreDownloads.size
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(coreDownloads, key = { _, c -> c.coreId }) { _, core ->
                    CoreDownloadItem(
                        core = core,
                        onRetry = { onRetry(core.coreId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            Text(
                text = stringResource(R.string.firstrun_core_download_note_skip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                FocusableOutlinedButton(
                    text = stringResource(R.string.firstrun_core_download_button_skip),
                    isFocused = focusedIndex == 1,
                    onClick = onSkip
                )
                Spacer(modifier = Modifier.weight(1f))
                FocusableButton(
                    text = stringResource(R.string.firstrun_core_download_button_continue),
                    isFocused = focusedIndex == 0,
                    enabled = isComplete,
                    onClick = onContinue
                )
            }
        }
    }
}

@Composable
private fun CoreDownloadItem(
    core: CoreDownloadState,
    onRetry: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = core.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = core.platforms.joinToString(", ") { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (core.status == CoreDownloadStatus.FAILED && core.errorMessage != null) {
                Text(
                    text = core.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        when (core.status) {
            CoreDownloadStatus.PENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CoreDownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            CoreDownloadStatus.COMPLETE -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.firstrun_core_download_status_complete_description),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            CoreDownloadStatus.FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = stringResource(R.string.firstrun_core_download_status_failed_description),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    ActionButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.firstrun_core_download_retry_description),
                            modifier = Modifier.size(Dimens.iconXs)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingXs))
                        Text(
                            text = stringResource(R.string.firstrun_core_download_button_retry),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompleteStep(
    gameCount: Int,
    platformCount: Int,
    isFocused: Boolean,
    onStart: () -> Unit
) {
    StepColumn {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )
        Text(
            text = stringResource(R.string.firstrun_complete_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = stringResource(
                R.string.firstrun_complete_library,
                pluralStringResource(R.plurals.firstrun_complete_game_count, gameCount, gameCount),
                pluralStringResource(R.plurals.firstrun_complete_platform_count, platformCount, platformCount)
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.firstrun_complete_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        FocusableButton(
            text = stringResource(R.string.firstrun_complete_button_start),
            isFocused = isFocused,
            onClick = onStart
        )
    }
}

@Composable
private fun firstRunErrorText(error: FirstRunError?): String? {
    if (error == null) return null
    val textRes = error.textRes ?: return error.serverMessage
    val arg = error.arg
    return if (arg != null) stringResource(textRes, arg) else stringResource(textRes)
}

@Composable
private fun StepHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun FocusableButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    SetupPrimaryButton(text, isFocused, onClick, enabled, icon)
}

@Composable
private fun FocusableOutlinedButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    SetupSecondaryButton(text, isFocused, onClick, enabled)
}
