package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.ui.components.Numpad
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState
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
            when (event) {
                SetupPinEvent.NavigateToCreateWallet -> {
                    // PIN set, session open — no wallet exists yet, go create one.
                    navController.navigate(Screen.CreateWallet) {
                        popUpTo(0) { inclusive = true }
                    }
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
            verticalArrangement = if (state.isChoosingLength) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isChoosingLength) {
                    Text(
                        "Choose PIN length",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Select how many digits your PIN will have",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PinConfig.PIN_LENGTH_OPTIONS.forEach { length ->
                            PinLengthOption(
                                length = length,
                                onClick = { viewModel.selectPinLength(length) }
                            )
                        }
                    }
                } else {
                    Text(
                        if (state.isConfirming) "Confirm your PIN" else "Create a PIN",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (state.isConfirming) "Re-enter the same PIN" else "Enter a ${state.pinLength}-digit PIN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    PinDotsInline(
                        enteredLength = state.enteredPinLength,
                        pinLength = state.pinLength
                    )

                    if (state.isProcessing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
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
            }

            if (!state.isChoosingLength) {
                Numpad(
                    onDigitClick = { viewModel.addDigit(('0'.code + it).toChar()) },
                    onDeleteClick = { viewModel.removeDigit() },
                    isLocked = state.isLocked || state.isProcessing
                )
            }
        }
    }
}

@Composable
private fun PinLengthOption(
    length: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$length",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "digits",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreenEnter(
    navController: NavController,
    viewModel: EnterPinViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val passwordField = remember { SecureTextFieldState() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.securityMethod) {
        if (state.securityMethod == SecurityMethod.PASSWORD) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(state.errorMessage, state.isProcessing) {
        if (state.errorMessage != null || state.isProcessing) {
            passwordField.update("")
        }
    }

    DisposableEffect(Unit) {
        onDispose { passwordField.wipe() }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EnterPinEvent.NavigateToAccountsList -> {
                    navController.navigate(Screen.AccountsList(event.walletId)) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                EnterPinEvent.NavigateToCreateWallet -> {
                    // Session unlocked but no wallet exists yet (credential was set before
                    // wallet creation and the app was restarted) — go create one.
                    navController.navigate(Screen.CreateWallet) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is EnterPinEvent.NavigateToRecovery -> {
                    // Key material corrupted — re-setup the PIN (fresh DEK, new DB).
                    navController.navigate(Screen.SetupPin) {
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

    val isPassword = state.securityMethod == SecurityMethod.PASSWORD

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
            verticalArrangement = if (isPassword) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (isPassword) "Enter your password" else "Enter your PIN",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Unlock to access your wallet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (isPassword) {
                    OutlinedTextField(
                        value = passwordField.text,
                        onValueChange = {
                            passwordField.update(it)
                            viewModel.onPasswordInput(it)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .offset { IntOffset(shakeOffset.toInt(), 0) },
                        isError = state.errorMessage != null,
                        enabled = !state.isLocked && !state.isProcessing
                    )
                } else {
                    PinDotsInline(
                        enteredLength = state.enteredPinLength,
                        pinLength = state.pinLength,
                        modifier = Modifier.offset { IntOffset(shakeOffset.toInt(), 0) }
                    )
                }

                if (state.isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (state.isLocked) {
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

            if (isPassword) {
                Spacer(modifier = Modifier.height(24.dp))
                if (state.isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Button(
                        onClick = {
                            viewModel.submitPassword()
                        },
                        enabled = !state.isLocked && passwordField.text.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Unlock", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                Numpad(
                    onDigitClick = { viewModel.addDigit(('0'.code + it).toChar()) },
                    onDeleteClick = { viewModel.removeDigit() },
                    isLocked = state.isLocked || state.isProcessing
                )
            }
        }
    }
}

/** Inline row of filled/empty dots using Box + CircleShape. */
@Composable
internal fun PinDotsInline(
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
