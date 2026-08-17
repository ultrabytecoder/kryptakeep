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
import compose.icons.feathericons.X
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SetPasswordEvent
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SetPasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    navController: NavController,
    viewModel: SetPasswordViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val passwordField = remember { SecureTextFieldState() }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose { passwordField.wipe() }
    }

    LaunchedEffect(state.isConfirming) {
        if (state.isConfirming) {
            passwordField.update("")
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SetPasswordEvent.NavigateToCreateWallet -> {
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
                    if (!state.isProcessing) {
                        IconButton(onClick = {
                            if (state.isConfirming) {
                                viewModel.onBack()
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(
                                if (state.isConfirming) FeatherIcons.ArrowLeft else FeatherIcons.X,
                                contentDescription = "Back"
                            )
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (state.isConfirming) "Confirm your password" else "Create a password",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (state.isConfirming) "Re-enter the same password" else "At least 12 characters with 3 character classes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = passwordField.text,
                onValueChange = {
                    passwordField.update(it)
                    viewModel.onInput(it)
                },
                label = { Text(if (state.isConfirming) "Confirm password" else "Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                isError = state.errorMessage != null || state.validationError != null,
                enabled = !state.isProcessing
            )

            if (state.validationError != null && !state.isConfirming) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = {
                        if (state.isConfirming) {
                            viewModel.onConfirm()
                        } else {
                            viewModel.onContinue()
                        }
                    },
                    enabled = !state.isConfirming || passwordField.text.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        if (state.isConfirming) "Set Password" else "Continue",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
