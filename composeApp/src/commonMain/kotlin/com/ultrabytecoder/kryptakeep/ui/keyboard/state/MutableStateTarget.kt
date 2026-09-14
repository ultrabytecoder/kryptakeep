package com.ultrabytecoder.kryptakeep.ui.keyboard.state

class MutableStateTarget(
    override val id: String,
    private val getter: () -> String,
    private val setter: (String) -> Unit,
    private val maxLength: Int = Int.MAX_VALUE,
    override val maxLines: Int = 1,
    override val isSingleLine: Boolean = true
) : KeyboardTarget {
    override val text: String get() = getter()

    override fun insert(char: Char) {
        if (isFull()) return
        setter(text + char)
    }

    override fun delete() {
        if (text.isNotEmpty()) {
            setter(text.dropLast(1))
        }
    }

    override fun clear() {
        setter("")
    }

    override fun isFull(): Boolean = text.length >= maxLength
}
