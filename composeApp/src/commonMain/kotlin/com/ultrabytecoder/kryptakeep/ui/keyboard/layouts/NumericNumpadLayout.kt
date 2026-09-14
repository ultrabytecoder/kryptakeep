package com.ultrabytecoder.kryptakeep.ui.keyboard.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrabytecoder.kryptakeep.ui.keyboard.components.KeyboardKey
import com.ultrabytecoder.kryptakeep.ui.keyboard.components.KeyboardRow
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyCode
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.LocalKeyboardController

@Composable
fun NumericNumpadLayout(
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onDigitClick: ((Int) -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val controller = LocalKeyboardController.current

    val handleDigit: (Int) -> Unit = onDigitClick ?: { digit ->
        controller?.onKey(KeyCode.Digit(digit.toString()[0]))
    }
    val handleDelete: () -> Unit = onDeleteClick ?: {
        controller?.onKey(KeyCode.Backspace)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        KeyboardRow {
            listOf(1, 2, 3).forEach { digit ->
                KeyboardKey(
                    label = digit.toString(),
                    onClick = { handleDigit(digit) },
                    enabled = !isLocked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        KeyboardRow {
            listOf(4, 5, 6).forEach { digit ->
                KeyboardKey(
                    label = digit.toString(),
                    onClick = { handleDigit(digit) },
                    enabled = !isLocked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        KeyboardRow {
            listOf(7, 8, 9).forEach { digit ->
                KeyboardKey(
                    label = digit.toString(),
                    onClick = { handleDigit(digit) },
                    enabled = !isLocked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        KeyboardRow {
            Box(modifier = Modifier.weight(1f))
            KeyboardKey(
                label = "0",
                onClick = { handleDigit(0) },
                enabled = !isLocked,
                modifier = Modifier.weight(1f)
            )
            KeyboardKey(
                label = "⌫",
                onClick = { handleDelete() },
                enabled = !isLocked,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
