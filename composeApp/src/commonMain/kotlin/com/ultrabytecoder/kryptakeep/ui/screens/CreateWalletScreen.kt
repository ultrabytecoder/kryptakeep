package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.MnemonicCode
import org.kotlincrypto.random.CryptoRand
import compose.icons.FeatherIcons
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateWalletViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(
    onWalletCreated: (walletId: Long) -> Unit,
    viewModel: CreateWalletViewModel
) {
    var walletName by remember { mutableStateOf("") }
    var mnemonic by remember { mutableStateOf("") }
    var mnemonicError by remember { mutableStateOf<String?>(null) }
    var passphraseError by remember { mutableStateOf<String?>(null) }

    var usePassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var passphraseConfirm by remember { mutableStateOf("") }
    var passphraseConfirmVisible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Wallet") }
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
                "Enter a name and your recovery mnemonic",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = walletName,
                onValueChange = { walletName = it },
                label = { Text("Wallet name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = mnemonic,
                onValueChange = {
                    mnemonic = it
                    mnemonicError = null
                },
                label = { Text("Mnemonic") },
                isError = mnemonicError != null,
                supportingText = mnemonicError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            TextButton(
                onClick = {
                    val entropy = CryptoRand.Default.nextBytes(ByteArray(32))
                    mnemonic = MnemonicCode.toMnemonics(entropy).joinToString(" ")
                    mnemonicError = null
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Generate new")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        usePassphrase = !usePassphrase
                        if (!usePassphrase) {
                            passphrase = ""
                            passphraseConfirm = ""
                            passphraseError = null
                        }
                    }
            ) {
                Checkbox(
                    checked = usePassphrase,
                    onCheckedChange = null
                )
                Text("Use a passphrase (advanced, optional)")
            }

            if (usePassphrase) {
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
                    value = passphrase,
                    onValueChange = { passphrase = it; passphraseError = null },
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
                    value = passphraseConfirm,
                    onValueChange = { passphraseConfirm = it; passphraseError = null },
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
                    mnemonicError = null
                    passphraseError = null

                    if (usePassphrase && passphrase != passphraseConfirm) {
                        passphraseError = "Passphrases do not match"
                        return@Button
                    }

                    val effectivePassphrase = if (usePassphrase) passphrase else ""

                    scope.launch {
                        when (val result = viewModel.createWallet(
                            name = walletName.trim(),
                            mnemonic = mnemonic.trim(),
                            passphrase = effectivePassphrase
                        )) {
                            is CreateWalletViewModel.Result.Success -> {
                                onWalletCreated(result.walletId)
                            }
                            is CreateWalletViewModel.Result.Error -> {
                                mnemonicError = result.message
                            }
                        }
                    }
                },
                enabled = walletName.isNotBlank()
                    && mnemonic.isNotBlank()
                    && (!usePassphrase || passphrase.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Wallet")
            }
        }
    }
}
