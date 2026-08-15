package com.ultrabytecoder.kryptakeep.security

/**
 * Securely zeroes sensitive data in memory.
 *
 * Plain `fill(0)` is not a zeroization guarantee: the JVM JIT can prove the
 * writes unobservable and remove them (dead-store elimination), and native
 * compilers can do the same for `memset`. Platform actuals use
 * compiler-resistant zeroization:
 * - Android/JVM: a volatile publication barrier that makes the zeroed contents
 *   observable to other threads, so the JIT cannot eliminate the fill.
 * - iOS: `memset_s` (C11), which cannot be elided.
 */
expect fun ByteArray.wipe()

/** See [ByteArray.wipe]. */
expect fun CharArray.wipe()
