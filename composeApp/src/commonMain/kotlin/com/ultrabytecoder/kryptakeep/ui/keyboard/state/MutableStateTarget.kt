package com.ultrabytecoder.kryptakeep.ui.keyboard.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MutableStateTarget(
    override val id: String,
    private val getter: () -> String,
    private val setter: (String) -> Unit,
    private val maxLength: Int = Int.MAX_VALUE,
    override val maxLines: Int = 1,
    override val isSingleLine: Boolean = true
) : KeyboardTarget {
    override val text: String get() = getter()
    override var cursorIndex: Int by mutableStateOf(0)

    override fun insert(char: Char) {
        if (isFull()) return
        val current = text
        val i = cursorIndex.coerceIn(0, current.length)
        setter(current.substring(0, i) + char + current.substring(i))
        cursorIndex = i + 1
    }

    override fun delete() {
        val current = text
        val i = cursorIndex.coerceIn(0, current.length)
        if (i > 0) {
            setter(current.substring(0, i - 1) + current.substring(i))
            cursorIndex = i - 1
        }
    }

    override fun clear() {
        setter("")
        cursorIndex = 0
    }

    override fun setCursor(index: Int) {
        cursorIndex = index.coerceIn(0, text.length)
    }

    override fun moveCursor(delta: Int) {
        setCursor(cursorIndex + delta)
    }

    override fun isFull(): Boolean = text.length >= maxLength
}
