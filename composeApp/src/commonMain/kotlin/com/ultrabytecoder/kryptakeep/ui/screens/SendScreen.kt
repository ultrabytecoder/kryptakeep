package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Camera
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    navController: NavController,
    viewModel: SendViewModel
) {
    val account by viewModel.account.collectAsState()
    val fee by viewModel.fee.collectAsState()
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val launchScanner = rememberQrScannerLauncher { result ->
        if (result != null) {
            address = result
        }
    }

    LaunchedEffect(amount) {
        if (amount.isNotBlank()) {
            try {
                val parsed = BigDecimal.parseString(amount)
                delay(500)
                viewModel.estimateFee(parsed)
            } catch (_: Exception) {
            }
        }
    }

    if (account == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Account not found")
        }
        return
    }
    val accountVal = account

    val parsedAmount = if (amount.isNotBlank()) {
        try { BigDecimal.parseString(amount) } catch (_: Exception) { null }
    } else null

    val feeVal = fee
    val total = if (parsedAmount != null && feeVal != null) {
        parsedAmount.add(feeVal)
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Available Balance",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = accountVal!!.amount,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Recipient Address") },
                    placeholder = { Text("Enter or paste address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { launchScanner() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        FeatherIcons.Camera,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            if (feeVal != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Network Fee",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = feeVal.toPlainString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (total != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = total.toPlainString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (address.isNotBlank() && amount.isNotBlank()) {
                        isLoading = true
                        scope.launch {
                            try {
                                val txid = viewModel.sendTransaction(address, BigDecimal.parseString(amount))
                                isLoading = false
                                navController.navigate(Screen.TransactionSent(txid)) {
                                    popUpTo(Screen.AccountsList::class) { inclusive = false }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                snackbarHostState.showSnackbar("Failed: ${e.message}")
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please fill in all fields")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send")
                }
            }
        }
    }
}
