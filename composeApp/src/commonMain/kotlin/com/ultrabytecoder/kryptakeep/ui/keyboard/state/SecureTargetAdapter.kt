package com.ultrabytecoder.kryptakeep.ui.keyboard.state

import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState

class SecureTargetAdapter(
    private val state: SecureTextFieldState,
    override val id: String = "secure_field",
    private val maxLength: Int = Int.MAX_VALUE,
    override val maxLines: Int = 1,
    override val isSingleLine: Boolean = true,
    private val onValueChanged: (String) -> Unit = {}
) : KeyboardTarget {
    override val text: String get() = state.text

    override fun insert(char: Char) {
        if (isFull()) return
        val current = state.toCharArray()
        val newChars = current + char
        current.wipe()
        state.update(newChars)
        newChars.wipe()
        onValueChanged(state.text)
    }

    override fun delete() {
        val current = state.toCharArray()
        if (current.isNotEmpty()) {
            val newChars = current.copyOfRange(0, current.size - 1)
            current.wipe()
            state.update(newChars)
            newChars.wipe()
            onValueChanged(state.text)
        }
    }

    override fun clear() {
        state.wipe()
        onValueChanged("")
    }

    override fun isFull(): Boolean = state.text.length >= maxLength
}
