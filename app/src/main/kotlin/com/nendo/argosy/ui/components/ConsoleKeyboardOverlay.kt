package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.ConsoleUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * A drop-in console keyboard for any screen with a text query: it renders over a scrim, takes the
 * gamepad for itself while visible (pushModal), and edits the query purely through callbacks, so a
 * host only supplies its string and a way to close. A: type, X: delete, Y: space, B: done.
 */
@Composable
fun ConsoleKeyboardOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
    // Embedded drops the scrim and the centering, so the caller can seat the
    // keyboard in its own pane instead of floating it over the screen.
    embedded: Boolean = false,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dispatcher = LocalInputDispatcher.current
    val row = remember { mutableIntStateOf(0) }
    val col = remember { mutableIntStateOf(0) }
    val caps = remember { mutableStateOf(false) }
    val queryNow = rememberUpdatedState(query)
    val changeNow = rememberUpdatedState(onQueryChange)
    val dismissNow = rememberUpdatedState(onDismiss)

    fun applyAt(r: Int, c: Int) {
        when (val out = ConsoleKeyboardLayout.outputAt(r, c)) {
            is ConsoleKeyboardLayout.KeyOutput.Character ->
                changeNow.value(
                    queryNow.value + if (caps.value) out.value.uppercaseChar() else out.value
                )
            ConsoleKeyboardLayout.KeyOutput.Space ->
                if (queryNow.value.isNotEmpty() && !queryNow.value.endsWith(" ")) {
                    changeNow.value(queryNow.value + " ")
                }
            ConsoleKeyboardLayout.KeyOutput.Delete ->
                if (queryNow.value.isNotEmpty()) changeNow.value(queryNow.value.dropLast(1))
            ConsoleKeyboardLayout.KeyOutput.Clear -> changeNow.value("")
            null -> Unit
        }
    }

    DisposableEffect(Unit) {
        val handler = object : InputHandler {
            override fun onUp(): InputResult {
                row.intValue = (row.intValue - 1).mod(ConsoleKeyboardLayout.rowCount)
                col.intValue = col.intValue.coerceAtMost(ConsoleKeyboardLayout.maxColFor(row.intValue))
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                row.intValue = (row.intValue + 1).mod(ConsoleKeyboardLayout.rowCount)
                col.intValue = col.intValue.coerceAtMost(ConsoleKeyboardLayout.maxColFor(row.intValue))
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                val max = ConsoleKeyboardLayout.maxColFor(row.intValue)
                col.intValue = (col.intValue - 1).mod(max + 1)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                val max = ConsoleKeyboardLayout.maxColFor(row.intValue)
                col.intValue = (col.intValue + 1).mod(max + 1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                applyAt(row.intValue, col.intValue)
                return InputResult.HANDLED
            }

            override fun onContextMenu(): InputResult {
                applyAt(ConsoleKeyboardLayout.ACTION_ROW, 1)
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                applyAt(ConsoleKeyboardLayout.ACTION_ROW, 0)
                return InputResult.HANDLED
            }

            override fun onPrevSection(): InputResult {
                caps.value = !caps.value
                return InputResult.HANDLED
            }

            override fun onNextSection(): InputResult {
                caps.value = !caps.value
                return InputResult.HANDLED
            }

            override fun onMenu() = InputResult.HANDLED
            override fun onSelect() = InputResult.HANDLED
            override fun onPrevTrigger() = InputResult.HANDLED
            override fun onNextTrigger() = InputResult.HANDLED
            override fun onLeftStickClick() = InputResult.HANDLED
            override fun onRightStickClick() = InputResult.HANDLED
            override fun onLongConfirm() = InputResult.HANDLED

            override fun onBack(): InputResult {
                dismissNow.value()
                return InputResult.HANDLED
            }
        }
        dispatcher.pushModal(handler)
        onDispose { dispatcher.popModal() }
    }

    val panel: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = (if (embedded) modifier else Modifier.widthIn(max = ConsoleUi.formWidthDp.dp).fillMaxWidth())
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .border(Dimens.borderThin, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusLg))
                .clickableNoFocus(onClick = {})
                .padding(Dimens.spacingMd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                modifier = Modifier.fillMaxWidth()
            ) {
                KeyboardToolbarButton(
                    label = stringResource(R.string.search_kb_caps),
                    selected = caps.value,
                    onClick = { caps.value = !caps.value }
                )
                Text(
                    text = if (isPassword) "•".repeat(query.length).ifEmpty { placeholder } else query.ifEmpty { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = Dimens.spacingSm)
                )
                KeyboardToolbarButton(
                    label = stringResource(R.string.search_kb_done),
                    selected = false,
                    onClick = onDismiss
                )
            }
            ConsoleKeyboard(
                focusedRow = row.intValue,
                focusedCol = col.intValue,
                active = true,
                caps = caps.value,
                onKeyTap = { r, c ->
                    row.intValue = r
                    col.intValue = c
                    applyAt(r, c)
                }
            )
            FooterHints(
                hints = listOf(
                    InputButton.X to stringResource(R.string.search_kb_delete),
                    InputButton.Y to stringResource(R.string.search_kb_space),
                    InputButton.LB_RB to stringResource(R.string.search_kb_caps),
                    InputButton.B to stringResource(R.string.search_kb_done)
                ),
                onHintClick = { button ->
                    when (button) {
                        InputButton.X -> applyAt(ConsoleKeyboardLayout.ACTION_ROW, 1)
                        InputButton.Y -> applyAt(ConsoleKeyboardLayout.ACTION_ROW, 0)
                        InputButton.LB_RB -> caps.value = !caps.value
                        InputButton.B -> onDismiss()
                        else -> Unit
                    }
                }
            )
        }
    }

    if (embedded) {
        panel()
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                .clickableNoFocus(onClick = onDismiss)
        ) {
            panel()
        }
    }
}

@Composable
private fun KeyboardToolbarButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = Dimens.buttonHeight)
            .background(if (selected) colors.onSurface else colors.surfaceVariant, shape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.background else colors.onSurface)
    }
}
