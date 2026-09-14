package com.ultrabytecoder.kryptakeep.ui.keyboard.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyCode
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyboardLayoutType

data class KeyboardUiState(
    val isVisible: Boolean = false,
    val layoutType: KeyboardLayoutType = KeyboardLayoutType.Qwerty,
    val isShifted: Boolean = false,
    val supportsDecimal: Boolean = false
)

/**
 * Central state + event dispatcher for the in-app keyboard.
 *
 * Thread confinement: every mutator (`show`, `hide`, `onKey`, `insertChar`,
 * `deleteChar`) writes Compose snapshot state and MUST run on the Compose/UI
 * (main) thread. All current call sites are Compose compositions or event
 * handlers (key clicks, tap-to-position), which already run on the UI thread —
 * keep new call sites there as well.
 */
class KeyboardController(
    var onAction: (() -> Unit)? = null
) {
    private var _state: KeyboardUiState by mutableStateOf(KeyboardUiState())
    var activeTarget: KeyboardTarget? by mutableStateOf(null)
    // When false (e.g. a locked or in-flight screen) the keyboard is inert: no
    // characters are inserted/deleted. Set per-screen via setInputEnabled.
    private var inputEnabled = true

    val isVisible: Boolean get() = _state.isVisible
    val target: KeyboardTarget? get() = activeTarget
    val layoutType: KeyboardLayoutType get() = _state.layoutType
    val isShifted: Boolean get() = _state.isShifted
    // Derived from layoutType (single source of truth) so it can't drift (M1/M22).
    val isSymbolsActive: Boolean get() = layoutType == KeyboardLayoutType.Symbols
    val supportsDecimal: Boolean get() = _state.supportsDecimal

    fun show(target: KeyboardTarget, layout: KeyboardLayoutType = KeyboardLayoutType.Qwerty, supportsDecimal: Boolean = false) {
        activeTarget = target
        _state = _state.copy(isVisible = true, layoutType = layout, isShifted = false, supportsDecimal = supportsDecimal)
    }

    fun showNumpad() {
        _state = _state.copy(isVisible = true, layoutType = KeyboardLayoutType.Numeric, isShifted = false)
    }

    fun hide() {
        _state = _state.copy(isVisible = false)
        activeTarget = null
    }

    fun onKey(key: KeyCode) {
        // Ignore key events outside an active input session — prevents shift/symbol
        // state from drifting when no target is showing.
        if (!isVisible || activeTarget == null) return
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
                val next = if (_state.layoutType == KeyboardLayoutType.Symbols) {
                    KeyboardLayoutType.Qwerty
                } else {
                    KeyboardLayoutType.Symbols
                }
                _state = _state.copy(layoutType = next, isShifted = false)
            }
            is KeyCode.Action -> {
                onAction?.invoke()
                hide()
            }
        }
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
    }

    fun insertChar(c: Char) {
        if (!inputEnabled) return
        activeTarget?.insert(c)
    }

    fun deleteChar() {
        if (!inputEnabled) return
        activeTarget?.delete()
    }
}

val LocalKeyboardController = compositionLocalOf<KeyboardController?> { null }

@Composable
fun rememberKeyboardController(onAction: (() -> Unit)? = null): KeyboardController {
    val controller = remember { KeyboardController() }
    // Keep onAction in sync across recomposition (e.g. a state-dependent submit
    // action whose captured state changes over time).
    val onActionRef = rememberUpdatedState(onAction)
    LaunchedEffect(controller) {
        controller.onAction = { onActionRef.value?.invoke() }
    }
    // Hide the keyboard when this controller's screen leaves composition so the
    // active target (and any secure field it drives) cannot outlive the screen.
    DisposableEffect(controller) {
        onDispose { controller.hide() }
    }
    return controller
}
