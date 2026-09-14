package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.ui.theme.gameioBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
internal fun SetupBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().gameioBackground()) { content() }
}

@Composable
internal fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    gamepadFocused: Boolean,
    focusRequester: FocusRequester,
    placeholder: String? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.radiusMd)
    OutlinedTextField(
        value = value,
        enabled = enabled,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        shape = shape,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceVariant,
            unfocusedContainerColor = if (gamepadFocused) colors.surfaceVariant else colors.background,
            focusedBorderColor = colors.secondary,
            unfocusedBorderColor = if (gamepadFocused) colors.secondary else colors.outline,
            focusedLabelColor = colors.onSurface,
            unfocusedLabelColor = if (gamepadFocused) colors.onSurface else colors.onSurfaceVariant,
            cursorColor = colors.secondary
        ),
        modifier = Modifier.fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
    )
}

@Composable
internal fun SetupError(message: String?) {
    if (message == null) return
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingMd)
    )
}

@Composable
internal fun SetupPrimaryButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null
) = SetupButton(text, isFocused, onClick, enabled, primary = true, icon = icon)

@Composable
internal fun SetupSecondaryButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) = SetupButton(text, isFocused, onClick, enabled, primary = false)

@Composable
private fun SetupButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val fill = when {
        !enabled -> colors.surfaceVariant
        primary -> colors.onSurface
        isFocused -> colors.surfaceVariant
        else -> colors.surface
    }
    val ink = when {
        !enabled -> colors.onSurfaceVariant
        primary -> colors.background
        else -> colors.onSurface
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = Dimens.buttonHeight)
            .background(fill, shape)
            .border(Dimens.borderThin, colors.outlineVariant, shape)
            .argosyFocusIndicators(
                focused = isFocused && enabled,
                indicators = FocusIndicators.Ring,
                ringColor = colors.secondary,
                ringThickness = Dimens.borderMedium,
                shape = shape
            )
            .clickableNoFocus(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(Dimens.iconSm))
        Text(text, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
    }
}
