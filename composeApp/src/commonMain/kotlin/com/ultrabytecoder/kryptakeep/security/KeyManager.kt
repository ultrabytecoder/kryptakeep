package com.ultrabytecoder.kryptakeep.security

import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.kotlincrypto.random.CryptoRand

/**
 * Single atomic key-material envelope: wrapped DEK + salt + iterations,
 * serialized to one JSON blob and written with a single
 * [SettingsStorage.putString] call (commit()-backed), so a crash between writes
 * can never leave partial key material.
 */
@Serializable
private data class WrappedDekEnvelope(
    val version: Int,
    val salt: String,
    val wrapped: String,
    val iterations: Int
)

/**
 * Manages the database encryption key hierarchy (Envelope Encryption):
 *
 *  - [DEK] (Data Encryption Key, 32 random bytes) encrypts the SQLCipher database.
 *  - [KEK] (Key Encryption Key) = PBKDF2(credential, salt, 600k) wraps the DEK at rest.
 *  - The PBKDF2 **salt** is wrapped by [HardwareKeyStore] before storage:
 *    on Mobile this is the OS hardware key (Android Keystore / iOS Secure Enclave),
 *    which makes offline brute-force impossible; on Desktop it is a passthrough
 *    (the salt is stored in plaintext) and security relies on the high-entropy
 *    password enforced by [PinConfig].
 *  - The DEK wrap is authenticated with AES-GCM and bound (AAD) to the install ID
 *    and the wrap version, so a wrapped DEK from another install/version cannot
 *    be swapped in.
 *
 * The credential is set up first (startup wizard), so the DEK is wrapped by the
 * credential from the very first moment — a raw DEK never exists outside
 * [SessionManager]'s memory.
 *
 * The wrapped DEK is authenticated (AES-GCM), so a wrong credential fails the
 * unwrap with an authentication error — the unwrap itself is the verification.
 */
