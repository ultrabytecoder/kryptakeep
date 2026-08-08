package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.ultrabytecoder.kryptakeep.domain.model.FeePresets
import com.ultrabytecoder.kryptakeep.ui.viewmodel.FeeSelectionMode
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    navController: NavController,
    viewModel: SendViewModel
) {
    val account by viewModel.account.collectAsState()
    val fee by viewModel.fee.collectAsState()
    val feeError by viewModel.feeError.collectAsState()
    val feePresets by viewModel.feePresets.collectAsState()
    val selectedFeeMode by viewModel.selectedFeeMode.collectAsState()
    val customBtcFeeRate by viewModel.customBtcFeeRate.collectAsState()
    val customEthPriorityFee by viewModel.customEthPriorityFee.collectAsState()
    val customEthMaxFee by viewModel.customEthMaxFee.collectAsState()
    val customTrc20FeeLimit by viewModel.customTrc20FeeLimit.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var btcFeeText by remember { mutableStateOf(customBtcFeeRate.toString()) }
    var ethPriorityText by remember { mutableStateOf(customEthPriorityFee.toString()) }
    var ethMaxFeeText by remember { mutableStateOf(customEthMaxFee.toString()) }
    var trc20FeeText by remember { mutableStateOf(customTrc20FeeLimit.toString()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val launchScanner = rememberQrScannerLauncher { result ->
        if (result != null) {
            address = result
        }
    }

    val supportsFeeSelection = viewModel.supportsFeeSelection()

    LaunchedEffect(amount, selectedFeeMode, btcFeeText, ethPriorityText, ethMaxFeeText, trc20FeeText) {
        if (amount.isNotBlank()) {
            try {
                val parsed = BigDecimal.parseString(amount)
                delay(300)
                viewModel.estimateFee(parsed, address.takeIf { it.isNotBlank() })
            } catch (_: Exception) {
            }
        } else {
            viewModel.clearFee()
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
    val feeErrVal = feeError
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
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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

                // Fee selection section
                if (supportsFeeSelection) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FeeSelector(
                        selectedMode = selectedFeeMode,
                        onSelectMode = { viewModel.setSelectedFeeMode(it) },
                        feePresets = feePresets,
                        customBtcFeeText = btcFeeText,
                        onBtcFeeTextChange = { btcFeeText = it; viewModel.setCustomBtcFeeRate(it.toLongOrNull() ?: 0L) },
                        customEthPriorityText = ethPriorityText,
                        onEthPriorityTextChange = { ethPriorityText = it; viewModel.setCustomEthFees(it.toLongOrNull() ?: 0L, ethMaxFeeText.toLongOrNull() ?: 0L) },
                        customEthMaxFeeText = ethMaxFeeText,
                        onEthMaxFeeTextChange = { ethMaxFeeText = it; viewModel.setCustomEthFees(ethPriorityText.toLongOrNull() ?: 0L, it.toLongOrNull() ?: 0L) },
                        customTrc20FeeText = trc20FeeText,
                        onTrc20FeeTextChange = { trc20FeeText = it; viewModel.setCustomTrc20FeeLimit(it.toLongOrNull() ?: 0L) },
                        accountType = accountVal!!.type,
                        onValidate = { viewModel.validateCustomFee() },
                        validationError = validationError
                    )
                }

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

                if (feeErrVal != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = feeErrVal,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Send button pinned at bottom
            PaddingValues(16.dp).let { pv ->
                Button(
                    onClick = {
                        if (address.isNotBlank() && amount.isNotBlank()) {
                            // Validate custom fee before sending
                            if (selectedFeeMode is FeeSelectionMode.Custom && !viewModel.validateCustomFee()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please fix fee values")
                                }
                                return@Button
                            }
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
                        .height(56.dp)
                        .padding(pv),
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
}

@Composable
private fun FeeSelector(
    selectedMode: FeeSelectionMode,
    onSelectMode: (FeeSelectionMode) -> Unit,
    feePresets: FeePresets?,
    customBtcFeeText: String,
    onBtcFeeTextChange: (String) -> Unit,
    customEthPriorityText: String,
    onEthPriorityTextChange: (String) -> Unit,
    customEthMaxFeeText: String,
    onEthMaxFeeTextChange: (String) -> Unit,
    customTrc20FeeText: String,
    onTrc20FeeTextChange: (String) -> Unit,
    accountType: com.ultrabytecoder.kryptakeep.domain.model.AccountType,
    onValidate: () -> Boolean,
    validationError: String?
) {
    val parentChain = accountType.parentChain() ?: accountType
    val isBtc = parentChain is com.ultrabytecoder.kryptakeep.domain.model.AccountType.Btc
    val isEth = parentChain is com.ultrabytecoder.kryptakeep.domain.model.AccountType.Eth
    val isTrc20 = accountType is com.ultrabytecoder.kryptakeep.domain.model.AccountType.Trc20

    val chips = listOf(
        FeeSelectionMode.Auto to "Auto",
        FeeSelectionMode.Slow to "Slow",
        FeeSelectionMode.Medium to "Medium",
        FeeSelectionMode.Fast to "Fast",
        FeeSelectionMode.Custom to "Custom"
    )

    // Hide Custom chip for chains that don't support it
    val visibleChips = if (isBtc || isEth || isTrc20) chips else chips.take(4)

    Text(
        text = "Fee Selection",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleChips.forEach { (mode, label) ->
            val isSelected = selectedMode == mode
            FilterChip(
                selected = isSelected,
                onClick = { onSelectMode(mode) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Custom fee inputs
    if (selectedMode is FeeSelectionMode.Custom) {
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isBtc -> {
                OutlinedTextField(
                    value = customBtcFeeText,
                    onValueChange = onBtcFeeTextChange,
                    label = { Text("Fee Rate (sat/vB)") },
                    placeholder = { Text("10") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
            isEth -> {
                OutlinedTextField(
                    value = customEthPriorityText,
                    onValueChange = onEthPriorityTextChange,
                    label = { Text("Priority Fee (Gwei)") },
                    placeholder = { Text("25") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customEthMaxFeeText,
                    onValueChange = onEthMaxFeeTextChange,
                    label = { Text("Max Fee (Gwei)") },
                    placeholder = { Text("35") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationError != null
                )
            }
            isTrc20 -> {
                val feeLimitSun = customTrc20FeeText.toLongOrNull() ?: 0L
                val feeTrx = feeLimitSun / 1_000_000.0
                OutlinedTextField(
                    value = customTrc20FeeText,
                    onValueChange = onTrc20FeeTextChange,
                    label = { Text("Fee Limit (SUN)") },
                    placeholder = { Text("35000000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationError != null,
                    supportingText = {
                        Text(
                            text = "~${BigDecimal.parseString(feeTrx.toString()).toPlainString()} TRX",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

