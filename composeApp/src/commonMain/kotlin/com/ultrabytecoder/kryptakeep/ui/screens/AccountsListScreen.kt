package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.data.TokenInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AccountsListViewModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsListScreen(
    navController: NavController,
    viewModel: AccountsListViewModel
) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedWalletId by viewModel.selectedWalletId.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle(initialValue = null)
    val syncingAccounts by viewModel.syncingAccounts.collectAsStateWithLifecycle(initialValue = emptySet())
    val selectedWallet = wallets.find { it.id == selectedWalletId }

    var walletSelectorExpanded by remember { mutableStateOf(false) }

    val networkConfig: NetworkConfig = koinInject()
    val erc20IconMap = remember(networkConfig) { buildErc20IconMap(networkConfig.erc20Tokens) }
    val trc20IconMap = remember(networkConfig) { buildTrc20IconMap(networkConfig.trc20Tokens) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { walletSelectorExpanded = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedWallet?.name ?: "Wallet")
                            Icon(
                                FeatherIcons.ChevronDown,
                                contentDescription = "Select wallet",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = walletSelectorExpanded,
                            onDismissRequest = { walletSelectorExpanded = false }
                        ) {
                            wallets.forEach { wallet ->
                                DropdownMenuItem(
                                    text = { Text(wallet.name) },
                                    onClick = {
                                        walletSelectorExpanded = false
                                        navController.navigate(Screen.AccountsList(wallet.id)) {
                                            popUpTo(Screen.AccountsList::class) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    trailingIcon = {
                                        if (wallet.id == selectedWalletId) {
                                            Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    var moreMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(FeatherIcons.MoreVertical, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Mnemonic") },
                                onClick = {
                                    moreMenuExpanded = false
                                    navController.navigate(Screen.ExportMnemonic(selectedWalletId))
                                },
                                leadingIcon = { Icon(FeatherIcons.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage Wallets") },
                                onClick = {
                                    moreMenuExpanded = false
                                    navController.navigate(Screen.ManageWallets)
                                },
                                leadingIcon = { Icon(FeatherIcons.Briefcase, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    moreMenuExpanded = false
                                    navController.navigate(Screen.Settings)
                                },
                                leadingIcon = { Icon(FeatherIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.CreateAccount(selectedWalletId)) }) {
                        Icon(FeatherIcons.Plus, contentDescription = "Add Account")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (accounts == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val loadedAccounts = accounts!!
            if (loadedAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            FeatherIcons.Briefcase,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No accounts yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Add your first account to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = loadedAccounts,
                        key = { it.id }
                    ) { account ->
                        AccountItem(
                            account = account,
                            isSyncing = account.id in syncingAccounts,
                            erc20IconMap = erc20IconMap,
                            trc20IconMap = trc20IconMap,
                            onClick = {
                                navController.navigate(Screen.AccountDetails(account.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun buildErc20IconMap(tokens: Map<String, TokenInfo>): Map<String, DrawableResource> = buildMap {
    tokens.forEach { (symbol, info) ->
        val icon = when (symbol) {
            "USDC" -> Res.drawable.ic_usdc
            "LINK" -> Res.drawable.ic_link
            "WETH" -> Res.drawable.ic_weth
            "DAI" -> Res.drawable.ic_dai
            "USDT" -> Res.drawable.ic_usdt
            "WBTC" -> Res.drawable.ic_wbtc
            else -> Res.drawable.ic_eth
        }
        put(info.address, icon)
    }
}

private fun buildTrc20IconMap(tokens: Map<String, TokenInfo>): Map<String, DrawableResource> = buildMap {
    tokens.forEach { (symbol, info) ->
        val icon = when (symbol) {
            "USDT" -> Res.drawable.ic_usdt
            "USDC" -> Res.drawable.ic_usdc
            "BTT" -> Res.drawable.ic_btt
            "WETH" -> Res.drawable.ic_weth
            else -> Res.drawable.ic_token_trc20
        }
        put(info.address, icon)
    }
}

private fun AccountType.icon(
    erc20IconMap: Map<String, DrawableResource>,
    trc20IconMap: Map<String, DrawableResource>
): DrawableResource = when (this) {
    is AccountType.Btc -> Res.drawable.ic_btc
    is AccountType.Eth -> Res.drawable.ic_eth
    is AccountType.Trx -> Res.drawable.ic_trx
    is AccountType.Ton -> Res.drawable.ic_ton
    is AccountType.Erc20 -> erc20IconMap[tokenAddress] ?: Res.drawable.ic_eth
    is AccountType.Trc20 -> trc20IconMap[tokenAddress] ?: Res.drawable.ic_token_trc20
}

@Composable
fun AccountItem(
    account: AccountInfo,
    isSyncing: Boolean,
    erc20IconMap: Map<String, DrawableResource>,
    trc20IconMap: Map<String, DrawableResource>,
    onClick: () -> Unit
) {
    val backgroundColor = Color(0xFF627EEA)

    val infiniteTransition = rememberInfiniteTransition()
    val syncAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(account.type.icon(erc20IconMap, trc20IconMap)),
                        contentDescription = account.name,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${account.amount} ${account.symbol}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (account.address != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${account.address.take(8)}...${account.address.takeLast(6)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }

            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = syncAlpha))
                )
            }
        }
    }
}
