package com.ultrabytecoder.kryptakeep.ui.keyboard.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrabytecoder.kryptakeep.ui.keyboard.components.KeyboardKey
import com.ultrabytecoder.kryptakeep.ui.keyboard.components.KeyboardRow
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyCode
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.LocalKeyboardController

@Composable
fun QwertyLayout(
    modifier: Modifier = Modifier,
    actionLabel: String = "Done",
    onAction: (() -> Unit)? = null
) {
    val controller = LocalKeyboardController.current
    val isShifted = controller?.isShifted == true

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        KeyboardRow {
            listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p').forEach { char ->
                val label = if (isShifted) char.uppercaseChar().toString() else char.toString()
                KeyboardKey(
                    label = label,
                    onClick = { controller?.onKey(KeyCode.Letter(char)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        KeyboardRow(modifier = Modifier.padding(horizontal = 12.dp)) {
            listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l').forEach { char ->
                val label = if (isShifted) char.uppercaseChar().toString() else char.toString()
                KeyboardKey(
                    label = label,
                    onClick = { controller?.onKey(KeyCode.Letter(char)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        KeyboardRow {
            KeyboardKey(
                label = "⇧",
                onClick = { controller?.onKey(KeyCode.Shift) },
                isActive = isShifted,
                modifier = Modifier.weight(1.5f)
            )
            listOf('z', 'x', 'c', 'v', 'b', 'n', 'm').forEach { char ->
                val label = if (isShifted) char.uppercaseChar().toString() else char.toString()
                KeyboardKey(
                    label = label,
                    onClick = { controller?.onKey(KeyCode.Letter(char)) },
                    modifier = Modifier.weight(1f)
                )
            }
            KeyboardKey(
                label = "⌫",
                onClick = { controller?.onKey(KeyCode.Backspace) },
                modifier = Modifier.weight(1.5f)
            )
        }

        KeyboardRow {
            KeyboardKey(
                label = "?123",
                onClick = { controller?.onKey(KeyCode.SymbolToggle) },
                modifier = Modifier.weight(1.5f)
            )
            KeyboardKey(
                label = " ",
                onClick = { controller?.onKey(KeyCode.Space) },
                modifier = Modifier.weight(4f)
            )
            KeyboardKey(
                label = actionLabel,
                onClick = {
                    onAction?.invoke()
                    controller?.hide()
                },
                modifier = Modifier.weight(2f)
            )
        }
    }
}
