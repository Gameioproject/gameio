package com.nendo.argosy.ui.screens.home.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.FooterVariant
import com.nendo.argosy.ui.components.GameioMark
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.screens.home.*
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.DiscoveryHome as T

@Composable
fun DiscoveryHomeScreen(state: HomeUiState, viewModel: HomeViewModel, onGameSelect: (Long) -> Unit, onMenu: () -> Unit, useBackdrop: Boolean = false) {
    DiscoveryTheme {
        BoxWithConstraints(Modifier.fillMaxSize().then(if (useBackdrop) Modifier else Modifier.background(MaterialTheme.colorScheme.background))) {
            val dimensions = DiscoveryDimensions(
                (maxHeight / T.referenceHeightDp.dp).coerceIn(T.minScale, T.maxScale) * LocalUiScale.current.scale,
                bodyTextScale = MaterialTheme.typography.bodyMedium.fontSize.value /
                    com.nendo.argosy.ui.theme.generated.TypographyTokens.bodyMedium.fontSize.value,
                displayTextScale = MaterialTheme.typography.headlineMedium.fontSize.value /
                    com.nendo.argosy.ui.theme.generated.TypographyTokens.headlineMedium.fontSize.value
            )
            val sections = remember(state.platforms, state.currentRow, state.libraryFilter,
                state.discoveryData, state.favoriteGames, state.recommendedGames,
                state.explore, state.discoveryFocus.feed) { state.discoverySections }
            val vertical = rememberLazyListState()
            val zone = state.discoveryFocus.zone
            LaunchedEffect(zone, state.currentRow, state.discoveryFocus.feed) {
                val index = when {
                    zone <= DiscoveryFocus.HERO -> 0
                    else -> zone + 1
                }
                if (index < vertical.layoutInfo.totalItemsCount) vertical.scrollDiscoverySelection(index)
            }
            LaunchedEffect(state.currentRow, state.libraryFilter, state.discoveryFocus.feed,
                state.discoveryData.loading, state.explore.cursor, state.explore.loading) {
                if (state.discoveryFocus.feed == DiscoveryFeed.EXPLORE && !state.discoveryData.loading) {
                    snapshotFlow {
                        val info = vertical.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
                    }.distinctUntilChanged().collect { nearEnd ->
                        if (nearEnd) viewModel.loadMoreExplore()
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                DiscoveryHeader(state, viewModel, dimensions, onGameSelect, onMenu)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val heroDimensions = dimensions.copy(viewportHeight = maxHeight - dimensions.platformBar)
                LazyColumn(state = vertical, modifier = Modifier.fillMaxSize()) {
                    item(key = "platforms", contentType = "platforms") { DiscoveryPlatforms(state, viewModel, dimensions) }
                    item(key = "hero", contentType = "hero") { DiscoveryHero(state, viewModel, heroDimensions, onGameSelect) }
                    item(key = "feeds", contentType = "feeds") { DiscoveryFeeds(state, viewModel, dimensions) }
                    itemsIndexed(sections, key = { _, section -> section.key },
                        contentType = { _, section -> if (section.games.isEmpty()) "empty-section" else "game-section" }) { index, section ->
                        Column(Modifier.padding(horizontal = dimensions.padding).padding(bottom = dimensions.sectionGap)) {
                            Row(Modifier.height(dimensions.headingHeight).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(section.titleRes), fontSize = dimensions.sectionTitle,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(dimensions.gap))
                                Text(if (section.continuation) stringResource(R.string.explore_more_genre)
                                    else state.currentPlatform?.displayName ?: stringResource(R.string.discovery_all_platforms),
                                    fontSize = dimensions.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                section.loading && section.games.isEmpty() -> Row(
                                    Modifier.height(dimensions.emptyHeight),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(dimensions.gap)
                                ) {
                                    CircularProgressIndicator(Modifier.size(dimensions.control / 2),
                                        strokeWidth = dimensions.ring)
                                    Text(stringResource(R.string.discovery_loading), fontSize = dimensions.body,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                section.games.isEmpty() -> Column(
                                    Modifier.heightIn(min = dimensions.emptyHeight), verticalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(if (section.failed) R.string.discovery_error else section.emptyRes),
                                        fontSize = dimensions.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (section.failed) DiscoveryButton(stringResource(R.string.discovery_retry),
                                        zone == index + DiscoveryFocus.FIRST_ROW, dimensions,
                                        if (section.key == "explore-tail") viewModel::retryExplore else viewModel::retryDiscovery)
                                }
                                else -> {
                                    DiscoveryGameRail(section.games, index + DiscoveryFocus.FIRST_ROW, state,
                                        viewModel, dimensions, dimensions.rowCard)
                                    if (section.failed) Text(stringResource(R.string.discovery_offline),
                                        fontSize = dimensions.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                }
                FooterSpacer()
            }
            FooterHints(
                hints = listOf(
                    InputButton.DPAD_VERTICAL to stringResource(R.string.discovery_explore),
                    InputButton.LB_RB to stringResource(R.string.discovery_platform),
                    InputButton.LT to stringResource(R.string.discovery_search),
                    InputButton.RT to stringResource(R.string.discovery_library_only),
                    InputButton.RS to stringResource(R.string.discovery_hide_guide),
                    InputButton.Y to stringResource(R.string.discovery_favorite),
                    InputButton.X to stringResource(R.string.discovery_details),
                    InputButton.A to stringResource(
                        state.focusedGame?.let(::discoveryPrimaryLabel) ?: R.string.discovery_explore)
                ), variant = FooterVariant.SUBTLE
            )
        }
    }
}

@Composable
private fun DiscoveryHeader(
    state: HomeUiState, viewModel: HomeViewModel, d: DiscoveryDimensions, onGameSelect: (Long) -> Unit, onMenu: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(d.bar).padding(horizontal = d.padding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.gap)) {
        Row(Modifier.clickableNoFocus(onClick = onMenu), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.gap)) {
            GameioMark(Modifier.size(d.control * 0.7f))
            Text(stringResource(R.string.discovery_brand), fontSize = d.brand, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.weight(1f))
        listOf(Icons.Default.Shuffle to R.string.discovery_surprise, Icons.Default.Search to R.string.discovery_search)
            .forEachIndexed { index, (icon, label) ->
                DiscoveryButton("", state.discoveryFocus.zone == DiscoveryFocus.TOOLS && state.discoveryFocus.control == index,
                    d, onClick = {
                        viewModel.focusDiscoveryControl(DiscoveryFocus.TOOLS, index)
                        viewModel.confirmDiscoveryControl(onGameSelect)
                    }, icon = { Icon(icon, stringResource(label), Modifier.size(d.control / 2), MaterialTheme.colorScheme.onSurface) })
            }
        val filterLabel = when (state.libraryFilter) {
            com.nendo.argosy.data.preferences.HomeLibraryFilter.ALL -> R.string.discovery_all_games
            com.nendo.argosy.data.preferences.HomeLibraryFilter.DOWNLOADABLE -> R.string.discovery_available
            com.nendo.argosy.data.preferences.HomeLibraryFilter.LIBRARY -> R.string.discovery_library_only
        }
        DiscoveryButton(stringResource(filterLabel),
            state.discoveryFocus.zone == DiscoveryFocus.TOOLS && state.discoveryFocus.control == 2,
            d, onClick = {
                viewModel.focusDiscoveryControl(DiscoveryFocus.TOOLS, 2)
                viewModel.confirmDiscoveryControl(onGameSelect)
            })
        SystemStatusBar(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiscoveryPlatforms(state: HomeUiState, viewModel: HomeViewModel, d: DiscoveryDimensions) {
    val list = rememberLazyListState()
    val selected = if (state.discoveryFocus.zone == DiscoveryFocus.PLATFORMS) state.discoveryFocus.control
        else state.availableRows.indexOf(state.currentRow).coerceAtLeast(0)
    LaunchedEffect(selected) { if (state.availableRows.isNotEmpty()) list.animateScrollToItem(selected) }
    LazyRow(state = list, modifier = Modifier.fillMaxWidth().height(d.platformBar),
        horizontalArrangement = Arrangement.spacedBy(d.gap / 2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = d.padding)) {
        itemsIndexed(state.availableRows, key = { _, row -> row.toString() }) { index, row ->
            val label = if (row == HomeRow.Continue) stringResource(R.string.discovery_home)
                else if (row.shortLabelRes != null) stringResource(row.shortLabelRes)
                else state.shortLabelFor(row)
            DiscoveryButton(label, state.discoveryFocus.zone == DiscoveryFocus.PLATFORMS && selected == index,
                d, onClick = { viewModel.selectRow(row) }, primary = row == state.currentRow, quiet = row != state.currentRow)
        }
    }
}

@Composable
private fun DiscoveryFeeds(state: HomeUiState, viewModel: HomeViewModel, d: DiscoveryDimensions) {
    Row(Modifier.fillMaxWidth().height(d.control + d.sectionGap).padding(horizontal = d.padding),
        horizontalArrangement = Arrangement.spacedBy(d.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        DiscoveryFeed.entries.forEachIndexed { index, feed ->
            DiscoveryButton(stringResource(feed.labelRes),
                state.discoveryFocus.zone == DiscoveryFocus.FEEDS && state.discoveryFocus.control == index,
                d, onClick = { viewModel.selectDiscoveryFeed(feed) }, primary = state.discoveryFocus.feed == feed, quiet = state.discoveryFocus.feed != feed)
        }
        DiscoveryButton(stringResource(R.string.discovery_full_library),
            state.discoveryFocus.zone == DiscoveryFocus.FEEDS && state.discoveryFocus.control == DiscoveryFeed.entries.size,
            d, viewModel::openDiscoveryLibrary)
    }
}
