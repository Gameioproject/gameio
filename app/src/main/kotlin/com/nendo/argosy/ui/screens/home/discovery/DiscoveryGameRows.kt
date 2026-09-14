package com.nendo.argosy.ui.screens.home.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.interaction.DragInteraction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import com.nendo.argosy.ui.components.YouTubeVideoPlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.screens.home.*
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.DiscoveryHome as T
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DiscoveryGameRail(
    games: List<HomeGameUi>, zone: Int, state: HomeUiState, viewModel: HomeViewModel,
    dimensions: DiscoveryDimensions, cardHeight: Dp, modifier: Modifier = Modifier
) {
    val boxArtStyle = com.nendo.argosy.ui.theme.LocalBoxArtStyle.current
    val selected = if (zone == DiscoveryFocus.HERO && state.discoveryFocus.zone != zone) {
        state.discoveryFocus.heroIndex
    } else if (state.discoveryFocus.zone == zone) state.focusedGameIndex
    else state.discoveryFocus.rowIndexes[zone] ?: 0
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, games.lastIndex.coerceAtLeast(0)))
    var dragging by remember { mutableStateOf(false) }
    var touchSelection by remember { mutableStateOf<Int?>(null) }
    val currentGames by rememberUpdatedState(games)
    LaunchedEffect(list) {
        list.interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    snapshotFlow { list.isScrollInProgress }.first { !it }
                    val visible = list.layoutInfo.visibleItemsInfo
                    val item = visible.firstOrNull { it.offset >= 0 } ?: visible.firstOrNull()
                    if (item != null && item.index in currentGames.indices) {
                        touchSelection = item.index
                        viewModel.selectDiscoveryGame(zone, item.index)
                    }
                    dragging = false
                }
            }
        }
    }
    LaunchedEffect(selected, state.discoveryFocus.zone == zone) {
        if (touchSelection == selected) {
            touchSelection = null
        } else if (!dragging && state.discoveryFocus.zone == zone && games.isNotEmpty()) {
            touchSelection = null
            list.scrollDiscoverySelection(selected.coerceIn(0, games.lastIndex))
        }
    }
    BoxWithConstraints(modifier) {
    val availableWidth = maxWidth - dimensions.ringPadding * 2
    LazyRow(state = list, modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(dimensions.ringPadding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(games, key = { _, game -> game.id }, contentType = { _, _ -> "game" }) { index, game ->
            val focused = state.discoveryFocus.zone == zone && state.focusedGameIndex == index
            val request = state.videoPreviewRequest?.takeIf {
                focused && it.gameId == game.id && it.videoId == game.youtubeVideoId
            }
            DiscoveryCoverTransition(
                playing = request != null && state.isVideoPreviewActive,
                focused = focused, cardHeight = cardHeight, maxWidth = availableWidth,
                dimensions = dimensions,
                coverAspectRatio = if (boxArtStyle.nativeAspectRatio) {
                    game.coverAspectRatio ?: boxArtStyle.aspectRatio
                } else boxArtStyle.aspectRatio,
                onClick = { viewModel.selectDiscoveryGame(zone, index, activate = true) },
                onLongClick = {
                    viewModel.selectDiscoveryGame(zone, index)
                    viewModel.handleItemLongPress(index)
                },
                cover = { coverModifier ->
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                        primary = MaterialTheme.colorScheme.secondary,
                        onPrimary = MaterialTheme.colorScheme.onSecondary
                    )) {
                        GameCard(game = game, isFocused = focused, modifier = coverModifier,
                            showPlatformBadge = true, scaleOverride = 1f, alphaOverride = 1f,
                            downloadIndicator = state.downloadIndicatorFor(game.id),
                            coverPathOverride = state.repairedCoverPaths[game.id],
                            onCoverLoadFailed = viewModel::repairCoverImage,
                            onCoverLoaded = viewModel::extractGradientForGame)
                    }
                },
                video = { videoModifier ->
                    if (request != null) key(request) {
                        YouTubeVideoPlayer(
                            videoId = request.videoId, muted = state.muteVideoPreview,
                            onReady = { viewModel.activateVideoPreview(request) },
                            onError = { viewModel.cancelVideoPreviewLoading(request) },
                            modifier = videoModifier
                        )
                    }
                },
                caption = {
                    if (zone != DiscoveryFocus.HERO) Text(game.title, fontSize = dimensions.label,
                        color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = dimensions.ringPadding))
                }
            )
        }
    }
    }
}

