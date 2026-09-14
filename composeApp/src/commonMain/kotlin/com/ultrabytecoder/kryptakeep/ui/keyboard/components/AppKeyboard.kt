package com.ultrabytecoder.kryptakeep.ui.keyboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrabytecoder.kryptakeep.ui.keyboard.layouts.NumericNumpadLayout
import com.ultrabytecoder.kryptakeep.ui.keyboard.layouts.QwertyLayout
import com.ultrabytecoder.kryptakeep.ui.keyboard.layouts.SymbolLayout
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyboardLayoutType
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.LocalKeyboardController

@Composable
fun AppKeyboard(
    modifier: Modifier = Modifier,
    actionLabel: String = "Done",
    onAction: (() -> Unit)? = null
) {
    val controller = LocalKeyboardController.current ?: return

    AnimatedVisibility(
        visible = controller.isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            when (controller.layoutType) {
                KeyboardLayoutType.Numeric -> {
                    NumericNumpadLayout(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                KeyboardLayoutType.Qwerty,
                KeyboardLayoutType.Mnemonic,
                KeyboardLayoutType.Hex -> {
                    QwertyLayout(
                        actionLabel = actionLabel,
                        onAction = {
                            onAction?.invoke()
                            controller.hide()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                KeyboardLayoutType.Symbols -> {
                    SymbolLayout(
                        actionLabel = actionLabel,
                        onAction = {
                            onAction?.invoke()
                            controller.hide()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
