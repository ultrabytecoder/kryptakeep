package com.ultrabytecoder.kryptakeep.domain.repository

import com.ultrabytecoder.kryptakeep.domain.service.BiometricService
import kotlinx.coroutines.flow.StateFlow

interface BiometricRepository {
    val isBiometricEnabled: StateFlow<Boolean>

    suspend fun enableBiometric(service: BiometricService): Boolean

    suspend fun authenticate(service: BiometricService): ByteArray?

    fun disableBiometric(service: BiometricService)
}