package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.Shield
import compose.icons.feathericons.X
import com.ultrabytecoder.kryptakeep.domain.repository.BiometricRepository
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.components.Numpad
import com.ultrabytecoder.kryptakeep.ui.viewmodel.EnterPinEvent
import com.ultrabytecoder.kryptakeep.ui.viewmodel.EnterPinViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SetupPinEvent
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SetupPinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreenSetup(
    navController: NavController,
    viewModel: SetupPinViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is SetupPinEvent.NavigateToAccountsList) {
                navController.navigate(Screen.AccountsList(event.walletId)) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    if (!state.isConfirming && !state.isProcessing) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(FeatherIcons.X, contentDescription = "Close")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (state.isConfirming) "Confirm your PIN" else "Create a PIN",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (state.isConfirming) "Re-enter the same PIN" else "Enter a ${PinConfig.LENGTH}-digit PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                PinDotsInline(
                    enteredLength = state.enteredPin.length,
                    pinLength = PinConfig.LENGTH
                )

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Numpad(
                onDigitClick = { viewModel.addDigit(it.toString()) },
                onDeleteClick = { viewModel.removeDigit() },
                isLocked = state.isLocked || state.isProcessing
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreenEnter(
    navController: NavController,
    viewModel: EnterPinViewModel,
    biometricRepository: BiometricRepository
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isBiometricEnabled by biometricRepository.isBiometricEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EnterPinEvent.NavigateToAccountsList -> {
                    navController.navigate(Screen.AccountsList(event.walletId)) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is EnterPinEvent.NavigateToRecovery -> {
                    // Blob corrupted — reset to wallet creation flow
                    navController.navigate(Screen.CreateWallet) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // Shake animation — triggers on each new shakeTriggerId
    val shakeAnimatable = remember { Animatable(0f) }
    LaunchedEffect(state.shakeTriggerId) {
        shakeAnimatable.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 250
                0f at 0
                -12f at 50
                12f at 100
                -8f at 150
                8f at 200
                0f at 250
            }
        )
    }
    val shakeOffset = shakeAnimatable.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Enter your PIN",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Unlock to access your wallet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Biometric re-trigger button (only shown when biometric is enabled)
                if (isBiometricEnabled) {
                    IconButton(
                        onClick = { viewModel.triggerBiometric() },
                        enabled = !state.isLocked && !state.isProcessing
                    ) {
                        Icon(
                            FeatherIcons.Shield,
                            contentDescription = "Use biometric unlock",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                PinDotsInline(
                    enteredLength = state.enteredPin.length,
                    pinLength = PinConfig.LENGTH,
                    modifier = Modifier.offset { IntOffset(shakeOffset.toInt(), 0) }
                )

                if (state.isLocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Too many attempts. Try again in ${state.lockSecondsRemaining}s",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Numpad(
                onDigitClick = { viewModel.addDigit(it.toString()) },
                onDeleteClick = { viewModel.removeDigit() },
                isLocked = state.isLocked || state.isProcessing
            )
        }
    }
}

/** Inline row of filled/empty dots using Box + CircleShape. */
@Composable
private fun PinDotsInline(
    enteredLength: Int,
    pinLength: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        repeat(pinLength) { index ->
            val filled = index < enteredLength
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
            )
        }
    }
}