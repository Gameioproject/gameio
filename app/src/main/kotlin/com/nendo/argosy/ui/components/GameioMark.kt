package com.nendo.argosy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

object GameioBrand {
    val Tile = Color(0xFF0B0B0C)
    val Glyph = Color(0xFFF5F5F5)
}

private val MarkRows = listOf(
    ".XXXXX...XXXXX.",
    "XXXXXXXXXXXXXXX",
    "XXX.XXXXXXXX.XX",
    "XX...XXXXXX.X.X",
    "XXX.XXXXXXXX.XX",
    "XXXXXXXXXXXXXXX",
    "XXXXXX...XXXXXX",
    "XXXX.......XXXX",
    ".XX.........XX.",
)

private data class MarkRun(val col: Int, val row: Int, val length: Int)

private val MarkRuns: List<MarkRun> = MarkRows.flatMapIndexed { row, line ->
    Regex("X+").findAll(line).map { MarkRun(it.range.first, row, it.value.length) }.toList()
}

/**
 * The Gameio mark: a pixel-art controller, white on a black rounded tile. Same geometry as the
 * launcher icon and the website favicon (108-unit canvas). Without the tile the glyph takes the
 * theme's onBackground colour so it stays visible in both themes.
 */
@Composable
fun GameioMark(modifier: Modifier = Modifier, tile: Boolean = true) {
    val glyph = if (tile) GameioBrand.Glyph else MaterialTheme.colorScheme.onBackground
    Canvas(modifier = modifier) {
        val u = size.minDimension / 108f
        if (tile) {
            drawRoundRect(
                color = GameioBrand.Tile,
                size = Size(108f * u, 108f * u),
                cornerRadius = CornerRadius(22f * u, 22f * u)
            )
        }
        val cell = (if (tile) 4.4f else 7.2f) * u
        val left = (size.width - MarkRows[0].length * cell) / 2f
        val top = (size.height - MarkRows.size * cell) / 2f
        val path = Path()
        MarkRuns.forEach { run ->
            path.addRect(
                Rect(
                    left = left + run.col * cell,
                    top = top + run.row * cell,
                    right = left + (run.col + run.length) * cell,
                    bottom = top + (run.row + 1) * cell
                )
            )
        }
        drawPath(path, glyph)
    }
}
