package com.ultrabytecoder.kryptakeep.security

/**
 * Desktop device key — Passthrough implementation.
 *
 * Desktop platforms lack a universal, hardware-backed OS keystore abstraction
 * available to the JVM without fragile native bindings. Relying on a file-based
 * AES key provides no real protection against offline brute-force if the app
 * data directory is exfiltrated.
 *
 * **Threat Model Change:** Desktop security now relies on **high-entropy passwords**
 * (16+ chars, enforced by `PinConfig.desktop.kt`). The PBKDF2 salt is stored in
 * plaintext. If an attacker steals the app data, they still cannot brute-force
 * the KEK offline because the password has 100+ bits of entropy, making
 * PBKDF2-HMAC-SHA256(600k iterations) mathematically infeasible to crack.
 *
 * The `encrypt` and `decrypt` functions are identity functions (returning a copy
 * to prevent premature wiping of the original array by callers).
 */
actual object HardwareKeyStore {

    actual fun encrypt(plaintext: ByteArray): ByteArray {
        // Passthrough: return a copy so the caller's wipe() doesn't destroy the original.
        return plaintext.copyOf()
    }

    actual fun decrypt(encrypted: ByteArray): ByteArray {
        // Passthrough: return a copy so the caller's wipe() doesn't destroy the original.
        return encrypted.copyOf()
    }

    actual fun deleteKey() {
        // No-op. No hardware or file-based key exists to delete.
    }
}
