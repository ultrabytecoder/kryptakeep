package com.ultrabytecoder.kryptakeep.ui.keyboard.state

interface KeyboardTarget {
    val text: String
    /**
     * Character count without materializing the full [text] string.
     * Preferred over `text.length` in mask/display paths where only the
     * count is needed (avoids holding a reference to the secret between
     * recompositions).
     */
    val length: Int get() = text.length
    /** Maximum accepted length; insert() is capped at this (single source of truth). */
    val maxLength: Int
    /** Cursor position (0..text.length); backed by snapshot state so the caret tracks it. */
    var cursorIndex: Int
    fun insert(char: Char)
    fun delete()
    fun clear()
    fun isFull(): Boolean = length >= maxLength
    /** Moves the cursor to [index], clamped to 0..text.length. */
    fun setCursor(index: Int)
    /** Moves the cursor by [delta], clamped to 0..text.length. */
    fun moveCursor(delta: Int)
    val maxLines: Int
    val isSingleLine: Boolean
}
