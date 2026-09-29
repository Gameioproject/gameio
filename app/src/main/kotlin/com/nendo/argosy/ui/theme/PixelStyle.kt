package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.Pixel as T

val PixelFontFamily = FontFamily(Font(R.font.silkscreen_regular))

fun pixelLabelStyle(fontSize: TextUnit = T.labelSp.sp): TextStyle = TextStyle(
    fontFamily = PixelFontFamily,
    fontSize = fontSize,
    lineHeight = TextUnit.Unspecified,
    letterSpacing = T.labelLetterSpacingSp.sp
)

/**
 * A rectangle with one pixel notch cut from each corner, the edge the Gameio controller glyph
 * is drawn with. The notch never exceeds a quarter of the shorter side, so small chips keep
 * their body.
 */
@Immutable
class SteppedCornerShape(private val step: Dp = T.stepDp.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.toPx() }.coerceAtMost(minOf(size.width, size.height) / 4f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)
            lineTo(w - s, s)
            lineTo(w, s)
            lineTo(w, h - s)
            lineTo(w - s, h - s)
            lineTo(w - s, h)
            lineTo(s, h)
            lineTo(s, h - s)
            lineTo(0f, h - s)
            lineTo(0f, s)
            lineTo(s, s)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is SteppedCornerShape && other.step == step

    override fun hashCode(): Int = step.hashCode()
}

val PixelShape = SteppedCornerShape()

/**
 * Draws four square corner brackets just outside the element's bounds, the pixel focus marker
 * that replaces a rounded outline around covers.
 */
fun Modifier.pixelCornerBrackets(
    color: Color,
    length: Dp = T.bracketLengthDp.dp,
    width: Dp = T.bracketWidthDp.dp,
    offset: Dp = T.bracketOffsetDp.dp
): Modifier = drawWithContent {
    drawContent()
    val l = length.toPx()
    val t = width.toPx()
    val o = offset.toPx()
    val left = -o
    val top = -o
    val right = size.width + o
    val bottom = size.height + o
    drawRect(color, Offset(left, top), Size(l, t))
    drawRect(color, Offset(left, top), Size(t, l))
    drawRect(color, Offset(right - l, top), Size(l, t))
    drawRect(color, Offset(right - t, top), Size(t, l))
    drawRect(color, Offset(left, bottom - t), Size(l, t))
    drawRect(color, Offset(left, bottom - l), Size(t, l))
    drawRect(color, Offset(right - l, bottom - t), Size(l, t))
    drawRect(color, Offset(right - t, bottom - l), Size(t, l))
}
