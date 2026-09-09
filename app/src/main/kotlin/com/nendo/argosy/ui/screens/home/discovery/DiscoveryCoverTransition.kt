package com.nendo.argosy.ui.screens.home.discovery

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.DiscoveryHome as T
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DiscoveryCoverTransition(
    playing: Boolean,
    focused: Boolean,
    cardHeight: Dp,
    maxWidth: Dp,
    dimensions: DiscoveryDimensions,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable (Modifier) -> Unit,
    video: @Composable (Modifier) -> Unit,
    caption: @Composable () -> Unit = {}
) {
    val portraitWidth = cardHeight * T.cardAspectRatio
    val targetWidth = if (playing) cardHeight * T.trailerAspectRatio else portraitWidth
    val animated = focused || playing
    val width = if (animated) animateDpAsState(
        targetValue = minOf(targetWidth, maxWidth),
        animationSpec = tween(MotionTokens.Tween.mediumMs), label = "trailerWidth"
    ).value else minOf(portraitWidth, maxWidth)
    val coverAlpha = if (animated) animateFloatAsState(
        targetValue = if (playing) 0f else 1f,
        animationSpec = MotionTokens.Tween.medium, label = "trailerCoverAlpha"
    ).value else 1f
    val shape = RoundedCornerShape(dimensions.radius)
    Column(modifier.width(width)
        .then(if (focused) Modifier.border(dimensions.ring, MaterialTheme.colorScheme.primary, shape) else Modifier)
        .padding(dimensions.ringPadding)
        .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)) {
        if (animated) {
            Box(Modifier.fillMaxWidth().height(cardHeight).clip(shape)
                .background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                video(Modifier.fillMaxWidth().aspectRatio(T.trailerAspectRatio)
                    .graphicsLayer { alpha = 1f - coverAlpha })
                cover(Modifier.width(portraitWidth).fillMaxHeight()
                    .graphicsLayer { alpha = coverAlpha })
                Box(Modifier.matchParentSize().clickableNoFocus(onClick = onClick, onLongClick = onLongClick))
            }
        } else {
            cover(Modifier.fillMaxWidth().height(cardHeight))
        }
        caption()
    }
}
