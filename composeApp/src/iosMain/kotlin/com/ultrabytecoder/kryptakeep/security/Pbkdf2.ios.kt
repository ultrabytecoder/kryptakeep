package com.ultrabytecoder.kryptakeep.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CCKeyDerivationPBKDF
import platform.CommonCrypto.kCCPBKDF2
import platform.CommonCrypto.kCCPRFHmacAlgSHA256
import platform.CommonCrypto.kCCSuccess

actual object Pbkdf2 {
    actual fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int
    ): ByteArray {
        val derivedKey = ByteArray(derivedKeyLengthBytes)
        val passwordBytes = password.encodeToByteArray()

        derivedKey.usePinned { derivedKeyPinned ->
            passwordBytes.usePinned { passwordPinned ->
                salt.usePinned { saltPinned ->
                    val passwordPtr = if (passwordBytes.isEmpty()) null else passwordPinned.addressOf(0)
                    val saltPtr = if (salt.isEmpty()) null else saltPinned.addressOf(0)

                    val result = CCKeyDerivationPBKDF(
                        algorithm = kCCPBKDF2,
                        password = passwordPtr,
                        passwordLen = passwordBytes.size.convert(),
                        salt = saltPtr,
                        saltLen = salt.size.convert(),
                        prf = kCCPRFHmacAlgSHA256,
                        rounds = iterations.convert(),
                        derivedKey = derivedKeyPinned.addressOf(0),
                        derivedKeyLen = derivedKeyLengthBytes.convert()
                    )

                    require(result == kCCSuccess) {
                        "CCKeyDerivationPBKDF failed with error code $result"
                    }
                }
            }
        }
        return derivedKey
    }
}