package com.nendo.argosy.ui.screens.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.ConsoleUi
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
internal fun SetupSystemRow(platform: PlatformEntity, isFocused: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val context = LocalContext.current
    val artwork = remember(platform.slug, platform.logoPath) {
        PlatformIconAssets.resolveAssetUri(context, platform.slug) ?: platform.logoPath
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = ConsoleUi.systemRowHeightDp.dp)
            .background(if (isFocused) colors.surfaceVariant else colors.surface, shape)
            .border(Dimens.borderThin, colors.outlineVariant, shape)
            .argosyFocusIndicators(isFocused, FocusIndicators.Ring, ringColor = colors.secondary, shape = shape)
            .semantics { selected = platform.syncEnabled; role = Role.Checkbox }
            .clickableNoFocus(onClick = onToggle)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Box(Modifier.size(Dimens.iconXl), contentAlignment = Alignment.Center) {
            if (artwork != null) {
                AsyncImage(artwork, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(Dimens.iconXl))
            } else {
                Text(platform.shortName, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(platform.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.firstrun_platform_select_game_count, platform.gameCount, platform.gameCount),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Box(
            Modifier.size(Dimens.iconMd)
                .background(if (platform.syncEnabled) colors.onSurface else colors.background, RoundedCornerShape(Dimens.radiusSm))
                .border(Dimens.borderThin, colors.outline, RoundedCornerShape(Dimens.radiusSm)),
            contentAlignment = Alignment.Center
        ) {
            if (platform.syncEnabled) Icon(Icons.Default.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(Dimens.iconSm))
        }
    }
}
