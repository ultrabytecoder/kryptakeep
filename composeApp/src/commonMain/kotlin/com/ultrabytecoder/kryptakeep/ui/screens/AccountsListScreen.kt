package com.ultrabytecoder.kryptakeep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ultrabytecoder.kryptakeep.domain.model.AccountGroup
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AccountsListViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsListScreen(
    navController: NavController,
    viewModel: AccountsListViewModel
) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedWalletId by viewModel.selectedWalletId.collectAsStateWithLifecycle()
    val accountGroups by viewModel.accountGroups.collectAsStateWithLifecycle(initialValue = null)
    val syncingAccounts by viewModel.syncingAccounts.collectAsStateWithLifecycle(initialValue = emptySet())
    val expandedAccountIds by viewModel.expandedAccountIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val selectedWallet = wallets.find { it.id == selectedWalletId }

    var walletSelectorExpanded by remember { mutableStateOf(false) }

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
                                        viewModel.selectWallet(wallet.id)
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
        if (accountGroups == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val loadedGroups = accountGroups!!
            if (loadedGroups.isEmpty()) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = loadedGroups,
                        key = { it.parent.id }
                    ) { group ->
                        AccountGroupItem(
                            group = group,
                            isExpanded = group.parent.id in expandedAccountIds,
                            isSyncing = group.parent.id in syncingAccounts,
                            onToggleExpand = { viewModel.toggleExpanded(group.parent.id) },
                            onParentClick = {
                                navController.navigate(Screen.AccountDetails(group.parent.id))
                            },
                            onTokenClick = { token ->
                                navController.navigate(Screen.AccountDetails(group.parent.id, token.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountGroupItem(
    group: AccountGroup,
    isExpanded: Boolean,
    isSyncing: Boolean,
    onToggleExpand: () -> Unit,
    onParentClick: () -> Unit,
    onTokenClick: (AccountInfo) -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)
    val backgroundColor = group.parent.type.backgroundColor()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onParentClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Parent row
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
                        painter = painterResource(group.parent.type.nativeIcon()),
                        contentDescription = group.parent.name,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.parent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${group.parent.amount} ${group.parent.symbol}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // Chevron for expand/collapse (only if tokens exist)
                if (group.tokens.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = if (isExpanded) "Collapse" else "Expand") {
                                onToggleExpand()
                            }
                    ) {
                        Icon(
                            FeatherIcons.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(20.dp)
                                .graphicsLayer { rotationZ = rotation },
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Token rows (expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    group.tokens.forEach { token ->
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Row(
                            modifier = Modifier
                                .padding(start = 64.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
                                .fillMaxWidth()
                                .clickable { onTokenClick(token) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(token.type.tokenIcon()),
                                    contentDescription = token.symbol,
                                    modifier = Modifier.size(24.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = token.symbol,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${token.amount} ${token.symbol}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Syncing indicator
            if (isSyncing) {
                val infiniteTransition = rememberInfiniteTransition()
                val syncAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )
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

private fun AccountType.backgroundColor(): Color = when (this) {
    is AccountType.Btc -> Color(0xFFF7931A)
    is AccountType.Eth -> Color(0xFF627EEA)
    is AccountType.Trx -> Color(0xFFFF0013)
    is AccountType.Ton -> Color(0xFF0098EA)
    is AccountType.Erc20 -> Color(0xFF8B9FE8)
    is AccountType.Trc20 -> Color(0xFFFF4D5A)
    is AccountType.TonToken -> Color(0xFF0098EA)
}

private fun AccountType.nativeIcon(): DrawableResource = when (this) {
    is AccountType.Btc -> Res.drawable.ic_btc
    is AccountType.Eth -> Res.drawable.ic_eth
    is AccountType.Trx -> Res.drawable.ic_trx
    is AccountType.Ton -> Res.drawable.ic_ton
    else -> Res.drawable.ic_eth
}

private fun AccountType.tokenIcon(): DrawableResource = when (this) {
    is AccountType.Erc20 -> {
        when (tokenAddress.lowercase()) {
            "0x1c7d4b196cb0c7b01d743fbc6116a902379c7238".lowercase(),
            "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48".lowercase() -> Res.drawable.ic_usdc
            "0x779877a7b0d9e8603169ddbd7836e478b4624789".lowercase(),
            "0x514910771af9ca656af840dff83e8264ecf986ca".lowercase() -> Res.drawable.ic_link
            "0x7b79995e5f793a07bc00c21412e50ecae098e7f9".lowercase(),
            "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2".lowercase() -> Res.drawable.ic_weth
            "0xff34b3d4aee8ddcd6f9afffb6fe49bd371b8a357".lowercase(),
            "0x6b175474e89094c44da98b954eedeac495271d0f".lowercase() -> Res.drawable.ic_dai
            "0x7169d38820dfd117c3fa1f22a697dba58d90ba06".lowercase(),
            "0xdac17f958d2ee523a2206206994597c13d831ec7".lowercase() -> Res.drawable.ic_usdt
            "0x29f2d40b060cca9757c756d8244d569865662692".lowercase(),
            "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599".lowercase() -> Res.drawable.ic_wbtc
            else -> Res.drawable.ic_eth
        }
    }
    is AccountType.Trc20 -> {
        when (tokenAddress.lowercase()) {
            "txyzopyrdj2d9xrtbg411xzz3km5vkaebf".lowercase(),
            "tla0bzlzq5lm4rtf5g6q4c7q6j4jwzqy2r".lowercase() -> Res.drawable.ic_usdt
            "temvynqpntmqkpxp6wxtw2k7e4sm3crmwz".lowercase(),
            "te3l676d7vfa6z5zjz6w8q63zq5q7z5zjz".lowercase() -> Res.drawable.ic_usdc
            "tnuokl1ni8aoshffl1asca1gou9rxwazfn".lowercase() -> Res.drawable.ic_btt
            "txm9mxnegwad67jfme3ttk2mut3e69e9kn".lowercase() -> Res.drawable.ic_weth
            else -> Res.drawable.ic_token_trc20
        }
    }
    is AccountType.TonToken -> Res.drawable.ic_ton
    else -> Res.drawable.ic_eth
}