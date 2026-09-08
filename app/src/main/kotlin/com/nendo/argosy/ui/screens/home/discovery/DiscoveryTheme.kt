package com.nendo.argosy.ui.screens.home.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.DiscoveryHome as T

data class DiscoveryDimensions(val scale: Float) {
    val padding get() = T.pagePaddingDp.dp * scale
    val bar get() = T.barHeightDp.dp * scale
    val platformBar get() = T.platformHeightDp.dp * scale
    val heroHeight get() = T.heroHeightDp.dp * scale
    val heroCard get() = T.heroCardHeightDp.dp * scale
    val rowCard get() = T.rowCardHeightDp.dp * scale
    val gap get() = T.cardGapDp.dp * scale
    val sectionGap get() = T.sectionGapDp.dp * scale
    val control get() = T.controlHeightDp.dp * scale
    val radius get() = T.radiusDp.dp * scale
    val ring get() = T.ringWidthDp.dp * scale
    val ringPadding get() = T.ringPaddingDp.dp * scale
    val emptyHeight get() = T.emptyHeightDp.dp * scale
    val headingHeight get() = T.rowHeadingHeightDp.dp * scale
    val title get() = T.titleSp.sp * scale
    val body get() = T.bodySp.sp * scale
    val label get() = T.labelSp.sp * scale
    val sectionTitle get() = T.sectionTitleSp.sp * scale
    val brand get() = T.brandSp.sp * scale
}

@Composable
fun DiscoveryTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = colors.copy(
        primary = colors.onSurface,
        onPrimary = colors.background
    )) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default, content = content)
    }
}
