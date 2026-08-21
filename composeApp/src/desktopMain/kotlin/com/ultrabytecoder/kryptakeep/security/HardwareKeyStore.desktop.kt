package com.ultrabytecoder.kryptakeep.security

import com.ultrabytecoder.kryptakeep.security.hardware.DesktopOs
import com.ultrabytecoder.kryptakeep.security.hardware.FileBackend
import com.ultrabytecoder.kryptakeep.security.hardware.HardwareKeyBackend
import com.ultrabytecoder.kryptakeep.security.hardware.MacOSBackend
import com.ultrabytecoder.kryptakeep.security.hardware.OsDetector
import com.ultrabytecoder.kryptakeep.security.hardware.WindowsBackend

/**
 * Desktop device key, dispatched to an OS-specific backend by [OsDetector]:
 *
 * - macOS: Secure Enclave P-256 (Keychain fallback) — [MacOSBackend] (follow-up commit).
 * - Windows: DPAPI-protected key — [WindowsBackend] (follow-up commit).
 * - Linux/other: [FileBackend] (libsecret backend — follow-up commit).
 *
 * Until the OS backends land, all platforms route to [FileBackend], preserving
 * the previous behavior exactly.
 */
actual object HardwareKeyStore {

    private val backend: HardwareKeyBackend = selectBackend()

    private val lock = Any()

    actual fun encrypt(plaintext: ByteArray): ByteArray = synchronized(lock) {
        backend.encrypt(plaintext)
    }

    actual fun decrypt(encrypted: ByteArray): ByteArray = synchronized(lock) {
        try {
            backend.decrypt(encrypted)
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

    /** Identifier of the active backend (for the UI badge). */
    fun currentBackendId(): String = backend.id

    private fun selectBackend(): HardwareKeyBackend = when (OsDetector.current) {
        DesktopOs.MAC -> MacOSBackend()
        DesktopOs.WINDOWS -> WindowsBackend()
        // LinuxBackend (libsecret) lands in a follow-up commit.
        DesktopOs.LINUX -> FileBackend()
        DesktopOs.OTHER -> FileBackend()
    }
}
