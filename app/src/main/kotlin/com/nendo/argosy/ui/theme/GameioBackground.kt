package com.nendo.argosy.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.FocusEffects as FX
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlin.math.sin
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.GameioBackdrop as T

/**
 * A stationary square grid keeps the backdrop quiet while game rows scroll; only the glow
 * drifts, so a still screen keeps breathing without anything sliding under the covers.
 */
@Composable
fun Modifier.gameioBackground(): Modifier {
    val colors = MaterialTheme.colorScheme
    val drifting = LocalMotionTier.current != MotionTier.Reduced
    val transition = if (drifting) rememberInfiniteTransition(label = "backdropDrift") else null
    val driftPhase by transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.Tween.driftMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "backdropDrift"
    ) ?: remember { mutableStateOf(0f) }
    return drawWithCache {
        val cell = T.cellSizeDp.dp.toPx()
        val grid = Path().apply {
            var x = 0f
            while (x < size.width) { moveTo(x, 0f); lineTo(x, size.height); x += cell }
            var y = 0f
            while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += cell }
        }
        val sweep = sin(driftPhase * TWO_PI)
        val breathe = (sin(driftPhase * TWO_PI * 2f) + 1f) / 2f
        val travel = size.width * FX.driftTravelPercent
        val glowScale = FX.driftScaleMin + (FX.driftScaleMax - FX.driftScaleMin) * breathe
        val glow = Brush.radialGradient(
            listOf(colors.onSurface.copy(alpha = T.glowAlpha), Color.Transparent),
            center = Offset(size.width / 2 + travel * sweep, travel * sweep * 0.5f),
            radius = size.maxDimension * T.glowRadiusRatio * glowScale
        )
        val stroke = Stroke(T.lineWidthDp.dp.toPx())
        onDrawBehind {
            drawRect(colors.background)
            drawRect(glow)
            drawPath(grid, colors.onSurface.copy(alpha = T.gridAlpha), style = stroke)
        }
    }
}

private const val TWO_PI = (Math.PI * 2).toFloat()
