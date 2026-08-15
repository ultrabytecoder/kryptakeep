package com.ultrabytecoder.kryptakeep.security

import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import org.kotlincrypto.random.CryptoRand

/**
 * Encrypts/decrypts the wallet mnemonic.
 *
 * Key hierarchy (mirrors the DEK envelope, see [KeyManager]):
 *  - KEK = PBKDF2(PIN, salt, MNEMONIC_PBKDF2_ITERATIONS)
 *  - The salt is wrapped with the device hardware key ([HardwareKeyStore]) before
 *    storage, so offline PIN brute-force on the mnemonic requires the device —
 *    same protection as the DEK envelope.
 *  - Legacy installs stored the salt in plaintext: decryption transparently falls
 *    back to the raw blob on authentication failure ([MnemonicResult.wasLegacy]),
 *    so old wallets stay readable and callers can re-wrap the salt.
 */
object MnemonicCipher {

    /** Result of [decrypt]: the mnemonic bytes plus whether the salt was legacy
     * (plaintext) — the caller should then migrate to a hardware-wrapped salt. */
    data class MnemonicResult(val plain: ByteArray, val wasLegacy: Boolean)

    /** Fresh random salt for one mnemonic encryption. */
    fun newSalt(): ByteArray = CryptoRand.Default.nextBytes(ByteArray(PinConfig.SALT_SIZE))

    /** Wraps [salt] with the device hardware key for storage. */
    fun wrapSalt(salt: ByteArray): ByteArray = HardwareKeyStore.encrypt(salt)

    /**
     * Recovers the raw salt from its stored form (hardware-wrapped, or legacy
     * plaintext). Returns null when the hardware key is unavailable/invalidated.
     */
    fun unwrapSalt(stored: ByteArray): ByteArray? =
        try {
            HardwareKeyStore.decrypt(stored)
        } catch (e: AesGcmAuthenticationException) {
            // Legacy installs stored the salt plaintext; hardware decryption of
            // random bytes fails authentication — treat the blob as the salt.
            stored
        } catch (e: HardwareKeyInvalidatedException) {
            null
        }

    /**
     * Generates a fresh salt, derives the KEK from [pin] and encrypts [mnemonic].
     * Returns (encryptedBlob, storedSalt) — the caller stores `storedSalt`
     * (hardware-wrapped) next to `encryptedBlob`.
     */
    fun encrypt(mnemonic: ByteArray, pin: CharArray): Pair<ByteArray, ByteArray> {
        val salt = newSalt()
        var pinBytes: ByteArray? = null
        var kek: ByteArray? = null
        return try {
            val storedSalt = wrapSalt(salt)
            pinBytes = pin.toPinBytes()
            kek = Pbkdf2.derive(
                password = pinBytes,
                salt = salt,
                iterations = PinConfig.MNEMONIC_PBKDF2_ITERATIONS,
                derivedKeyLengthBytes = PinConfig.MNEMONIC_KEY_SIZE
            )
            val encrypted = AesGcm.encrypt(kek, mnemonic)
            encrypted to storedSalt
        } finally {
            salt.wipe()
            pinBytes?.wipe()
            kek?.wipe()
        }
    }

    /**
     * Derives the KEK from [pin] and [storedSalt] and decrypts [encrypted].
     *
     * @throws AesGcmAuthenticationException when the PIN is wrong or the blob was
     * tampered with — the caller must treat it as an authentication failure.
     * @throws HardwareKeyInvalidatedException when the hardware key is unavailable.
     */
    fun decrypt(encrypted: ByteArray, pin: CharArray, storedSalt: ByteArray): MnemonicResult {
        val salt = unwrapSalt(storedSalt) ?: throw HardwareKeyInvalidatedException("Hardware key unavailable")
        var pinBytes: ByteArray? = null
        var kek: ByteArray? = null
        return try {
            pinBytes = pin.toPinBytes()
            kek = Pbkdf2.derive(
                password = pinBytes,
                salt = salt,
                iterations = PinConfig.MNEMONIC_PBKDF2_ITERATIONS,
                derivedKeyLengthBytes = PinConfig.MNEMONIC_KEY_SIZE
            )
            MnemonicResult(AesGcm.decrypt(kek, encrypted), wasLegacy = salt === storedSalt)
        } finally {
            salt.wipe()
            pinBytes?.wipe()
            kek?.wipe()
        }
    }
}