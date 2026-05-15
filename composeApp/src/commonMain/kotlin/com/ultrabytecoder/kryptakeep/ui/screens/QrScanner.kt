package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.runtime.Composable

@Composable
expect fun rememberQrScannerLauncher(onResult: (String?) -> Unit): () -> Unit
