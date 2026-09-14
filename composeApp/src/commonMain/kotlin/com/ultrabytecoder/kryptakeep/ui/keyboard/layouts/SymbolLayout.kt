package com.ultrabytecoder.kryptakeep.ui.keyboard.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrabytecoder.kryptakeep.ui.keyboard.components.KeyboardKey
import com.ultrabytecoder.kryptakeep.ui.keyboard.components.KeyboardRow
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyCode
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyboardLayoutType
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.LocalKeyboardController

@Composable
fun SymbolLayout(
    modifier: Modifier = Modifier,
    actionLabel: String = "Done"
) {
    val controller = LocalKeyboardController.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        KeyboardRow {
            listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0').forEach { char ->
                KeyboardKey(
                    label = char.toString(),
                    onClick = { controller?.onKey(KeyCode.Digit(char)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        KeyboardRow {
            listOf("-", "=", "(", ")", "[", "]", "{", "}", "@", "#").forEach { symbol ->
                KeyboardKey(
                    label = symbol,
                    onClick = { controller?.insertChar(symbol[0]) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        KeyboardRow {
            listOf("$", "^", "&", "*", "_", "+", "%", "~").forEach { symbol ->
                KeyboardKey(
                    label = symbol,
                    onClick = { controller?.insertChar(symbol[0]) },
                    modifier = Modifier.weight(1f)
                )
            }
            KeyboardKey(
                label = "⌫",
                onClick = { controller?.onKey(KeyCode.Backspace) },
                contentDescription = "Delete",
                modifier = Modifier.weight(2f)
            )
        }

        KeyboardRow {
            KeyboardKey(
                label = "?ABC",
                onClick = { controller?.switchToQwerty() },
                contentDescription = "Letters",
                modifier = Modifier.weight(1.5f)
            )
            KeyboardKey(
                label = " ",
                onClick = { controller?.onKey(KeyCode.Space) },
                modifier = Modifier.weight(4f)
            )
            KeyboardKey(
                label = actionLabel,
                onClick = { controller?.onKey(KeyCode.Action) },
                modifier = Modifier.weight(2f)
            )
        }
    }
}
