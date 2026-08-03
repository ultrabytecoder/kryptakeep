package com.ultrabytecoder.kryptakeep.domain.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberBiometricService(): BiometricService {
    val context = LocalContext.current
    return remember(context) { BiometricService(context) }
}