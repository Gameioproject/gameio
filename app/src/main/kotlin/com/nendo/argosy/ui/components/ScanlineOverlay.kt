package com.nendo.argosy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.FocusEffects as FX
import com.nendo.argosy.ui.theme.generated.MotionTokens

/**
 * CRT lines over everything the launcher draws, rolling by one line height.
 *
 * The roll is what keeps it from moire-ing into a static pattern on a 720p panel, and the
 * lines are drawn over the content rather than into the backdrop so covers and text get
 * them too, the way a real screen would.
 */
@Composable
fun Modifier.scanlineOverlay(enabled: Boolean): Modifier {
    if (!enabled) return this
    val rolling = LocalMotionTier.current != MotionTier.Reduced
    val transition = if (rolling) rememberInfiniteTransition(label = "scanlineRoll") else null
    val phase by transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.Tween.scanlineRollMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanlineRoll"
    ) ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }

    return drawWithContent {
        drawContent()
        val spacing = FX.scanlineSpacingDp.dp.toPx()
        val thickness = FX.scanlineThicknessDp.dp.toPx()
        val color = Color.White.copy(alpha = FX.scanlineAlpha)
        var y = -spacing + phase * spacing
        while (y < size.height) {
            drawRect(color = color, topLeft = Offset(0f, y), size = Size(size.width, thickness))
            y += spacing
        }
    }
}
