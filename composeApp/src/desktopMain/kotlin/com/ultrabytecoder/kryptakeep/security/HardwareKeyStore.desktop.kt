package com.ultrabytecoder.kryptakeep.security

import com.ultrabytecoder.kryptakeep.security.hardware.DesktopOs
import com.ultrabytecoder.kryptakeep.security.hardware.FileBackend
import com.ultrabytecoder.kryptakeep.security.hardware.HardwareKeyBackend
import com.ultrabytecoder.kryptakeep.security.hardware.LinuxBackend
import com.ultrabytecoder.kryptakeep.security.hardware.MacOSBackend
import com.ultrabytecoder.kryptakeep.security.hardware.OsDetector
import com.ultrabytecoder.kryptakeep.security.hardware.WindowsBackend

/**
 * Desktop device key, dispatched to an OS-specific backend by [OsDetector]:
 *
 * - macOS: Secure Enclave P-256 (Keychain fallback) — [MacOSBackend].
 * - Windows: DPAPI-protected key — [WindowsBackend].
 * - Linux: desktop secret service (GNOME Keyring / KWallet) — [LinuxBackend],
 *   with a transparent [FileBackend] fallback when libsecret is unavailable.
 * - Other: [FileBackend].
 */
actual object HardwareKeyStore {

    private val backend: HardwareKeyBackend = selectBackend()

    private val lock = Any()

    actual fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray = synchronized(lock) {
        backend.encrypt(plaintext, aad)
    }

    actual fun decrypt(encrypted: ByteArray, aad: ByteArray): ByteArray = synchronized(lock) {
        try {
            backend.decrypt(encrypted, aad)
        } catch (e: HardwareKeyInvalidatedException) {
            throw e
        } catch (e: AesGcmAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw HardwareKeyInvalidatedException("Device key backend '${backend.id}' failed: ${e.message}")
        }
    }

    actual fun deleteKey() = synchronized(lock) {
        backend.deleteKey()
    }

    actual fun purgeCache() = synchronized(lock) {
        backend.purgeCache()
    }

    actual fun configureInstallId(id: ByteArray) {
        synchronized(lock) {
            (backend as? WindowsBackend)?.setInstallId(id)
        }
    }

    /** Identifier of the active backend (for the UI badge). */
    fun currentBackendId(): String = backend.id

    private fun selectBackend(): HardwareKeyBackend = when (OsDetector.current) {
        DesktopOs.MAC -> MacOSBackend()
        DesktopOs.WINDOWS -> WindowsBackend()
        DesktopOs.LINUX -> LinuxBackend()
        DesktopOs.OTHER -> FileBackend()
    }
}
