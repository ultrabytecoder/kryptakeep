package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kryptakeep.composeapp.generated.resources.Res
import kryptakeep.composeapp.generated.resources.ic_btc
import kryptakeep.composeapp.generated.resources.ic_eth
import kryptakeep.composeapp.generated.resources.ic_trx
import kryptakeep.composeapp.generated.resources.ic_ton
import kryptakeep.composeapp.generated.resources.ic_usdc
import kryptakeep.composeapp.generated.resources.ic_link
import kryptakeep.composeapp.generated.resources.ic_weth
import kryptakeep.composeapp.generated.resources.ic_dai
import kryptakeep.composeapp.generated.resources.ic_usdt
import kryptakeep.composeapp.generated.resources.ic_wbtc
import kryptakeep.composeapp.generated.resources.ic_btt
import kryptakeep.composeapp.generated.resources.ic_token_trc20
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.data.TokenInfo
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateAccountViewModel
import com.ultrabytecoder.kryptakeep.domain.model.AccountType as DomainAccountType
import org.koin.compose.koinInject

private data class AccountTypeUi(
    val displayName: String,
    val tickerSymbol: String,
    val color: Color,
    val icon: DrawableResource
)

private fun buildAccountTypes(
    erc20Tokens: Map<String, TokenInfo>,
    trc20Tokens: Map<String, TokenInfo>
): Map<DomainAccountType, AccountTypeUi> = buildMap {
    put(DomainAccountType.Btc, AccountTypeUi("Bitcoin (BTC)", "BTC", Color(0xFFF7931A), Res.drawable.ic_btc))
    put(DomainAccountType.Eth, AccountTypeUi("Ethereum (ETH)",  "ETH", Color(0xFF627EEA), Res.drawable.ic_eth))
    put(DomainAccountType.Trx, AccountTypeUi("TRON (TRX)",  "TRX", Color(0xFFFF0013), Res.drawable.ic_trx))
    put(DomainAccountType.Ton, AccountTypeUi("TON (TON)",  "TON", Color(0xFF0098EA), Res.drawable.ic_ton))
    erc20Tokens.forEach { (symbol, info) ->
        val icon = when (symbol) {
            "USDC" -> Res.drawable.ic_usdc
            "LINK" -> Res.drawable.ic_link
            "WETH" -> Res.drawable.ic_weth
            "DAI" -> Res.drawable.ic_dai
            "USDT" -> Res.drawable.ic_usdt
            "WBTC" -> Res.drawable.ic_wbtc
            else -> Res.drawable.ic_eth
        }
        put(DomainAccountType.Erc20(info.address), AccountTypeUi("$symbol ETH", symbol, Color(0xFF8B9FE8), icon))
    }
    trc20Tokens.forEach { (symbol, info) ->
        val icon = when (symbol) {
            "USDT" -> Res.drawable.ic_usdt
            "USDC" -> Res.drawable.ic_usdc
            "BTT" -> Res.drawable.ic_btt
            "WETH" -> Res.drawable.ic_weth
            else -> Res.drawable.ic_token_trc20
        }
        put(DomainAccountType.Trc20(info.address), AccountTypeUi("$symbol TRX", symbol, Color(0xFFFF4D5A), icon))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
    navController: NavController,
    viewModel: CreateAccountViewModel
) {
    var selectedType by remember { mutableStateOf<DomainAccountType?>(null) }
    val scope = rememberCoroutineScope()
    val networkConfig: NetworkConfig = koinInject()
    val accountTypes = remember(networkConfig) {
        buildAccountTypes(networkConfig.erc20Tokens, networkConfig.trc20Tokens)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Account") },
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
                .padding(16.dp)
        ) {
            Text(
                "Select account type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Choose the type for your new account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(accountTypes.entries.toList()) { (domainType, accountTypeUi) ->
                    AccountTypeCard(
                        accountType = accountTypeUi,
                        isSelected = selectedType == domainType,
                        onClick = { selectedType = domainType }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val type = selectedType ?: return@Button
                    val ui = accountTypes[type]
                    val displayName = ui?.displayName ?: "Wallet"
                    val tickerSymbol = ui?.tickerSymbol ?: type.type
                    scope.launch {
                        viewModel.createAccount(displayName, type, tickerSymbol)
                        navController.popBackStack()
                    }
                },
                enabled = selectedType != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Account")
            }
        }
    }
}

@Composable
private fun AccountTypeCard(
    accountType: AccountTypeUi,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) accountType.color else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accountType.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(accountType.icon),
                    contentDescription = accountType.displayName,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = accountType.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
