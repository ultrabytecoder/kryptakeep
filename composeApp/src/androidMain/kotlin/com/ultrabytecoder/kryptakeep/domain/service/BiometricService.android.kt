package com.ultrabytecoder.kryptakeep.domain.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

actual class BiometricService actual constructor(context: Any?) {

    private val activity: FragmentActivity = when (context) {
        is FragmentActivity -> context
        else -> throw IllegalArgumentException("BiometricService requires a FragmentActivity, got ${context?.javaClass?.simpleName}")
    }
    private val appContext: Context = activity.applicationContext

    private companion object {
        const val KEY_ALIAS = "kryptakeep_biometric_key"
        const val PREFS_NAME = "biometric_prefs"
        const val PREF_TOKEN = "enc_token"
        const val PREF_IV = "enc_iv"
    }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val prefs
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual val isAvailable: Boolean
        get() = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun getOrCreateSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun buildPromptInfo(title: String, subtitle: String): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    private fun classifyError(code: Int): BiometricAuthResult = when (code) {
        BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricAuthResult.KeyInvalidated
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAuthResult.KeyInvalidated
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> BiometricAuthResult.KeyInvalidated
        BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricAuthResult.KeyInvalidated
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricAuthResult.KeyInvalidated
        BiometricPrompt.ERROR_LOCKOUT -> BiometricAuthResult.Cancelled
        else -> BiometricAuthResult.Cancelled
    }

    actual suspend fun promptAndEncrypt(data: ByteArray): Boolean =
        suspendCancellableCoroutine { cont ->
            try {
                val cipher = Cipher.getInstance(
                    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
                )
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (!cont.isCompleted) cont.resume(false)
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            result.cryptoObject?.cipher?.let { c ->
                                val encrypted = c.doFinal(data)
                                val editor = prefs.edit()
                                editor.putString(PREF_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                                editor.putString(PREF_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
                                editor.apply()
                                if (!cont.isCompleted) cont.resume(true)
                            } ?: run {
                                if (!cont.isCompleted) cont.resume(false)
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // User keeps trying — wait for success or error
                        }
                    }
                )
                cont.invokeOnCancellation { prompt.cancelAuthentication() }
                prompt.authenticate(
                    buildPromptInfo("Enable Biometric Unlock", "Authenticate to enable biometrics"),
                    BiometricPrompt.CryptoObject(cipher)
                )
            } catch (e: Exception) {
                if (!cont.isCompleted) cont.resume(false)
            }
        }

    actual suspend fun promptAndDecrypt(): BiometricAuthResult =
        suspendCancellableCoroutine { cont ->
            try {
                val tokenB64 = prefs.getString(PREF_TOKEN, null)
                val ivB64 = prefs.getString(PREF_IV, null)
                if (tokenB64 == null || ivB64 == null) {
                    cont.resume(BiometricAuthResult.KeyInvalidated)
                    return@suspendCancellableCoroutine
                }

                val cipher = Cipher.getInstance(
                    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
                )
                val spec = GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            val result = classifyError(errorCode)
                            if (!cont.isCompleted) cont.resume(result)
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            result.cryptoObject?.cipher?.let { c ->
                                try {
                                    val decrypted = c.doFinal(Base64.decode(tokenB64, Base64.NO_WRAP))
                                    if (!cont.isCompleted) cont.resume(BiometricAuthResult.Success(decrypted))
                                } catch (e: Exception) {
                                    if (!cont.isCompleted) cont.resume(BiometricAuthResult.Cancelled)
                                }
                            } ?: run {
                                if (!cont.isCompleted) cont.resume(BiometricAuthResult.Cancelled)
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // User keeps trying — do nothing
                        }
                    }
                )
                cont.invokeOnCancellation { prompt.cancelAuthentication() }
                prompt.authenticate(
                    buildPromptInfo("Unlock Kryptakeep", "Use biometrics to unlock"),
                    BiometricPrompt.CryptoObject(cipher)
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                clear()
                if (!cont.isCompleted) cont.resume(BiometricAuthResult.KeyInvalidated)
            } catch (e: Exception) {
                if (!cont.isCompleted) cont.resume(BiometricAuthResult.Cancelled)
            }
        }

    actual fun clear() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
        prefs.edit().clear().apply()
    }
}