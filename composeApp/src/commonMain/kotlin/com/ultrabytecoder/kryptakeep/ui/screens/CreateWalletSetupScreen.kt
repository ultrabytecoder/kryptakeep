package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.ultrabytecoder.kryptakeep.security.SecureMnemonicCode
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateWalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletSetupScreen(
    viewModel: CreateWalletViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var walletName by remember { mutableStateOf(viewModel.walletNameValue) }
    var mnemonicError by remember { mutableStateOf<String?>(null) }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    val createError by viewModel.createError.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()

    val mode by viewModel.mode.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()
    val useGesture by viewModel.useGesture.collectAsState()

    val mnemonicState = remember { SecureTextFieldState() }
    var usePassphrase by remember { mutableStateOf(false) }
    val passphraseState = remember { SecureTextFieldState() }
    var passphraseVisible by remember { mutableStateOf(false) }
    val passphraseConfirmState = remember { SecureTextFieldState() }
    var passphraseConfirmVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mnemonicState.wipe()
            passphraseState.wipe()
            passphraseConfirmState.wipe()
        }
    }

    // A stale error from a previous attempt is cleared when the screen is
    // entered (Way B retries must not show the old message).
    LaunchedEffect(Unit) {
        viewModel.clearCreateError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Wallet") },
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
                "Set up your wallet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (mode == CreateWalletViewModel.Mode.GENERATE_NEW)
                    "Generate a new recovery phrase"
                else
                    "Restore from an existing recovery phrase",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Mode selector lives at the top, above the form fields.
            SegmentedSingleChoice(
                options = listOf(
                    CreateWalletViewModel.Mode.GENERATE_NEW to "Generate new",
                    CreateWalletViewModel.Mode.RESTORE_EXISTING to "Restore existing"
                ),
                selected = mode,
                onSelected = { newMode ->
                    if (newMode != mode) {
                        // Wipe restore-mode local secrets when switching away.
                        if (mode == CreateWalletViewModel.Mode.RESTORE_EXISTING) {
                            mnemonicState.wipe()
                            passphraseState.wipe()
                            passphraseConfirmState.wipe()
                            mnemonicError = null
                            passphraseError = null
                            usePassphrase = false
                        }
                        viewModel.setMode(newMode)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = walletName,
                onValueChange = {
                    walletName = it
                    viewModel.setWalletName(it)
                },
                label = { Text("Wallet name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (mode == CreateWalletViewModel.Mode.GENERATE_NEW) {
                Text(
                    "Phrase length",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedSingleChoice(
                    options = SecureMnemonicCode.SUPPORTED_WORD_COUNTS.map { it to "$it words" },
                    selected = wordCount,
                    onSelected = { viewModel.setWordCount(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setUseGesture(!useGesture) }
                ) {
                    Checkbox(
                        checked = useGesture,
                        onCheckedChange = null
                    )
                    Text("Add extra entropy (draw a gesture)")
                }
                Text(
                    "Optional: your gesture is mixed with the system random entropy. " +
                        "It is never stored — you only need it once, right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = mnemonicState.text,
                    onValueChange = {
                        mnemonicState.update(it)
                        mnemonicError = null
                        viewModel.clearCreateError()
                    },
                    label = { Text("Mnemonic") },
                    isError = mnemonicError != null,
                    supportingText = mnemonicError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Restore mode keeps the passphrase inline so both the
                // mnemonic and passphrase are asked on a single screen.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            usePassphrase = !usePassphrase
                            if (!usePassphrase) {
                                passphraseState.wipe()
                                passphraseConfirmState.wipe()
                                passphraseError = null
                            }
                        }
                ) {
                    Checkbox(
                        checked = usePassphrase,
                        onCheckedChange = null
                    )
                    Text("I'm using a passphrase")
                }

                if (usePassphrase) {
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
            }

            Spacer(modifier = Modifier.weight(1f))

            if (mode == CreateWalletViewModel.Mode.GENERATE_NEW) {
                Button(
                    onClick = {
                        onNext()
                    },
                    enabled = walletName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next")
                }
            } else {
                createError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (usePassphrase && passphraseState.text != passphraseConfirmState.text) {
                            passphraseError = "Passphrases do not match"
                            return@Button
                        }
                        passphraseError = null
                        val mnemonicChars = mnemonicState.trimmedCopy()
                        val passphraseChars =
                            if (usePassphrase) passphraseState.toCharArray() else CharArray(0)
                        viewModel.setPassphrase(passphraseChars)
                        viewModel.createWalletFromMnemonic(mnemonicChars)
                        // The ViewModel owns its own copies (wiped in its
                        // finally block); scrub the UI's local buffers now
                        // that the secrets have been handed off.
                        mnemonicState.wipe()
                        passphraseState.wipe()
                        passphraseConfirmState.wipe()
                    },
                    enabled = walletName.isNotBlank()
                        && mnemonicState.text.isNotBlank()
                        && (!usePassphrase || passphraseState.text.isNotBlank())
                        && !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create Wallet")
                }
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
