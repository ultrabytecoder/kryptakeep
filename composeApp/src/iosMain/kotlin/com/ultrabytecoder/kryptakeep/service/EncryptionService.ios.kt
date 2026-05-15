package com.ultrabytecoder.kryptakeep.service

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.errSecSuccess
import platform.CommonCrypto.CCAlgorithm
import platform.CommonCrypto.CCOperation
import platform.CommonCrypto.kCCAlgorithmAES
import platform.CommonCrypto.kCCModeGCM
import platform.CommonCrypto.kCCSizeAES256
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.darwin.CFTypeRefVar
import platform.posix.arc4random_buf
import platform.posix.memcpy
import platform.CommonCrypto.CCCryptorCreateWithMode
import platform.CommonCrypto.CCCryptorGCMAddIV
import platform.CommonCrypto.CCCryptorGCMFinal
import platform.CommonCrypto.CCCryptorUpdate
import platform.CommonCrypto.ccNoPadding
import platform.CommonCrypto.CCCryptorRelease
import platform.CommonCrypto.CCCryptorGCMDecrypt
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class EncryptionService actual constructor(context: Any?) {

    private companion object {
        const val KEY_ALIAS = "kryptakeep.encryption_key"
        const val KEY_SIZE = 32
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 16
    }

    private val aesKey: ByteArray by lazy { getOrCreateKey() }

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    private fun getOrCreateKey(): ByteArray {
        val existingKey = queryKeychain()
        if (existingKey != null) return existingKey

        val newKey = ByteArray(KEY_SIZE).also {
            arc4random_buf(it.refTo(0), KEY_SIZE.toULong())
        }
        storeKeychain(newKey)
        return newKey
    }

    @ExperimentalForeignApi::class
    private fun queryKeychain(): ByteArray? {
        memScoped {
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to KEY_ALIAS,
                kSecReturnData to true,
                kSecMatchLimit to kSecMatchLimitOne
            )

            val resultPtr = alloc<CFTypeRefVar>()

            val status = SecItemCopyMatching(
                query.toCFDictionary(),
                resultPtr.ptr
            )

            if (status == errSecSuccess) {
                val nsData = resultPtr.value as NSData
                return ByteArray(nsData.length.toInt()).also { bytes ->
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                    }
                }
            }
            return null
        }
    }

    @ExperimentalForeignApi::class
    private fun storeKeychain(key: ByteArray) {
        memScoped {
            val keyData = NSData.create(
                bytes = key.refTo(0),
                length = key.size.toULong()
            )

            val attributes = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to KEY_ALIAS,
                kSecValueData to keyData,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            )

            SecItemAdd(attributes.toCFDictionary(), null)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun encrypt(plainData: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also {
            arc4random_buf(it.refTo(0), GCM_IV_LENGTH.toULong())
        }

        val ciphertext = ByteArray(plainData.size)
        val tag = ByteArray(GCM_TAG_LENGTH)

        val cryptorRef = kotlinx.cinterop.alloc<kotlinx.cinterop.CPointerVar<platform.CommonCrypto.CCCryptor?>>()
        try {
            CCCryptorCreateWithMode(
                0u, // kCCEncrypt = 0
                kCCModeGCM,
                kCCAlgorithmAES,
                ccNoPadding,
                null,
                aesKey.refTo(0),
                kCCSizeAES256.toULong(),
                null, // unused for GCM
                0u,
                0u,
                0u,
                cryptorRef.ptr
            )

            CCCryptorGCMAddIV(cryptorRef.value!!, iv.refTo(0), GCM_IV_LENGTH.toULong())

            var bytesProcessed: UInt = 0u
            CCCryptorUpdate(
                cryptorRef.value!!,
                plainData.refTo(0),
                plainData.size.toULong(),
                ciphertext.refTo(0),
                ciphertext.size.toULong(),
                kotlinx.cinterop.cValueOf(bytesProcessed)
            )

            CCCryptorGCMFinal(cryptorRef.value!!, tag.refTo(0), GCM_TAG_LENGTH.toULong())
        } finally {
            if (cryptorRef.value != null) {
                CCCryptorRelease(cryptorRef.value!!)
            }
        }

        return iv + ciphertext + tag
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun decrypt(encryptedData: ByteArray): ByteArray {
        require(encryptedData.size > GCM_IV_LENGTH + GCM_TAG_LENGTH) { "Encrypted data too short" }

        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(
            GCM_IV_LENGTH,
            encryptedData.size - GCM_TAG_LENGTH
        )
        val tag = encryptedData.copyOfRange(
            encryptedData.size - GCM_TAG_LENGTH,
            encryptedData.size
        )

        val plaintext = ByteArray(ciphertext.size)

        val cryptorRef = kotlinx.cinterop.alloc<kotlinx.cinterop.CPointerVar<platform.CommonCrypto.CCCryptor?>>()
        try {
            CCCryptorCreateWithMode(
                1u, // kCCDecrypt = 1
                kCCModeGCM,
                kCCAlgorithmAES,
                ccNoPadding,
                null,
                aesKey.refTo(0),
                kCCSizeAES256.toULong(),
                null,
                0u,
                0u,
                0u,
                cryptorRef.ptr
            )

            CCCryptorGCMAddIV(cryptorRef.value!!, iv.refTo(0), GCM_IV_LENGTH.toULong())

            var bytesProcessed: UInt = 0u
            CCCryptorGCMDecrypt(
                cryptorRef.value!!,
                ciphertext.refTo(0),
                ciphertext.size.toULong(),
                plaintext.refTo(0),
                kotlinx.cinterop.cValueOf(bytesProcessed)
            )

            CCCryptorGCMFinal(cryptorRef.value!!, tag.refTo(0), GCM_TAG_LENGTH.toULong())
        } finally {
            if (cryptorRef.value != null) {
                CCCryptorRelease(cryptorRef.value!!)
            }
        }

        return plaintext
    }
}
