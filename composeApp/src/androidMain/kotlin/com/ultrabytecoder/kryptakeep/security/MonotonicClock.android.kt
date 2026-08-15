package com.ultrabytecoder.kryptakeep.security

/**
 * `elapsedRealtime` counts time since device boot, is monotonic (immune to
 * user clock changes) and includes deep sleep. Resets only on reboot.
 */
actual fun monotonicNowMillis(): Long = android.os.SystemClock.elapsedRealtime()