@Composable
fun DiscoveryHero(
    state: HomeUiState, viewModel: HomeViewModel, dimensions: DiscoveryDimensions,
    onGameSelect: (Long) -> Unit
) {
    val game = state.discoveryHeroGame
    Row(Modifier.fillMaxWidth().height(dimensions.heroHeight).padding(horizontal = dimensions.padding),
        horizontalArrangement = Arrangement.spacedBy(dimensions.sectionGap),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxWidth(T.heroWidthRatio), verticalArrangement = Arrangement.Center) {
            Text(stringResource(if (game?.lastPlayedAt != null) R.string.discovery_continue else R.string.discovery_hero_fallback),
                fontSize = dimensions.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (game != null) {
                Text(game.title, fontSize = dimensions.title,
                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = dimensions.gap / 2))
                Text(listOfNotNull(game.genre?.split(",")?.firstOrNull(), game.releaseYear?.toString(),
                    game.platformDisplayName).joinToString(" · "),
                    fontSize = dimensions.label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                game.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = dimensions.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = dimensions.gap / 2))
                }
                game.rating?.let {
                    Text(stringResource(R.string.discovery_rating, it.toInt()), fontSize = dimensions.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = dimensions.gap / 2))
                }
                Row(Modifier.padding(top = dimensions.gap), horizontalArrangement = Arrangement.spacedBy(dimensions.gap / 2)) {
                    DiscoveryButton(stringResource(discoveryPrimaryLabel(game)),
                        focused = state.discoveryFocus.zone == DiscoveryFocus.ACTIONS && state.discoveryFocus.control == 0,
                        dimensions = dimensions, primary = true, onClick = {
                            viewModel.focusDiscoveryControl(DiscoveryFocus.ACTIONS, 0)
                            viewModel.confirmDiscoveryControl(onGameSelect)
                        })
                    DiscoveryButton(stringResource(R.string.discovery_details),
                        focused = state.discoveryFocus.zone == DiscoveryFocus.ACTIONS && state.discoveryFocus.control == 1,
                        dimensions = dimensions, onClick = {
                            viewModel.focusDiscoveryControl(DiscoveryFocus.ACTIONS, 1)
                            viewModel.confirmDiscoveryControl(onGameSelect)
                        })
                    DiscoveryButton("", focused = state.discoveryFocus.zone == DiscoveryFocus.ACTIONS && state.discoveryFocus.control == 2,
                        dimensions = dimensions, onClick = {
                            viewModel.focusDiscoveryControl(DiscoveryFocus.ACTIONS, 2)
                            viewModel.confirmDiscoveryControl(onGameSelect)
                        }, icon = {
                            Icon(if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(if (game.isFavorite) R.string.discovery_unfavorite else R.string.discovery_favorite),
                                modifier = Modifier.size(dimensions.control / 2))
                        })
                }
            } else {
                Text(stringResource(if (state.platforms.isEmpty()) R.string.discovery_no_platforms else R.string.discovery_no_recent),
                    fontSize = dimensions.body, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = dimensions.gap))
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val cardHeight = minOf(dimensions.heroCard,
                (maxWidth - dimensions.ringPadding * 4) / T.cardAspectRatio)
            DiscoveryGameRail(state.discoveryHero, DiscoveryFocus.HERO, state, viewModel,
                dimensions, cardHeight, Modifier.fillMaxWidth())
        }
    }
}

fun discoveryPrimaryLabel(game: HomeGameUi): Int = when {
    game.needsInstall -> R.string.discovery_install
    game.isDownloaded && game.lastPlayedAt != null -> R.string.discovery_resume
    game.isDownloaded -> R.string.discovery_play
    else -> R.string.discovery_download
}

@Composable
fun DiscoveryButton(
    label: String, focused: Boolean, dimensions: DiscoveryDimensions,
    onClick: () -> Unit, primary: Boolean = false, quiet: Boolean = false, icon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(dimensions.control / 2)
    Row(Modifier.height(dimensions.control)
        .border(dimensions.ring, if (focused) MaterialTheme.colorScheme.secondary else Color.Transparent, shape)
        .background(when {
            primary -> MaterialTheme.colorScheme.primary
            quiet -> Color.Transparent
            else -> MaterialTheme.colorScheme.surface
        }, shape)
        .clickableNoFocus(onClick = onClick)
        .padding(horizontal = dimensions.gap),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) icon()
        if (label.isNotEmpty()) Text(label, fontSize = dimensions.label,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1)
    }
}
