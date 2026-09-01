package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateWalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassphraseScreen(
    viewModel: CreateWalletViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    // Seeded from the ViewModel so a back-navigation (e.g. to redraw the
    // gesture) cannot reset the choice and silently drop a configured
    // passphrase.
    var usePassphrase by remember { mutableStateOf(viewModel.hasPassphrase) }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    val passphraseState = remember { SecureTextFieldState() }
    var passphraseVisible by remember { mutableStateOf(false) }
    val passphraseConfirmState = remember { SecureTextFieldState() }
    var passphraseConfirmVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            passphraseState.wipe()
            passphraseConfirmState.wipe()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passphrase") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
        ) {
            Text(
                "Do you want to add an additional layer of protection using a passphrase?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))

            SegmentedSingleChoice(
                options = listOf(
                    false to "No, skip",
                    true to "Yes, add passphrase"
                ),
                selected = usePassphrase,
                onSelected = {
                    usePassphrase = it
                    if (!it) {
                        passphraseState.wipe()
                        passphraseConfirmState.wipe()
                        passphraseError = null
                    }
                }
            )

            if (usePassphrase) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Important: the passphrase is NOT stored and cannot be recovered.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "If you forget it, your wallet is permanently unrecoverable, even with the correct mnemonic. " +
                                "A small typo creates a completely different wallet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = passphraseState.text,
                    onValueChange = { passphraseState.update(it); passphraseError = null },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = if (passphraseVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                            Icon(
                                imageVector = if (passphraseVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = if (passphraseVisible) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    isError = passphraseError != null,
                    supportingText = passphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = passphraseConfirmState.text,
                    onValueChange = { passphraseConfirmState.update(it); passphraseError = null },
                    label = { Text("Confirm passphrase") },
                    singleLine = true,
                    visualTransformation = if (passphraseConfirmVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passphraseConfirmVisible = !passphraseConfirmVisible }) {
                            Icon(
                                imageVector = if (passphraseConfirmVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = if (passphraseConfirmVisible) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    isError = passphraseError != null,
                    supportingText = passphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (usePassphrase) {
                        if (passphraseState.text != passphraseConfirmState.text) {
                            passphraseError = "Passphrases do not match"
                            return@Button
                        }
                        if (passphraseState.text.isBlank()) {
                            passphraseError = "Passphrase cannot be empty"
                            return@Button
                        }
                        passphraseError = null
                        viewModel.setPassphrase(passphraseState.toCharArray())
                    } else {
                        viewModel.setPassphrase(CharArray(0))
                    }
                    onNext()
                },
                enabled = !usePassphrase || passphraseState.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun <T> SegmentedSingleChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelected(value) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
