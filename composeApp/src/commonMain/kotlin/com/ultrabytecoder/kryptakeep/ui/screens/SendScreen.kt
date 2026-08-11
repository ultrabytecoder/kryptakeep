package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ultrabytecoder.kryptakeep.ui.theme.AuroraPrimary
import com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams
import com.ultrabytecoder.kryptakeep.domain.model.FeeEstimation
import com.ultrabytecoder.kryptakeep.domain.model.FeePresets
import com.ultrabytecoder.kryptakeep.ui.util.formatFeeChipRate
import com.ultrabytecoder.kryptakeep.ui.util.formatFeeDetail
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

    LaunchedEffect(amount, address, selectedFeeMode, btcFeeText, ethPriorityText, ethMaxFeeText, trc20FeeText) {
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
    // For token accounts (TRC20/ERC20), fee is in the native chain currency,
    // so adding amount + fee produces a meaningless number.
    val isTokenAccount = accountVal?.type?.isToken == true
    val total = if (!isTokenAccount && parsedAmount != null && feeVal?.totalCost != null) {
        parsedAmount.add(feeVal.totalCost)
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
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            style = MaterialTheme.typography.headlineSmall,
                            color = AuroraPrimary
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
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp)
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

                    // Use the params actually applied by the provider (e.g. from Auto mode RPC query)
                    val feeDetailLines = formatFeeDetail(feeVal.appliedParams)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                    text = feeVal?.totalCost?.toPlainString() ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (feeDetailLines != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Column {
                                    feeDetailLines.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
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
}

private data class FeeChipData(
    val mode: FeeSelectionMode,
    val label: String,
    val params: CustomFeeParams?
)

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
    val isTrx = parentChain is com.ultrabytecoder.kryptakeep.domain.model.AccountType.Trx

    val chipModes = listOf(
        FeeChipData(FeeSelectionMode.Auto, "Auto", null),
        FeeChipData(FeeSelectionMode.Slow, "Slow", feePresets?.slow),
        FeeChipData(FeeSelectionMode.Medium, "Medium", feePresets?.medium),
        FeeChipData(FeeSelectionMode.Fast, "Fast", feePresets?.fast),
        FeeChipData(FeeSelectionMode.Custom, "Custom", null)
    )

    // Fee selection visibility per chain:
    //   * BTC            -> Slow, Medium, Fast, Custom   (Auto hidden)
    //   * ETH / TRC20   -> Auto, Slow, Medium, Fast, Custom
    //   * TON and others -> Auto only (presets unavailable)
    val hasPresets = feePresets != null
    val visibleChipModes = when {
        isBtc -> chipModes.filter { it.mode !is FeeSelectionMode.Auto }
        isEth || (isTrx && hasPresets) -> chipModes
        else -> listOf(chipModes[0]) // Auto only
    }

    Text(
        text = "Fee Selection",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleChipModes.forEach { chip ->
            val isSelected = selectedMode == chip.mode
            FilterChip(
                selected = isSelected,
                onClick = { onSelectMode(chip.mode) },
                label = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                        val rateText = formatFeeChipRate(chip.params)
                        if (rateText != null || chip.mode is FeeSelectionMode.Auto) {
                            Text(
                                text = rateText ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(16.dp)
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
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(16.dp)
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
                    isError = validationError != null,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            isTrx -> {
                val feeLimitSun = customTrc20FeeText.toLongOrNull() ?: 0L
                val feeTrxWhole = feeLimitSun / 1_000_000
                val feeTrxFrac = (feeLimitSun % 1_000_000).toString().padStart(6, '0')
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
                            text = "~${feeTrxWhole}.${feeTrxFrac} TRX",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

