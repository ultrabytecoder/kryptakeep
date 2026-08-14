package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.repository.BiometricRepository
import com.ultrabytecoder.kryptakeep.domain.service.BiometricAuthResult
import com.ultrabytecoder.kryptakeep.domain.service.BiometricService
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.kotlincrypto.random.CryptoRand

class BiometricRepositoryImpl(
    private val settingsStorage: SettingsStorage
) : BiometricRepository {

    private companion object {
        const val KEY_ENABLED = "is_biometric_enabled"
        const val BIOMETRIC_TIMEOUT_MS = 60_000L
    }

    private val _isBiometricEnabled = MutableStateFlow(false)
    override val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    init {
        _isBiometricEnabled.value = settingsStorage.getString(KEY_ENABLED) == "true"
    }

    override suspend fun enableBiometric(service: BiometricService): Boolean {
        if (!service.isAvailable) return false
        service.clear() // Clear any stale key/token before setup
        val token = CryptoRand.Default.nextBytes(ByteArray(32))
        return try {
            val success = withTimeoutOrNull(BIOMETRIC_TIMEOUT_MS) {
                service.promptAndEncrypt(token)
            } == true
            if (success) {
                settingsStorage.putString(KEY_ENABLED, "true")
                _isBiometricEnabled.value = true
            }
            success
        } finally {
            token.wipe()
        }
    }

    override suspend fun authenticate(service: BiometricService): ByteArray? {
        if (!service.isAvailable) {
            disableBiometric(service)
            return null
        }
        val result = withTimeoutOrNull(BIOMETRIC_TIMEOUT_MS) {
            service.promptAndDecrypt()
        }

        return when (result) {
            is BiometricAuthResult.Success -> result.token
            BiometricAuthResult.Cancelled -> null
            BiometricAuthResult.KeyInvalidated -> {
                disableBiometric(service)
                null
            }
            null -> null
        }
    }

    override fun disableBiometric(service: BiometricService) {
        clearEnabledFlag()
        service.clear()
    }

    private fun clearEnabledFlag() {
        settingsStorage.remove(KEY_ENABLED)
        _isBiometricEnabled.value = false
    }
}