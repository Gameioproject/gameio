package com.nendo.argosy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.GameioBackdrop as T

/** A stationary square grid keeps the backdrop quiet while game rows scroll. */
@Composable
fun Modifier.gameioBackground(): Modifier {
    val colors = MaterialTheme.colorScheme
    return drawWithCache {
        val cell = T.cellSizeDp.dp.toPx()
        val grid = Path().apply {
            var x = 0f
            while (x < size.width) { moveTo(x, 0f); lineTo(x, size.height); x += cell }
            var y = 0f
            while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += cell }
        }
        val glow = Brush.radialGradient(
            listOf(colors.onSurface.copy(alpha = T.glowAlpha), Color.Transparent),
            center = Offset(size.width / 2, 0f), radius = size.maxDimension * T.glowRadiusRatio
        )
        val stroke = Stroke(T.lineWidthDp.dp.toPx())
        onDrawBehind {
            drawRect(colors.background)
            drawRect(glow)
            drawPath(grid, colors.onSurface.copy(alpha = T.gridAlpha), style = stroke)
        }
    }
}
