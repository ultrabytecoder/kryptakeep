package com.ultrabytecoder.kryptakeep.security

/**
 * Converts a PIN [CharArray] to a [ByteArray] WITHOUT creating a boxed
 * intermediate `List<Byte>` (which would linger on the heap until GC).
 * The caller owns the returned array and must wipe it when done.
 */
fun CharArray.toPinBytes(): ByteArray {
    val bytes = ByteArray(size)
    for (i in indices) bytes[i] = this[i].code.toByte()
    return bytes
}