package com.ultrabytecoder.kryptakeep.security

/**
 * The desktop target has no OS key store for this purpose — the device key is
 * the in-memory fallback in [HardwareKeyStore] (see desktop-security.md for the
 * threat-model discussion).
 */
internal actual fun createKeystoreProvider(lock: Any): KeystoreProvider = KeystoreProvider(lock)
