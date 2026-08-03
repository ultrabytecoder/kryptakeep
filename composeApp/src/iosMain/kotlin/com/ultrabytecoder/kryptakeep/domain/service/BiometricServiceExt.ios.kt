package com.ultrabytecoder.kryptakeep.domain.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberBiometricService(): BiometricService {
    return remember { BiometricService() }
}