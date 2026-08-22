package com.ultrabytecoder.kryptakeep.security

/**
 * No-op on Android: activity resume events already reset the idle timeout
 * (see WalletApplication.onActivityResumed).
 */
actual fun installIdleHook(callback: () -> Unit) {
}
