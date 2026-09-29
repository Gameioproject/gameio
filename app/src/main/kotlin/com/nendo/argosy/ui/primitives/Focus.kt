package com.nendo.argosy.ui.primitives

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.LocalActiveGamePalette
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens

enum class FocusDirection { Up, Down, Left, Right }

data class FocusIndicators(
    val halo: Boolean = false,
    val lift: Boolean = false,
    val ring: Boolean = false,
    val stripe: Boolean = false,
    val fill: Boolean = false,
    val bar: Boolean = false,
    val wash: Boolean = false,
) {
    companion object {
        val Tile = FocusIndicators(halo = true, lift = true)
        val NavRow = FocusIndicators(bar = true, wash = true)
        val TabRow = FocusIndicators(bar = true, wash = true)
        val ListRow = FocusIndicators(bar = true, wash = true)
        val Row = FocusIndicators(bar = true, wash = true)
        val Button = FocusIndicators(fill = true)
        val Pill = FocusIndicators(fill = true)
        val Subtle = FocusIndicators(fill = true)
        val Ring = FocusIndicators(ring = true)
        val None = FocusIndicators()
        val Default = ListRow
    }
}

fun Modifier.argosyFocusIndicators(
    focused: Boolean,
    indicators: FocusIndicators = FocusIndicators.Default,
    selected: Boolean = false,
    tint: Color? = null,
    liftDp: Dp = 6.dp,
    ringColor: Color? = null,
    ringThickness: Dp = 2.dp,
    stripeColor: Color? = null,
    stripeWidth: Dp = 4.dp,
    fillAlpha: Float = 0.18f,
    shape: Shape = RectangleShape,
): Modifier = composed {
    val theme = LocalArgosyTheme.current
    val palette = LocalActiveGamePalette.current
    val effectiveTint = tint ?: palette.firstOrNull() ?: theme.focusAccent
    val effectiveStripeColor = stripeColor ?: effectiveTint
    val effectiveRingColor = ringColor ?: effectiveTint
    val tier = LocalMotionTier.current
    val tierSpringFloat = Motion.tierFocusSpring(tier)
    val tierSpringDp = Motion.tierFocusSpringDp(tier)

    val translationY by animateDpAsState(
        targetValue = if (focused && indicators.lift) -liftDp else 0.dp,
        animationSpec = tierSpringDp,
        label = "argosy-lift",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused && indicators.lift) 1.04f else 1f,
        animationSpec = tierSpringFloat,
        label = "argosy-scale",
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (focused && indicators.halo) 1f else 0f,
        animationSpec = tierSpringFloat,
        label = "argosy-halo-alpha",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (focused && indicators.ring) 1f else 0f,
        animationSpec = tierSpringFloat,
        label = "argosy-ring-alpha",
    )
    val fillAlphaAnim by animateFloatAsState(
        targetValue = when {
            focused && indicators.fill -> fillAlpha
            selected && indicators.fill -> fillAlpha * 0.6f
            else -> 0f
        },
        animationSpec = tierSpringFloat,
        label = "argosy-fill-alpha",
    )
    val stripeAlphaAnim by animateFloatAsState(
        targetValue = if ((focused || selected) && indicators.stripe) 1f else 0f,
        animationSpec = tierSpringFloat,
        label = "argosy-stripe-alpha",
    )
    val effectAlpha by animateFloatAsState(
        targetValue = when {
            focused -> 1f
            selected -> 0.55f
            else -> 0f
        },
        animationSpec = tierSpringFloat,
        label = "argosy-effect-alpha",
    )

    var m: Modifier = this
    if (indicators.halo && haloAlpha > 0f) {
        m = m.drawBehind {
            val centerY = size.height / 2f
            val centerX = size.width / 2f
            val maxRadius = maxOf(size.width, size.height) * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to effectiveTint.copy(alpha = 0.55f * haloAlpha),
                        0.45f to effectiveTint.copy(alpha = 0.14f * haloAlpha),
                        0.85f to Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius,
                ),
                radius = maxRadius,
                center = Offset(centerX, centerY),
            )
        }
    }
    if (indicators.lift) {
        m = m.graphicsLayer {
            this.translationY = translationY.toPx()
            this.scaleX = scale
            this.scaleY = scale
        }
    }
    if (indicators.fill && fillAlphaAnim > 0f) {
        m = m.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawFocusOutline(outline, effectiveTint.copy(alpha = fillAlphaAnim))
        }
    }
    if (indicators.ring && ringAlpha > 0f) {
        m = m.border(width = ringThickness, color = effectiveRingColor.copy(alpha = ringAlpha), shape = shape)
    }
    if (indicators.wash && effectAlpha > 0f) {
        m = m.drawBehind { drawFocusWash(shape, effectiveTint, effectAlpha, layoutDirection) }
    }
    if (indicators.bar && effectAlpha > 0f) {
        m = m.drawBehind { drawFocusBar(effectiveTint, effectAlpha) }
    }
    if (indicators.stripe && stripeAlphaAnim > 0f) {
        m = m.drawBehind {
            val w = stripeWidth.toPx()
            drawRoundRect(
                color = effectiveStripeColor.copy(alpha = stripeAlphaAnim),
                topLeft = Offset(0f, size.height * 0.18f),
                size = Size(w, size.height * 0.64f),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
    m
}


private fun DrawScope.drawFocusWash(
    shape: Shape,
    tint: Color,
    alpha: Float,
    layoutDirection: LayoutDirection,
) {
    val end = size.width * ComponentDefaults.FocusEffects.washEndPercent
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to tint.copy(alpha = ComponentDefaults.FocusEffects.washAlpha * alpha),
            0.38f to tint.copy(alpha = ComponentDefaults.FocusEffects.washMidAlpha * alpha),
            1f to Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(end, 0f),
        tileMode = TileMode.Clamp,
    )
    drawFocusOutline(shape.createOutline(size, layoutDirection, this), brush)
}

private fun DrawScope.drawFocusBar(tint: Color, alpha: Float) {
    val width = DimensionTokens.Layout.focusBarWidth.toFloat()
    val inset = DimensionTokens.Layout.focusBarInset.toFloat()
    val height = (size.height - inset * 2f).coerceAtLeast(width)
    val top = (size.height - height) / 2f
    drawRoundRect(
        color = tint.copy(alpha = alpha),
        topLeft = Offset(0f, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f, width / 2f),
    )
}

private fun DrawScope.drawFocusOutline(outline: Outline, color: Color) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = Offset(outline.rect.left, outline.rect.top),
            size = Size(outline.rect.width, outline.rect.height),
        )
        is Outline.Rounded -> drawRoundRect(
            color = color,
            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
            size = Size(outline.roundRect.width, outline.roundRect.height),
            cornerRadius = CornerRadius(outline.roundRect.topLeftCornerRadius.x, outline.roundRect.topLeftCornerRadius.y),
        )
        is Outline.Generic -> drawPath(path = outline.path, color = color)
    }
}

private fun DrawScope.drawFocusOutline(outline: Outline, brush: Brush) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = Offset(outline.rect.left, outline.rect.top),
            size = Size(outline.rect.width, outline.rect.height),
        )
        is Outline.Rounded -> drawRoundRect(
            brush = brush,
            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
            size = Size(outline.roundRect.width, outline.roundRect.height),
            cornerRadius = CornerRadius(outline.roundRect.topLeftCornerRadius.x, outline.roundRect.topLeftCornerRadius.y),
        )
        is Outline.Generic -> drawPath(path = outline.path, brush = brush)
    }
}
