package com.ultrabytecoder.kryptakeep.ui.keyboard.model

sealed class KeyCode {
    data class Letter(val char: Char) : KeyCode()
    data class Digit(val digit: Char) : KeyCode()
    data object Space : KeyCode()
    data object Backspace : KeyCode()
    data object Shift : KeyCode()
    data object SymbolToggle : KeyCode()
    data object Action : KeyCode()
}
