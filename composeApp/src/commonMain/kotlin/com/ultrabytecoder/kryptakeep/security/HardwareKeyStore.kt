package com.ultrabytecoder.kryptakeep.security

/**
 * Thrown when the device-bound hardware key (Android Keystore / iOS Secure Enclave)
 * is missing, invalidated, or unavailable. The wrapped key material can no longer
 * be decrypted — treat as key-material corruption and route to PIN re-setup.
 */
class HardwareKeyInvalidatedException(message: String) : Exception(message)

/**
 * Always-on, non-interactive hardware-backed encryption (no biometric prompt).
 *
 * - Android: Android Keystore AES-256-GCM key (`setUserAuthenticationRequired(false)`),
 *   backed by TEE/StrongBox where the SoC provides it.
 * - iOS: Secure Enclave ECC P-256 key (`kSecAttrTokenIDSecureEnclave`), ECIES-AES-GCM.
 * - Desktop: Passthrough implementation (returns a copy of the input). Desktop
 *   platforms lack a universal OS-backed keystore, so security relies entirely
 *   on the high-entropy password enforced by PinConfig; the salt is stored
 *   in plaintext and is not bound to the device.
 *
 * Purpose: bind the key hierarchy to the device. The PIN salt is encrypted here, so
 * deriving the KEK requires the hardware key — an attacker who extracts the app data
 * cannot brute-force the PIN offline, and the data is useless on another device.
 */
expect object HardwareKeyStore {

    /** Encrypts [plaintext] with the device key (creating the key on first use). */
    fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Decrypts [encrypted] with the device key.
     *
     * @throws HardwareKeyInvalidatedException when the device key is missing/invalidated.
     * @throws AesGcmAuthenticationException when the ciphertext fails authentication.
     */
    fun decrypt(encrypted: ByteArray): ByteArray

    /**
     * Deletes the device key. Used when wiping all key material (PIN re-setup /
     * recovery). The key is re-created lazily on the next [encrypt] call.
     */
    fun deleteKey()
}