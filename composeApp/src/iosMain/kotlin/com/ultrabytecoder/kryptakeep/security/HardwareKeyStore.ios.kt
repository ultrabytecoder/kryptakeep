package com.ultrabytecoder.kryptakeep.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrLabel
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnRef

/**
 * Secure Enclave ECC P-256 key (non-exportable, no user-presence requirement — the key
 * is usable without a biometric prompt). Encryption uses ECIES-AES-GCM (X9.63, cofactor,
 * variable IV) via SecKeyCreateEncryptedData/SecKeyCreateDecryptedData.
 *
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` binds the key to this device install —
 * the wrapped key material is unusable on another device.
 */
@OptIn(ExperimentalForeignApi::class)
actual object HardwareKeyStore {

    private const val KEY_LABEL = "kryptakeep.hw"

    private fun keyAttributes(): CFDictionaryRef? = memScoped {
        val accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            0u, // no user-presence flag: no biometric prompt
            null
        )
        val attrs = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        CFDictionaryAddValue(attrs, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
        CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        // CFBridgingRetain returns a +1 reference; the dictionary retains its own
        // copy on CFDictionaryAddValue, so release ours right after to avoid a leak.
        val keySize = CFBridgingRetain(256)
        CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, keySize)
        CFRelease(keySize)
        CFDictionaryAddValue(attrs, kSecAttrAccessControl, accessControl)
        CFRelease(accessControl)
        val label = CFBridgingRetain(KEY_LABEL)
        CFDictionaryAddValue(attrs, kSecAttrLabel, label)
        CFRelease(label)
        attrs
    }

    private fun findKey(): SecKeyRef? = memScoped {
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        CFDictionaryAddValue(query, kSecClass, kSecClassKey)
        CFDictionaryAddValue(query, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
        val label = CFBridgingRetain(KEY_LABEL)
        CFDictionaryAddValue(query, kSecAttrLabel, label)
        CFRelease(label)
        val returnRef = CFBridgingRetain(true)
        CFDictionaryAddValue(query, kSecReturnRef, returnRef)
        CFRelease(returnRef)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status == errSecSuccess) result.value as SecKeyRef? else null
    }

    private fun getOrCreateKey(): SecKeyRef? {
        findKey()?.let { return it }
        return memScoped {
            val error = alloc<CFErrorRefVar>()
            val attrs = keyAttributes()
            val key = SecKeyCreateRandomKey(attrs, error.ptr)
            attrs?.let { CFRelease(it) }
            if (key == null) {
                // Release the CFErrorRef so a failed creation does not leak (NEW-13).
                error.value?.let { CFRelease(it) }
            }
            key
        }
    }

    actual fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey()
            ?: throw IllegalStateException("Failed to create Secure Enclave key")
        var publicKey: SecKeyRef? = null
        return try {
            publicKey = SecKeyCopyPublicKey(key)
                ?: throw IllegalStateException("Failed to export Secure Enclave public key")
            memScoped {
                val error = alloc<CFErrorRefVar>()
                val plaintextData = CFDataCreate(kCFAllocatorDefault, plaintext.refTo(0), plaintext.size.toLong())
                val encryptedData = SecKeyCreateEncryptedData(
                    publicKey!!,
                    kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM,
                    plaintextData,
                    error.ptr
                )
                CFRelease(plaintextData)
                if (encryptedData == null) {
                    error.value?.let { CFRelease(it) }
                    throw IllegalStateException("Secure Enclave encryption failed")
                }
                val length = CFDataGetLength(encryptedData).toInt()
                val result = CFDataGetBytePtr(encryptedData)!!.readBytes(length)
                CFRelease(encryptedData)
                result
            }
        } finally {
            // +1 references from SecKeyCreateRandomKey/SecKeyCopyPublicKey (NEW-8).
            publicKey?.let { CFRelease(it) }
            CFRelease(key)
        }
    }

    actual fun decrypt(encrypted: ByteArray): ByteArray {
        val key = findKey()
            ?: throw HardwareKeyInvalidatedException("Secure Enclave key missing")

        return try {
            memScoped {
                val error = alloc<CFErrorRefVar>()
                val ciphertextData = CFDataCreate(kCFAllocatorDefault, encrypted.refTo(0), encrypted.size.toLong())
                val plaintextData = SecKeyCreateDecryptedData(
                    key,
                    kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM,
                    ciphertextData,
                    error.ptr
                )
                CFRelease(ciphertextData)
                if (plaintextData == null) {
                    error.value?.let { CFRelease(it) }
                    throw AesGcmAuthenticationException("Secure Enclave decryption failed")
                }
                val length = CFDataGetLength(plaintextData).toInt()
                val result = CFDataGetBytePtr(plaintextData)!!.readBytes(length)
                CFRelease(plaintextData)
                result
            }
        } finally {
            // +1 reference from SecItemCopyMatching in findKey() (NEW-8).
            CFRelease(key)
        }
    }

    actual fun deleteKey() {
        memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )
            CFDictionaryAddValue(query, kSecClass, kSecClassKey)
            CFDictionaryAddValue(query, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
            val label = CFBridgingRetain(KEY_LABEL)
            CFDictionaryAddValue(query, kSecAttrLabel, label)
            CFRelease(label)
            SecItemDelete(query)
            CFRelease(query)
        }
    }
}