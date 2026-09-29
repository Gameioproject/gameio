package com.nendo.argosy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.GameioBackdrop as T

/** A stationary pixel-dot grid keeps the backdrop quiet while game rows scroll. */
@Composable
fun Modifier.gameioBackground(): Modifier {
    val colors = MaterialTheme.colorScheme
    return drawWithCache {
        val cell = T.cellSizeDp.dp.toPx().roundToInt().coerceAtLeast(2)
        val dot = T.dotSizeDp.dp.toPx().coerceIn(1f, cell / 2f)
        val tile = ImageBitmap(cell, cell).also { bitmap ->
            Canvas(bitmap).drawRect(0f, 0f, dot, dot, Paint().apply {
                color = colors.onSurface.copy(alpha = T.gridAlpha)
            })
        }
        val dots = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        onDrawBehind {
            drawRect(colors.background)
            drawRect(dots)
        }
    }
}
