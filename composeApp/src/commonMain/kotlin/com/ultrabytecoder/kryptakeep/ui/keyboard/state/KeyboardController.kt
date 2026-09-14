package com.ultrabytecoder.kryptakeep.ui.keyboard.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyCode
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyboardLayoutType

data class KeyboardUiState(
    val isVisible: Boolean = false,
    val layoutType: KeyboardLayoutType = KeyboardLayoutType.Qwerty,
    val isShifted: Boolean = false,
    val isSymbolsActive: Boolean = false
)

class KeyboardController(
    var onAction: (() -> Unit)? = null
) {
    private var _state: KeyboardUiState by mutableStateOf(KeyboardUiState())
    var activeTarget: KeyboardTarget? by mutableStateOf(null)

    val isVisible: Boolean get() = _state.isVisible
    val layoutType: KeyboardLayoutType get() = _state.layoutType
    val isShifted: Boolean get() = _state.isShifted
    val isSymbolsActive: Boolean get() = _state.isSymbolsActive

    val state: KeyboardUiState get() = _state

    fun show(target: KeyboardTarget, layout: KeyboardLayoutType = KeyboardLayoutType.Qwerty) {
        activeTarget = target
        _state = _state.copy(isVisible = true, layoutType = layout, isShifted = false)
    }

    fun showNumpad() {
        _state = _state.copy(isVisible = true, layoutType = KeyboardLayoutType.Numeric, isShifted = false)
    }

    fun hide() {
        _state = _state.copy(isVisible = false)
        activeTarget = null
    }

    fun switchToQwerty() {
        _state = _state.copy(isSymbolsActive = false, layoutType = KeyboardLayoutType.Qwerty, isShifted = false)
    }

    fun onKey(key: KeyCode) {
        when (key) {
            is KeyCode.Letter -> {
                val char = if (_state.isShifted) key.char.uppercaseChar() else key.char.lowercaseChar()
                insertChar(char)
                if (_state.isShifted) _state = _state.copy(isShifted = false)
            }
            is KeyCode.Digit -> insertChar(key.digit)
            is KeyCode.Space -> insertChar(' ')
            is KeyCode.Backspace -> deleteChar()
            is KeyCode.Shift -> _state = _state.copy(isShifted = !_state.isShifted)
            is KeyCode.SymbolToggle -> {
                val newSymbols = !_state.isSymbolsActive
                _state = _state.copy(
                    isSymbolsActive = newSymbols,
                    layoutType = if (newSymbols) KeyboardLayoutType.Symbols else KeyboardLayoutType.Qwerty,
                    isShifted = false
                )
            }
            is KeyCode.Action -> {
                onAction?.invoke()
                hide()
            }
        }
    }

    fun insertChar(c: Char) {
        activeTarget?.insert(c)
    }

    fun deleteChar() {
        activeTarget?.delete()
    }
}

val LocalKeyboardController = compositionLocalOf<KeyboardController?> { null }

@Composable
fun rememberKeyboardController(onAction: (() -> Unit)? = null): KeyboardController {
    return remember { KeyboardController(onAction = onAction) }
}
