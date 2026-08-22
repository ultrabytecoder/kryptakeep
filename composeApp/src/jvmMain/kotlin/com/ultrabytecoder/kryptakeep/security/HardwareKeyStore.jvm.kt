package com.ultrabytecoder.kryptakeep.security

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM device key.
 *
 * The key comes from [KeystoreProvider]: on Android that is the Android
 * Keystore (hardware-backed, the key never leaves the Keystore); on the
 * desktop target there is no Keystore, so the provider reports "no Keystore"
 * and an in-memory AES-256 key for the process lifetime is used instead. The
 * in-memory fallback preserves the security semantics the tests exercise
 * (a stable key, GCM authentication, AAD binding, and a missing-key error
 * after [deleteKey]) without any Android API.
 *
 * Concurrency: [keyLock] is the SINGLE lock for the whole object — it guards
 * the provider resolution, the fallback key, AND is passed into the provider so
 * the provider's internal operations use the same monitor. This eliminates any
 * nested-lock (lock-ordering) hazard between [HardwareKeyStore] and
 * [KeystoreProvider]. [provider] is a retryable holder (NOT `by lazy`) so a
 * transient provider-construction failure is not cached permanently.
 */
actual object HardwareKeyStore {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private val keyLock = Any()

    @Volatile
    private var providerHolder: KeystoreProvider? = null

    @Volatile
    private var fallbackKey: ByteArray? = null

    private val random = SecureRandom()

    /**
     * Resolves the platform [KeystoreProvider], constructing it on first use.
     * The holder is retried on each miss (unlike `by lazy`, which would cache a
     * failed construction forever), so a transient failure does not permanently
     * disable the hardware key for the process.
     *
     * Synchronized on [keyLock] so concurrent first-use cannot construct two
     * providers (one would be orphaned). [keyLock] is reentrant, so this is safe
     * to call from paths that already hold [keyLock] (e.g. [encrypt],
     * [resolveKeyForDecrypt]). The provider is constructed with [keyLock] so its
     * internal operations synchronize on the same monitor (no nested locks).
     */
    private fun provider(): KeystoreProvider = synchronized(keyLock) {
        providerHolder ?: createKeystoreProvider(keyLock).also { providerHolder = it }
    }

    /**
     * Returns the live fallback key, creating it on first use. Caller must hold
     * [keyLock]. Returns the live array reference; [SecretKeySpec] copies the
     * bytes, so wiping [fallbackKey] later does not affect already-created specs.
     */
    private fun getOrCreateFallbackKey(): ByteArray {
        return fallbackKey ?: ByteArray(32).also {
            random.nextBytes(it)
            fallbackKey = it
        }
    }

    /**
     * Resolves the active key for decryption WITHOUT creating a fallback key.
     * Returns the hardware key if present, the existing fallback key if one is
     * live, or null when no key exists (the caller then reports "missing").
     * The whole resolve is under [keyLock] so the "is there a key?" decision and
     * the key it returns are consistent (no TOCTOU).
     */
    private fun resolveKeyForDecrypt(): SecretKey? = synchronized(keyLock) {
        provider().getOrCreateKey()
            ?: fallbackKey?.let { SecretKeySpec(it, "AES") }
    }

    actual fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val key = synchronized(keyLock) {
            provider().getOrCreateKey() ?: SecretKeySpec(getOrCreateFallbackKey(), "AES")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    actual fun decrypt(encrypted: ByteArray, aad: ByteArray): ByteArray {
        val key = resolveKeyForDecrypt()
            ?: throw HardwareKeyInvalidatedException("Device hardware key missing")
        require(encrypted.size > GCM_IV_LENGTH) { "Encrypted data too short" }
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw AesGcmAuthenticationException("Hardware GCM authentication failed")
        }
    }

    actual fun deleteKey() {
        // Atomic: wipe the fallback key AND delete the hardware key under the
        // same [keyLock] so a concurrent encrypt() cannot generate a fresh
        // fallback key in the gap (which provider().deleteKey() would then
        // orphan). Reentrant — provider()/provider().deleteKey() re-acquire the
        // same [keyLock] without deadlocking.
        synchronized(keyLock) {
            fallbackKey?.wipe()
            fallbackKey = null
            provider().deleteKey()
        }
    }

    actual fun purgeCache() {
        // Wipes the in-process copy of the device key. On Android the key never
        // leaves the Keystore (nothing to purge); on the JVM target the
        // in-memory fallback key IS the cache, so it is wiped here. The next
        // encrypt/decrypt re-creates a fresh fallback key, which is the intended
        // "session is over" behavior (old ciphertext then fails GCM auth).
        synchronized(keyLock) {
            fallbackKey?.wipe()
            fallbackKey = null
        }
    }

    actual fun configureInstallId(id: ByteArray) {
        // No-op on the JVM target: the Android Keystore key is already
        // device-bound, and the in-memory fallback has no OS store to bind.
        // Install-ID binding on this target is provided at the AES-GCM layer by
        // KeyManager.wrapAad() (the AAD includes the install ID), not here.
    }
}

/**
 * Selects the platform [KeystoreProvider]. Android returns the Android Keystore
 * implementation; the desktop target uses the base implementation, which reports
 * "no Keystore" and routes [HardwareKeyStore] to the in-memory fallback.
 *
 * [lock] is the caller's monitor, passed in so the provider synchronizes on the
 * SAME lock as [HardwareKeyStore] (single-lock design — no nested-lock hazard).
 */
internal expect fun createKeystoreProvider(lock: Any): KeystoreProvider

/**
 * Platform hook for the hardware-backed key. Android subclasses this with the
 * Android Keystore; the desktop target uses the base implementation (all
 * methods report "no Keystore"), which routes [HardwareKeyStore] to the
 * in-memory fallback.
 *
 * All methods synchronize on [lock] (the monitor supplied by
 * [HardwareKeyStore]), so the provider and its caller share one lock.
 */
internal open class KeystoreProvider(protected val lock: Any) {

    /** Returns the hardware-backed key, creating it on first use; null when no Keystore exists. */
    open fun getOrCreateKey(): SecretKey? = null

    /** True when a hardware-backed key exists, false when it does not, null when there is no Keystore. */
    open fun hasKey(): Boolean? = null

    /** Deletes the hardware-backed key; no-op when no Keystore exists. */
    open fun deleteKey() {}
}
