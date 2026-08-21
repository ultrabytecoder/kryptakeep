package com.ultrabytecoder.kryptakeep.security.hardware

import com.ultrabytecoder.kryptakeep.data.appDataDir
import com.ultrabytecoder.kryptakeep.data.restrictFileToOwner
import com.ultrabytecoder.kryptakeep.security.AesGcmAuthenticationException
import com.ultrabytecoder.kryptakeep.security.HardwareKeyInvalidatedException
import com.ultrabytecoder.kryptakeep.security.wipe
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * File-backed device key: a random AES-256 key stored in
 * `<user.home>/.kryptakeep/.device_key` with owner-only permissions
 * (0600 on POSIX).
 *
 * This is the baseline backend (and the fallback for OSes without a
 * dedicated backend). It binds the key hierarchy to the app data directory —
 * the same threat-model position as Android Keystore, minus the hardware
 * backing. The key file is created lazily on first [encrypt].
 */
class FileBackend : HardwareKeyBackend {

    override val id: String = "file"

    private companion object {
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }

    private val baseDir = appDataDir()
    private val keyFile = File(baseDir, ".device_key")

    private val keyLock = Any()

    private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
        if (!keyFile.exists()) {
            baseDir.mkdirs()
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            val encoded = key.encoded
            try {
                // Create the file with owner-only permissions BEFORE writing the
                // key bytes, so there is no window where the key is readable
                // with umask-default permissions.
                createOwnerOnlyFile(keyFile)
                keyFile.writeBytes(encoded)
            } finally {
                encoded.wipe()
            }
        }
        val encoded = keyFile.readBytes()
        javax.crypto.spec.SecretKeySpec(encoded, "AES").also {
            encoded.wipe()
        }
    }

    private fun createOwnerOnlyFile(file: File) {
        if (OsDetector.isWindows) {
            file.createNewFile()
            return
        }
        try {
            Files.createFile(
                file.toPath(),
                PosixFilePermissions.asFileAttribute(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            )
        } catch (_: UnsupportedOperationException) {
            file.createNewFile()
            restrictFileToOwner(file)
        } catch (_: FileAlreadyExistsException) {
            restrictFileToOwner(file)
        }
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        if (!keyFile.exists()) {
            throw HardwareKeyInvalidatedException("Device hardware key missing")
        }
        require(encrypted.size > GCM_IV_LENGTH) { "Encrypted data too short" }
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw AesGcmAuthenticationException("Hardware GCM authentication failed")
        }
    }

    override fun deleteKey() {
        keyFile.delete()
    }
}
