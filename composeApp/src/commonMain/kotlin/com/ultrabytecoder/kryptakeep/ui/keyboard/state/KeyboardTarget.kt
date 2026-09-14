package com.ultrabytecoder.kryptakeep.ui.keyboard.state

interface KeyboardTarget {
    val id: String
    val text: String
    fun insert(char: Char)
    fun delete()
    fun clear()
    fun isFull(): Boolean
    val maxLines: Int
    val isSingleLine: Boolean
}
