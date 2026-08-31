package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The grid the search keyboard walks. Alphabetical on purpose: with a d-pad, predictable
 * hunt-time beats QWERTY familiarity, which is why consoles lay their keyboards out this way.
 */
object ConsoleKeyboardLayout {
    val charRows: List<String> = listOf(
        "abcdefg",
        "hijklmn",
        "opqrstu",
        "vwxyz-'",
        "0123456",
        "789.:&/"
    )
    const val COLS = 7
    const val ACTION_ROW = 6

    enum class ActionKey { SPACE, DELETE, CLEAR }

    val actions = listOf(ActionKey.SPACE, ActionKey.DELETE, ActionKey.CLEAR)

    val rowCount = charRows.size + 1

    fun maxColFor(row: Int): Int =
        if (row == ACTION_ROW) actions.size - 1 else COLS - 1

    fun charAt(row: Int, col: Int): Char? =
        charRows.getOrNull(row)?.getOrNull(col)

    sealed interface KeyOutput {
        data class Character(val value: Char) : KeyOutput
        data object Space : KeyOutput
        data object Delete : KeyOutput
        data object Clear : KeyOutput
    }

    fun outputAt(row: Int, col: Int): KeyOutput? =
        if (row == ACTION_ROW) {
            when (actions.getOrNull(col)) {
                ActionKey.SPACE -> KeyOutput.Space
                ActionKey.DELETE -> KeyOutput.Delete
                ActionKey.CLEAR -> KeyOutput.Clear
                null -> null
            }
        } else {
            charAt(row, col)?.let { KeyOutput.Character(it) }
        }
}

@Composable
fun ConsoleKeyboard(
    focusedRow: Int,
    focusedCol: Int,
    active: Boolean,
    onKeyTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        modifier = modifier.padding(Dimens.spacingMd)
    ) {
        ConsoleKeyboardLayout.charRows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                row.forEachIndexed { colIndex, char ->
                    KeyCap(
                        label = char.toString(),
                        focused = active && focusedRow == rowIndex && focusedCol == colIndex,
                        onTap = { onKeyTap(rowIndex, colIndex) },
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            ConsoleKeyboardLayout.actions.forEachIndexed { colIndex, action ->
                KeyCap(
                    label = stringResource(
                        when (action) {
                            ConsoleKeyboardLayout.ActionKey.SPACE -> R.string.search_kb_space
                            ConsoleKeyboardLayout.ActionKey.DELETE -> R.string.search_kb_delete
                            ConsoleKeyboardLayout.ActionKey.CLEAR -> R.string.search_kb_clear
                        }
                    ),
                    focused = active &&
                        focusedRow == ConsoleKeyboardLayout.ACTION_ROW &&
                        focusedCol == colIndex,
                    onTap = { onKeyTap(ConsoleKeyboardLayout.ACTION_ROW, colIndex) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    focused: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape
            )
            .border(
                Dimens.borderThin,
                if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape
            )
            .clickableNoFocus(onClick = onTap)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}
