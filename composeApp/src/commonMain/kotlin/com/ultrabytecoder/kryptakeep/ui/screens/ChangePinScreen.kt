package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod
import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.ui.components.Numpad
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState
import com.ultrabytecoder.kryptakeep.ui.viewmodel.ChangePinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(
    navController: NavController,
    viewModel: ChangePinViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val passwordField = remember { SecureTextFieldState() }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose { passwordField.wipe() }
    }

    LaunchedEffect(state.securityMethod) {
        if (state.securityMethod == SecurityMethod.PASSWORD) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(state.stage) {
        passwordField.update("")
    }

    LaunchedEffect(state.errorMessage, state.isProcessing) {
        if (state.errorMessage != null || state.isProcessing) {
            passwordField.update("")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ChangePinViewModel.Event.NavigateBack -> navController.popBackStack()
            }
        }
    }

    val isPassword = state.securityMethod == SecurityMethod.PASSWORD
    val credentialLabel = if (isPassword) "password" else "PIN"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change $credentialLabel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "Back")
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
            verticalArrangement = if (isPassword) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    when (state.stage) {
                        ChangePinViewModel.Stage.OldPin -> "Enter your current $credentialLabel"
                        ChangePinViewModel.Stage.NewPin -> "Choose a new $credentialLabel"
                        ChangePinViewModel.Stage.ConfirmNewPin -> "Confirm your new $credentialLabel"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Your recovery phrases are re-encrypted with the new $credentialLabel.",
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
                            .focusRequester(focusRequester),
                        isError = state.errorMessage != null,
                        enabled = !state.isProcessing
                    )
                } else {
                    PinDotsInline(
                        enteredLength = state.enteredLength,
                        pinLength = PinConfig.PIN_LENGTH
                    )
                }

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

            if (isPassword) {
                if (state.isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Button(
                        onClick = {
                            viewModel.submitPasswordStage()
                        },
                        enabled = passwordField.text.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Continue", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                Numpad(
                    onDigitClick = { viewModel.addDigit(('0'.code + it).toChar()) },
                    onDeleteClick = { viewModel.removeDigit() },
                    isLocked = state.isProcessing
                )
            }
        }
    }
}
