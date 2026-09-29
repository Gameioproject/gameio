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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.FocusEffects as FX
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlin.math.roundToInt
import kotlin.math.sin
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.GameioBackdrop as T

/**
 * A stationary pixel-dot grid keeps the backdrop quiet while game rows scroll; only the glow
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
        val cell = T.cellSizeDp.dp.toPx().roundToInt().coerceAtLeast(2)
        val dot = T.dotSizeDp.dp.toPx().coerceIn(1f, cell / 2f)
        val tile = ImageBitmap(cell, cell).also { bitmap ->
            Canvas(bitmap).drawRect(0f, 0f, dot, dot, Paint().apply {
                color = colors.onSurface.copy(alpha = T.gridAlpha)
            })
        }
        val dots = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        val travel = size.width * FX.driftTravelPercent
        val glowColors = listOf(colors.onSurface.copy(alpha = T.glowAlpha), Color.Transparent)
        onDrawBehind {
            val sweep = sin(driftPhase * TWO_PI)
            val breathe = (sin(driftPhase * TWO_PI * 2f) + 1f) / 2f
            val glowScale = FX.driftScaleMin + (FX.driftScaleMax - FX.driftScaleMin) * breathe
            val glow = Brush.radialGradient(
                glowColors,
                center = Offset(size.width / 2 + travel * sweep, travel * sweep * 0.5f),
                radius = size.maxDimension * T.glowRadiusRatio * glowScale
            )
            drawRect(colors.background)
            drawRect(glow)
            drawRect(dots)
        }
    }
}

private const val TWO_PI = (Math.PI * 2).toFloat()
