package com.nendo.argosy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate

/** Brand palette of the "Fan" mark; the tile blue is also the app's accent. */
object GameioBrand {
    val Tile = Color(0xFF1C3C8F)
    val Pink = Color(0xFFE64A8B)
    val Green = Color(0xFF2FBF8F)
    val Cover = Color(0xFFF2B734)
    val Art = Color(0xFFFF5A3C)
}

/**
 * The Gameio mark: three covers fanned like a hand of cards, the front one ringed white, on the
 * blue tile. Same geometry as the launcher icon and the website favicon (108-unit canvas).
 */
@Composable
fun GameioMark(modifier: Modifier = Modifier, tile: Boolean = true) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 108f
        if (tile) card(0f, 0f, 108f, 108f, 26f, GameioBrand.Tile, u)
        rotate(degrees = -18f, pivot = Offset(40f * u, 62f * u)) {
            card(23f, 36f, 34f, 52f, 5f, GameioBrand.Pink, u)
        }
        rotate(degrees = 18f, pivot = Offset(68f * u, 62f * u)) {
            card(51f, 36f, 34f, 52f, 5f, GameioBrand.Green, u)
        }
        card(33.5f, 28.5f, 41f, 59f, 8.5f, Color.White, u)
        card(37f, 32f, 34f, 52f, 5f, GameioBrand.Cover, u)
        card(44f, 41f, 20f, 18f, 2f, GameioBrand.Art, u)
        card(44f, 66f, 20f, 3f, 1.5f, GameioBrand.Tile.copy(alpha = 0.45f), u)
        card(44f, 72f, 12f, 3f, 1.5f, GameioBrand.Tile.copy(alpha = 0.3f), u)
    }
}

private fun DrawScope.card(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, u: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * u, y * u),
        size = Size(w * u, h * u),
        cornerRadius = CornerRadius(r * u, r * u)
    )
}
