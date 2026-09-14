package com.nendo.argosy.ui.screens.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ConsoleKeyboardOverlay
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FirstRunFavoritesStep(
    state: FirstRunFavoritesState,
    onSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggle: (Long) -> Unit,
    onPage: () -> Unit,
    onContinue: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.focusedIndex) {
        if (state.focusedIndex >= FAVORITES_HEADER_COUNT) {
            listState.animateScrollToItem(state.focusedIndex - FAVORITES_HEADER_COUNT)
        }
    }
    SetupBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                stringResource(R.string.firstrun_favorites_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.firstrun_favorites_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                SetupPrimaryButton(
                    text = stringResource(R.string.firstrun_favorites_search),
                    isFocused = state.focusedIndex == 0 && !state.keyboardOpen,
                    onClick = onSearch,
                    icon = Icons.Default.Search
                )
                SetupSecondaryButton(
                    text = stringResource(R.string.firstrun_favorites_continue),
                    isFocused = state.focusedIndex == 1 && !state.keyboardOpen,
                    onClick = onContinue,
                    enabled = state.savingId == null
                )
                SetupSecondaryButton(
                    text = stringResource(R.string.firstrun_favorites_skip),
                    isFocused = state.focusedIndex == 2 && !state.keyboardOpen,
                    onClick = onContinue,
                    enabled = state.savingId == null
                )
            }
            Text(
                pluralStringResource(R.plurals.firstrun_favorites_selected, state.selectedIds.size, state.selectedIds.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.query.isNotBlank()) {
                Text(
                    stringResource(R.string.firstrun_favorites_results, state.query),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.saveFailed) SetupError(stringResource(R.string.firstrun_favorites_save_error))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(state.games, key = { _, game -> game.id }) { index, game ->
                    FavoriteGameRow(
                        title = game.title,
                        platform = state.platformNames[game.platformId] ?: game.platformSlug,
                        cover = game.coverPath,
                        selected = game.id in state.selectedIds,
                        focused = !state.keyboardOpen && state.focusedIndex == FAVORITES_HEADER_COUNT + index,
                        saving = state.savingId == game.id,
                        enabled = state.savingId == null,
                        onClick = { onToggle(game.id) }
                    )
                }
                if (state.hasPageAction) {
                    item(key = "page") {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                            if (state.loadFailed) SetupError(stringResource(R.string.firstrun_favorites_load_error))
                            SetupSecondaryButton(
                                text = stringResource(if (state.loadFailed) R.string.firstrun_favorites_retry else R.string.firstrun_favorites_more),
                                isFocused = !state.keyboardOpen && state.focusedIndex == FAVORITES_HEADER_COUNT + state.games.size,
                                onClick = onPage,
                                enabled = !state.isLoading
                            )
                        }
                    }
                }
                if (state.isLoading) {
                    item(key = "loading") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            CircularProgressIndicator(Modifier.size(Dimens.iconMd), strokeWidth = Dimens.borderMedium)
                            Text(
                                stringResource(R.string.firstrun_favorites_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (!state.loadFailed && state.games.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.firstrun_favorites_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (state.keyboardOpen) {
            ConsoleKeyboardOverlay(
                query = state.query,
                onQueryChange = onQueryChange,
                onDismiss = onCloseSearch,
                placeholder = stringResource(R.string.firstrun_favorites_search_placeholder)
            )
        }
    }
}

@Composable
private fun FavoriteGameRow(
    title: String,
    platform: String,
    cover: String?,
    selected: Boolean,
    focused: Boolean,
    saving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) colors.surfaceVariant else colors.surface, shape)
            .argosyFocusIndicators(focused, FocusIndicators.Ring, shape = shape, ringColor = colors.secondary)
            .semantics { this.selected = selected }
            .clickableNoFocus(enabled, onClick)
            .padding(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier.size(Dimens.searchResultArtwork).clip(RoundedCornerShape(Dimens.radiusSm))
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text(title.take(1), color = colors.onSurfaceVariant)
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(platform, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (saving) {
            CircularProgressIndicator(Modifier.size(Dimens.iconMd), strokeWidth = Dimens.borderMedium)
        } else {
            Icon(
                if (selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(if (selected) R.string.firstrun_favorites_remove else R.string.firstrun_favorites_add),
                tint = if (selected) colors.secondary else colors.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}
