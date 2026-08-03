package com.ultrabytecoder.kryptakeep.domain.service

import kotlinx.cinterop.ExperimentalNativeApi
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateFlags
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.errSecAuthFailed
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccount
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData
import platform.darwin.CFTypeRefVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr

actual class BiometricService actual constructor(context: Any? = null) {

    private companion object {
        const val TOKEN_ACCOUNT = "biometric_token"
    }

    actual val isAvailable: Boolean
        get() {
            val context = LAContext()
            return context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error = null
            )
        }

    actual suspend fun promptAndEncrypt(data: ByteArray): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val context = LAContext()
            cont.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                "Enable biometric unlock",
                reply = { success, _ ->
                    if (success) {
                        val stored = storeToken(data)
                        if (!cont.isCompleted) cont.resume(stored)
                    } else {
                        if (!cont.isCompleted) cont.resume(false)
                    }
                }
            )
        }

    actual suspend fun promptAndDecrypt(): BiometricAuthResult =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val context = LAContext()
            cont.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                "Unlock Kryptakeep",
                reply = { success, _ ->
                    if (success) {
                        val result = retrieveToken(context)
                        if (!cont.isCompleted) cont.resume(result)
                    } else {
                        if (!cont.isCompleted) cont.resume(BiometricAuthResult.Cancelled)
                    }
                }
            )
        }

    actual fun clear() {
        val query = mapOf<Any?, Any>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to TOKEN_ACCOUNT
        )
        SecItemDelete(query)
    }

    private fun storeToken(data: ByteArray): Boolean {
        val deleteQuery = mapOf<Any?, Any>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to TOKEN_ACCOUNT
        )
        SecItemDelete(deleteQuery)

        val accessControl = SecAccessControlCreateWithFlags(
            allocator = platform.CoreFoundation.kCFAllocatorDefault,
            attrAccessible = platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            flags = SecAccessControlCreateFlags.kSecAccessControlBiometryCurrentSet,
            error = null
        ) ?: return false

        val tokenData = data.toNSData()

        val addQuery = mapOf<Any?, Any>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to TOKEN_ACCOUNT,
            kSecValueData to tokenData,
            kSecAttrAccessControl to accessControl
        )

        return SecItemAdd(addQuery, null) == errSecSuccess
    }

    @OptIn(ExperimentalNativeApi::class)
    private fun retrieveToken(context: LAContext): BiometricAuthResult = memScoped {
        val query = mapOf<Any?, Any>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to TOKEN_ACCOUNT,
            kSecReturnData to true,
            kSecUseAuthenticationContext to context
        )

        val resultPtr = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, resultPtr.ptr)

        return@memScoped when (status) {
            errSecSuccess -> {
                BiometricAuthResult.Success((resultPtr.value as NSData).toByteArray())
            }
            errSecAuthFailed -> BiometricAuthResult.KeyInvalidated
            errSecItemNotFound -> BiometricAuthResult.KeyInvalidated
            else -> BiometricAuthResult.Cancelled
        }
    }
}

@OptIn(ExperimentalNativeApi::class)
private fun ByteArray.toNSData(): NSData {
    val nsData = NSMutableData()
    if (isNotEmpty()) {
        nsData.appendBytes(toCValues().ptr, size.toULong())
    }
    return nsData
}

@OptIn(ExperimentalNativeApi::class)
private fun NSData.toByteArray(): ByteArray {
    val array = ByteArray(length.toInt())
    if (length > 0uL) {
        array.usePinned { pinned ->
            getBytes(pinned.addressOf(0), length)
        }
    }
    return array
}