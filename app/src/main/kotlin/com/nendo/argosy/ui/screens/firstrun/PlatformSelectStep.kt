package com.nendo.argosy.ui.screens.firstrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.components.PlatformFilterHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.PlatformFilterLogic

@Composable
internal fun PlatformSelectStep(
    platforms: List<PlatformEntity>,
    filterMode: PlatformFilterLogic.FilterMode,
    searchQuery: String,
    focusedIndex: Int,
    buttonFocusIndex: Int,
    headerFocused: Boolean,
    headerIndex: Int,
    searchActive: Boolean,
    sortMenuOpen: Boolean,
    sortMenuIndex: Int,
    onToggle: (Long) -> Unit,
    onToggleAll: () -> Unit,
    onSortModeChange: (PlatformFilterLogic.SortMode) -> Unit,
    onFilterModeChange: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSortMenu: () -> Unit,
    onCloseSortMenu: () -> Unit,
    onContinue: () -> Unit
) {
    val listState = rememberLazyListState()
    val enabledCount = platforms.count { it.syncEnabled }
    val allEnabled = platforms.isNotEmpty() && enabledCount == platforms.size
    val isOnButtons = !headerFocused && focusedIndex >= platforms.size

    LaunchedEffect(focusedIndex, headerFocused) {
        if (!headerFocused && platforms.isNotEmpty() && focusedIndex in platforms.indices) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingMd)
    ) {
        Text(
            text = stringResource(R.string.firstrun_platform_select_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.firstrun_platform_select_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = pluralStringResource(
                R.plurals.firstrun_platform_select_selected_count,
                platforms.size,
                enabledCount,
                platforms.size
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        PlatformFilterHeader(
            platformCount = platforms.size,
            filterMode = filterMode,
            searchQuery = searchQuery,
            headerFocused = headerFocused,
            headerIndex = headerIndex,
            searchActive = searchActive,
            sortMenuOpen = sortMenuOpen,
            sortMenuIndex = sortMenuIndex,
            onSearchQueryChange = onSearchQueryChange,
            onSortModeChange = onSortModeChange,
            onFilterModeChange = onFilterModeChange,
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            onOpenSortMenu = onOpenSortMenu,
            onCloseSortMenu = onCloseSortMenu,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            if (platforms.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.firstrun_platform_select_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimens.spacingLg)
                    )
                }
            }
            itemsIndexed(platforms, key = { _, p -> p.id }) { index, platform ->
                val isFocused = !headerFocused && index == focusedIndex
                SetupSystemRow(
                    platform = platform,
                    isFocused = isFocused,
                    onToggle = { onToggle(platform.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            SetupSecondaryButton(
                text = if (allEnabled) {
                    stringResource(R.string.firstrun_platform_select_button_deselect_all)
                } else {
                    stringResource(R.string.firstrun_platform_select_button_select_all)
                },
                isFocused = isOnButtons && buttonFocusIndex == 0,
                onClick = onToggleAll
            )
            Spacer(modifier = Modifier.weight(1f))
            SetupPrimaryButton(
                text = stringResource(R.string.firstrun_platform_select_button_continue),
                isFocused = isOnButtons && buttonFocusIndex == 1,
                onClick = onContinue
            )
        }
    }
}
