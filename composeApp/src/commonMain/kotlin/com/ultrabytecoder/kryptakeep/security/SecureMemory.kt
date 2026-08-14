package com.ultrabytecoder.kryptakeep.security

/**
 * Extensions for securely wiping sensitive data from memory.
 */
fun ByteArray.wipe() {
    fill(0)
}

fun CharArray.wipe() {
    fill('\u0000')
}