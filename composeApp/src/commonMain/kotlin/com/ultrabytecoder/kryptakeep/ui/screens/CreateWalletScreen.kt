package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.MnemonicCode
import kotlin.random.Random
import androidx.navigation.NavController
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateWalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(
    navController: NavController,
    viewModel: CreateWalletViewModel
) {
    var walletName by remember { mutableStateOf("") }
    var mnemonic by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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
                    errorMessage = null
                },
                label = { Text("Mnemonic") },
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            TextButton(
                onClick = {
                    val entropy = Random.Default.nextBytes(16)
                    mnemonic = MnemonicCode.toMnemonics(entropy).joinToString(" ")
                    errorMessage = null
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Generate new")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    errorMessage = null
                    scope.launch {
                        when (val result = viewModel.createWallet(walletName.trim(), mnemonic.trim())) {
                            is CreateWalletViewModel.Result.Success -> {
                                navController.navigate(Screen.AccountsList(result.walletId)) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            is CreateWalletViewModel.Result.Error -> {
                                errorMessage = result.message
                            }
                        }
                    }
                },
                enabled = walletName.isNotBlank() && mnemonic.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Wallet")
            }
        }
    }
}
