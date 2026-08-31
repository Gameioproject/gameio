package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
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
    placeholder: String = ""
) {
    val dispatcher = LocalInputDispatcher.current
    val row = remember { mutableIntStateOf(0) }
    val col = remember { mutableIntStateOf(0) }
    val queryNow = rememberUpdatedState(query)
    val changeNow = rememberUpdatedState(onQueryChange)
    val dismissNow = rememberUpdatedState(onDismiss)

    fun applyAt(r: Int, c: Int) {
        when (val out = ConsoleKeyboardLayout.outputAt(r, c)) {
            is ConsoleKeyboardLayout.KeyOutput.Character ->
                changeNow.value(queryNow.value + out.value)
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

            override fun onBack(): InputResult {
                dismissNow.value()
                return InputResult.HANDLED
            }
        }
        dispatcher.pushModal(handler)
        onDispose { dispatcher.popModal() }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickableNoFocus(onClick = onDismiss)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = Modifier
                .width(440.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .clickableNoFocus(onClick = {})
                .padding(Dimens.spacingMd)
        ) {
            Text(
                text = query.ifEmpty { placeholder },
                style = MaterialTheme.typography.titleLarge,
                color = if (query.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
            ConsoleKeyboard(
                focusedRow = row.intValue,
                focusedCol = col.intValue,
                active = true,
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
                    InputButton.B to stringResource(R.string.search_kb_done)
                )
            )
        }
    }
}
