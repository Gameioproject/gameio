package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.nendo.argosy.ui.theme.generated.ComponentDefaults.ConsoleUi
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The grid the console keyboard walks. Standard QWERTY, the layout everyone already knows
 * from a phone, with a number row above and the symbols an address or a password needs below.
 * Rows are of unequal length and are centred like a real keyboard; the d-pad clamps to the
 * row it lands on.
 */
object ConsoleKeyboardLayout {
    val charRows: List<String> = listOf(
        "1234567890",
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
        ".@-_:/&'!?",
        ",+=#\$%*~;"
    )

    /** The widest row; key size is chosen so this many fit the available width. */
    val COLS: Int = charRows.maxOf { it.length }

    // Derived, so adding a character row cannot leave the action row pointing at it.
    val ACTION_ROW: Int get() = charRows.size

    enum class ActionKey { SPACE, DELETE, CLEAR }

    val actions = listOf(ActionKey.SPACE, ActionKey.DELETE, ActionKey.CLEAR)

    val rowCount = charRows.size + 1

    fun maxColFor(row: Int): Int =
        if (row == ACTION_ROW) actions.size - 1 else (charRows.getOrNull(row)?.length ?: 1) - 1

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
    modifier: Modifier = Modifier,
    caps: Boolean = false
) {
    val gap = Dimens.spacingXs
    val availableHeight = LocalConfiguration.current.screenHeightDp - ConsoleUi.keyboardReservedHeightDp
    val heightLimit = (availableHeight / ConsoleKeyboardLayout.rowCount).dp.coerceAtLeast(ConsoleUi.keyMinSizeDp.dp)
    BoxWithConstraints(modifier = modifier) {
        // Ten keys have to fit whatever width the caller gives, portrait phone included.
        val keySize = ((maxWidth - gap * (ConsoleKeyboardLayout.COLS - 1)) / ConsoleKeyboardLayout.COLS)
            .coerceIn(ConsoleUi.keyMinSizeDp.dp, ConsoleUi.keySizeDp.dp)
            .coerceAtMost(heightLimit)
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ConsoleKeyboardLayout.charRows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { colIndex, char ->
                        KeyCap(
                            label = (if (caps) char.uppercaseChar() else char).toString(),
                            focused = active && focusedRow == rowIndex && focusedCol == colIndex,
                            onTap = { onKeyTap(rowIndex, colIndex) },
                            keySize = keySize,
                            modifier = Modifier.size(keySize)
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.width(keySize * ConsoleKeyboardLayout.COLS + gap * (ConsoleKeyboardLayout.COLS - 1))
            ) {
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
                    keySize = keySize,
                    modifier = Modifier
                        .weight(1f)
                        .height(keySize)
                )
            }
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    focused: Boolean,
    onTap: () -> Unit,
    keySize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (focused) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape
            )
            .border(
                if (focused) Dimens.borderMedium else Dimens.borderThin,
                if (focused) {
                    MaterialTheme.colorScheme.secondary
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
            fontSize = minOf(MaterialTheme.typography.titleMedium.fontSize.value, keySize.value / 2).sp,
            color = if (focused) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}