class KeyManager(
    private val settingsStorage: SettingsStorage
) {

    private companion object {
        const val KEY_WRAPPED_DEK = "dek_envelope"
        const val KEY_INSTALL_ID = "dek_install_id"
        const val WRAP_VERSION = 1
        const val DEK_SIZE = 32
        const val INSTALL_ID_SIZE = 16
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** True when credential key material (wrapped DEK + salt envelope) exists. */
    fun hasPinKeyMaterial(): Boolean = loadEnvelope() != null

    /**
     * Generates a brand-new DEK and wraps it with [pin] (fresh setup / recovery).
     * Caller owns the returned array.
     */
    fun generateAndWrapDek(pin: CharArray): ByteArray {
        val dek = CryptoRand.Default.nextBytes(ByteArray(DEK_SIZE))
        try {
            wrapDekWithPin(pin, dek)
        } catch (e: Exception) {
            dek.wipe()
            throw e
        }
        return dek
    }

    /**
     * Derives the KEK from [pin] and unwraps the DEK.
     * Returns null when the credential is wrong or the key material is missing/corrupt —
     * never throws for an authentication failure.
     *
     * @throws HardwareKeyInvalidatedException when the device hardware key is
     * missing/invalidated — the caller must route to recovery (credential re-setup).
     */
    fun unwrapDekWithPin(pin: CharArray): ByteArray? {
        val envelope = loadEnvelope() ?: return null

        val wrapped = try {
            Base64.Default.decode(envelope.wrapped)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val saltBlob = try {
            Base64.Default.decode(envelope.salt)
        } catch (e: IllegalArgumentException) {
            wrapped.wipe()
            return null
        }

        var pinBytes: ByteArray? = null
        var kek: ByteArray? = null
        var salt: ByteArray? = null
        var dek: ByteArray? = null
        return try {
            salt = try {
                HardwareKeyStore.decrypt(saltBlob)
            } catch (e: HardwareKeyInvalidatedException) {
                throw e
            } catch (e: AesGcmAuthenticationException) {
                throw HardwareKeyInvalidatedException("Hardware key mismatch with stored salt")
            } catch (e: Exception) {
                throw HardwareKeyInvalidatedException("Hardware key unavailable: ${e.message}")
            }
            pinBytes = pin.toPinBytes()
            kek = Pbkdf2.derive(
                password = pinBytes,
                salt = salt,
                iterations = envelope.iterations,
                derivedKeyLengthBytes = DEK_SIZE
            )
            dek = AesGcm.decrypt(kek, wrapped, wrapAad())
            dek
        } catch (e: AesGcmAuthenticationException) {
            null
        } finally {
            wrapped.wipe()
            saltBlob.wipe()
            pinBytes?.wipe()
            kek?.wipe()
            salt?.wipe()
        }
    }

    /**
     * Re-wraps an already-unwrapped [dek] with [newPin] (credential change). The caller
     * has already verified the old credential by unwrapping [dek] itself, so this skips
     * the second PBKDF2 derivation. Returns false when the wrap fails.
     * The caller keeps ownership of [dek] and must wipe it when done.
     */
    fun rewrapDekWithDek(dek: ByteArray, newPin: CharArray): Boolean {
        return try {
            wrapDekWithPin(newPin, dek)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Wipes all key material (recovery / reset). */
    fun deleteAll() {
        settingsStorage.remove(KEY_WRAPPED_DEK)
        // Remove the device hardware key too: the salt/DEK envelopes it protected
        // are gone, and a fresh key is created lazily on the next setup (iOS
        // Keychain items otherwise persist across app reinstalls).
        try {
            HardwareKeyStore.deleteKey()
        } catch (_: Exception) {
        }
        // Also drop the SecretCipher backend key (Desktop: random AES-256 key in
        // settings; Mobile: same hardware key as above, already deleted).
        try {
            secretCipherDeleteKey()
        } catch (_: Exception) {
        }
    }

    private fun wrapDekWithPin(pin: CharArray, dek: ByteArray) {
        var pinBytes: ByteArray? = null
        var kek: ByteArray? = null
        var wrapped: ByteArray? = null
        var salt: ByteArray? = null
        var hwSaltBlob: ByteArray? = null
        try {
            salt = CryptoRand.Default.nextBytes(ByteArray(PinConfig.SALT_SIZE))
            // Wrap the salt with the device key BEFORE storing anything:
            // a failure here aborts setup without leaving partial key material.
            hwSaltBlob = HardwareKeyStore.encrypt(salt)
            pinBytes = pin.toPinBytes()
            kek = Pbkdf2.derive(
                password = pinBytes,
                salt = salt,
                iterations = PinConfig.PBKDF2_ITERATIONS,
                derivedKeyLengthBytes = DEK_SIZE
            )
            wrapped = AesGcm.encrypt(kek, dek, wrapAad())
            val envelope = WrappedDekEnvelope(
                version = WRAP_VERSION,
                salt = Base64.Default.encode(hwSaltBlob),
                wrapped = Base64.Default.encode(wrapped),
                iterations = PinConfig.PBKDF2_ITERATIONS
            )
            // Single atomic write (commit()-backed) — a crash here leaves either
            // the old envelope or the new one, never a partial mix.
            settingsStorage.putString(KEY_WRAPPED_DEK, json.encodeToString(envelope))
        } finally {
            pinBytes?.wipe()
            kek?.wipe()
            wrapped?.wipe()
            salt?.wipe()
            hwSaltBlob?.wipe()
        }
    }

    /** Reads the envelope, or null when no key material exists. */
    private fun loadEnvelope(): WrappedDekEnvelope? {
        val stored = settingsStorage.getString(KEY_WRAPPED_DEK) ?: return null
        return try {
            val envelope = json.decodeFromString<WrappedDekEnvelope>(stored)
            if (envelope.version != WRAP_VERSION) null else envelope
        } catch (_: Exception) {
            null
        }
    }

    /** Stable per-install identifier generated on first use (not secret). */
    private fun installIdBytes(): ByteArray {
        val stored = settingsStorage.getString(KEY_INSTALL_ID)
        if (stored != null) {
            try {
                return Base64.Default.decode(stored)
            } catch (_: IllegalArgumentException) {
                // corrupt value — regenerate below
            }
        }
        val id = CryptoRand.Default.nextBytes(ByteArray(INSTALL_ID_SIZE))
        settingsStorage.putString(KEY_INSTALL_ID, Base64.Default.encode(id))
        return id
    }

    /** AAD binding the DEK wrap to this install and the wrap algorithm version. */
    private fun wrapAad(): ByteArray = installIdBytes() + byteArrayOf(WRAP_VERSION.toByte())
}
