package com.ultrabytecoder.kryptakeep.security

import kotlin.io.encoding.Base64
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * F-1 regression: the desktop device key must PERSIST across provider
 * re-creation (app restart). The pre-fix build used a process-lifetime
 * in-memory key, so every restart failed GCM authentication on the stored
 * salt and forced recovery. Uses FileBackend directly — the OS-store backends
 * (DPAPI/Keychain/libsecret) are platform-specific and not exercisable here.
 */
class DesktopKeystoreProviderTest {

    private fun tempHome(): File {
        val dir = kotlin.io.path.createTempDirectory("kk-keystore-test").toFile()
        System.setProperty("user.home", dir.toString())
        return dir
    }

    @Test
    fun fileBackendKeyPersistsAcrossProviderRecreation() {
        val home = tempHome()
        try {
            val lock = Any()
            val first = DesktopKeystoreProvider(lock, com.ultrabytecoder.kryptakeep.security.hardware.FileBackend())
            val plaintext = byteArrayOf(1, 2, 3, 4, 5)
            val aad = byteArrayOf(9, 9)
            val blob = first.encrypt(plaintext, aad)

            // Fresh provider instance (simulates an app restart): the key file on
            // disk must decrypt what the previous instance encrypted.
            val second = DesktopKeystoreProvider(lock, com.ultrabytecoder.kryptakeep.security.hardware.FileBackend())
            val decrypted = second.decrypt(blob, aad)
            assertTrue(decrypted.contentEquals(plaintext), "persisted key must decrypt prior ciphertext")

            // Wrong AAD must fail authentication.
            assertFailsWith<AesGcmAuthenticationException> {
                second.decrypt(blob, byteArrayOf(0))
            }
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun missingKeyDecryptThrowsInvalidated() {
        val home = tempHome()
        try {
            val provider = DesktopKeystoreProvider(Any(), com.ultrabytecoder.kryptakeep.security.hardware.FileBackend())
            // No key file exists yet: decrypt must report the device key as
            // missing, never create one (which would mask corruption).
            assertFailsWith<HardwareKeyInvalidatedException> {
                provider.decrypt(Base64.Default.decode("AAAAAAAAAAAAAAAAAAAAAKSkpKSkpKSkpKSkpKSkpA=="), ByteArray(0))
            }
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun deleteKeyOrphansPriorCiphertext() {
        val home = tempHome()
        try {
            val lock = Any()
            val provider = DesktopKeystoreProvider(lock, com.ultrabytecoder.kryptakeep.security.hardware.FileBackend())
            val blob = provider.encrypt(byteArrayOf(7, 7, 7), ByteArray(0))
            provider.deleteBlobKey()
            // After deletion a fresh key is created lazily; old ciphertext must fail.
            assertFailsWith<Exception> {
                provider.decrypt(blob, ByteArray(0))
            }
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun fileBackendCreatesOwnerOnlyKeyFile() {
        val home = tempHome()
        try {
            val provider = DesktopKeystoreProvider(Any(), com.ultrabytecoder.kryptakeep.security.hardware.FileBackend())
            provider.encrypt(byteArrayOf(1), ByteArray(0))
            val keyFile = File(home, ".kryptakeep/.device_key")
            assertTrue(keyFile.exists(), "device key file must be created")
            if (!System.getProperty("os.name").lowercase().contains("windows")) {
                val perms = java.nio.file.Files.getPosixFilePermissions(keyFile.toPath())
                assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ), "key file must not be world-readable")
                assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ), "key file must not be group-readable")
            }
        } finally {
            home.deleteRecursively()
        }
    }
}
