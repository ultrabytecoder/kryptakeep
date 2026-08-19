package com.ultrabytecoder.kryptakeep.security

/**
 * JVM zeroization: the volatile sink makes the zeroed contents observable to
 * other threads, so the JIT cannot prove the fill is dead and eliminate it
 * (dead-store elimination). Same pattern as the Android implementation.
 */
private @Volatile var byteArraySink: ByteArray? = null

private @Volatile var charArraySink: CharArray? = null

actual fun ByteArray.wipe() {
    if (isEmpty()) return
    fill(0)
    byteArraySink = this
    byteArraySink = null
}

actual fun CharArray.wipe() {
    if (isEmpty()) return
    fill('\u0000')
    charArraySink = this
    charArraySink = null
}

actual fun gcHint() {
    // Best-effort hint on HotSpot; may be ignored.
    System.gc()
}