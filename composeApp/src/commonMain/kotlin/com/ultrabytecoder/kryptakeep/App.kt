package com.ultrabytecoder.kryptakeep

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.screens.AccountDetailsScreen
import com.ultrabytecoder.kryptakeep.ui.screens.AccountsListScreen
import com.ultrabytecoder.kryptakeep.ui.screens.CreateAccountScreen
import com.ultrabytecoder.kryptakeep.ui.screens.AddTokenScreen
import com.ultrabytecoder.kryptakeep.ui.screens.CreateWalletScreen
import com.ultrabytecoder.kryptakeep.ui.screens.ExportMnemonicScreen
import com.ultrabytecoder.kryptakeep.ui.screens.ManageWalletsScreen
import com.ultrabytecoder.kryptakeep.ui.screens.PinScreenEnter
import com.ultrabytecoder.kryptakeep.ui.screens.PinScreenSetup
import com.ultrabytecoder.kryptakeep.ui.screens.SendScreen
import com.ultrabytecoder.kryptakeep.ui.screens.SettingsScreen
import com.ultrabytecoder.kryptakeep.ui.screens.CustomNodesScreen
import com.ultrabytecoder.kryptakeep.ui.screens.ChangePinScreen
import com.ultrabytecoder.kryptakeep.ui.screens.TransactionSentScreen
import com.ultrabytecoder.kryptakeep.ui.screens.TransactionDetailsScreen
import com.ultrabytecoder.kryptakeep.ui.screens.WelcomeScreen
import com.ultrabytecoder.kryptakeep.ui.theme.KryptaKeepTheme
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AccountDetailsViewModel
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncManager
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AccountsListViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateAccountViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AddTokenViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateWalletViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.ExportMnemonicViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.ManageWalletsViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SendViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SetupPinViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.EnterPinViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SettingsViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.TransactionDetailsViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CustomNodesViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.ChangePinViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.StartupViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.StartupState
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.provider.FiatQuoteProvider
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.EstimateFeeUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SendUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncAccountUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateAccountUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.AddTokenUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateTokenUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountAddressUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetMnemonicUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.DeleteWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.RenameWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SetupPinUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.ChangePinUseCase
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.security.SessionLockNotifier

@Composable
fun App() {
    KryptaKeepTheme {
        val navController = rememberNavController()

        // Session locked (app backgrounded, see SessionLockNotifier): return to the
        // startup wizard, which routes to the PIN unlock (or recovery) screen.
        LaunchedEffect(Unit) {
            SessionLockNotifier.locked.collect {
                // Guard against redundant navigations when the lock fires repeatedly
                // (e.g. rapid background/foreground): if we are already on the startup
                // screen, there is nothing to do (NEW-9).
                if (navController.currentDestination?.hasRoute(Screen.Startup::class) != true) {
                    navController.navigate(Screen.Startup) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Startup
        ) {
            composable<Screen.Startup> {
                val checkPinStatus: CheckPinStatusUseCase = koinInject()
                val viewModel = remember { StartupViewModel(checkPinStatus) }
                val currentState by viewModel.state.collectAsStateWithLifecycle()

                when (currentState) {
                    is StartupState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is StartupState.NeedsPinSetup -> {
                        LaunchedEffect(currentState) {
                            navController.navigate(Screen.Welcome) {
                                popUpTo(Screen.Startup) { inclusive = true }
                            }
                        }
                    }
                    is StartupState.NeedsPinUnlock -> {
                        LaunchedEffect(currentState) {
                            navController.navigate(Screen.EnterPin) {
                                popUpTo(Screen.Startup) { inclusive = true }
                            }
                        }
                    }
                }
            }
            composable<Screen.Welcome> {
                WelcomeScreen(navController)
            }
            composable<Screen.CreateWallet> {
                val createWallet: CreateWalletUseCase = koinInject()
                val viewModel = remember { CreateWalletViewModel(createWallet) }

                CreateWalletScreen(
                    onWalletCreated = { walletId ->
                        // The PIN is always set up BEFORE wallet creation (startup wizard),
                        // so the session is open and we can go straight to the main screen.
                        navController.navigate(Screen.AccountsList(walletId)) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    viewModel = viewModel
                )
            }
            composable<Screen.AccountsList> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.AccountsList>()
                val getAccounts: GetAccountsUseCase = koinInject()
                val getWallets: GetWalletsUseCase = koinInject()
                val syncUseCase: SyncUseCase = koinInject()
                val syncManager: SyncManager = koinInject()
                val settingsStorage: com.ultrabytecoder.kryptakeep.data.SettingsStorage = koinInject()
                val quoteProvider: FiatQuoteProvider = koinInject()
                val viewModel = remember(route.walletId) {
                    AccountsListViewModel(
                        route.walletId, getAccounts, getWallets, syncUseCase, syncManager,
                        settingsStorage, quoteProvider
                    )
                }
                AccountsListScreen(navController, viewModel)
            }
            composable<Screen.AccountDetails> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.AccountDetails>()
                val getAccounts: GetAccountsUseCase = koinInject()
                val getAccountAddress: GetAccountAddressUseCase = koinInject()
                val accountRepository: com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository = koinInject()
                val transactionRepository: TransactionRepository = koinInject()
                val settingsStorage: com.ultrabytecoder.kryptakeep.data.SettingsStorage = koinInject()
                val quoteProvider: FiatQuoteProvider = koinInject()
                val viewModel = remember(route.accountId, route.preselectedTokenId) {
                    AccountDetailsViewModel(
                        route.accountId, route.preselectedTokenId, getAccounts, getAccountAddress,
                        accountRepository, transactionRepository, settingsStorage, quoteProvider
                    )
                }
                AccountDetailsScreen(navController, viewModel)
            }
            composable<Screen.Send> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Send>()
                val getAccounts: GetAccountsUseCase = koinInject()
                val send: SendUseCase = koinInject()
                val estimateFee: EstimateFeeUseCase = koinInject()
                val syncAccount: SyncAccountUseCase = koinInject()
                val accountRepository: com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository = koinInject()
                val utxoRepository: com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository = koinInject()
                val transactionRepository: com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository = koinInject()
                val keyProvider: com.ultrabytecoder.kryptakeep.domain.service.KeyProvider = koinInject()
                val networkConfig: com.ultrabytecoder.kryptakeep.data.NetworkConfig = koinInject()
                val settingsStorage: com.ultrabytecoder.kryptakeep.data.SettingsStorage = koinInject()
                val viewModel = remember(route.accountId) {
                    SendViewModel(route.accountId, getAccounts, send, estimateFee, syncAccount,
                        accountRepository, utxoRepository, transactionRepository, keyProvider, networkConfig, settingsStorage)
                }
                SendScreen(navController, viewModel)
            }
            composable<Screen.TransactionSent> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.TransactionSent>()
                TransactionSentScreen(navController, route.txId)
            }
            composable<Screen.TransactionDetails> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.TransactionDetails>()
                val transactionRepository: TransactionRepository = koinInject()
                val getAccounts: GetAccountsUseCase = koinInject()
                val viewModel = remember(route.txId) {
                    TransactionDetailsViewModel(route.txId, transactionRepository, getAccounts)
                }
                TransactionDetailsScreen(navController, viewModel)
            }
            composable<Screen.CreateAccount> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.CreateAccount>()
                val createAccount: CreateAccountUseCase = koinInject()
                val createToken: CreateTokenUseCase = koinInject()
                val accountRepository: com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository = koinInject()
                val networkConfig: com.ultrabytecoder.kryptakeep.data.NetworkConfig = koinInject()
                val viewModel = remember(route.walletId) {
                    CreateAccountViewModel(route.walletId, createAccount, createToken, accountRepository, networkConfig)
                }
                CreateAccountScreen(navController, viewModel)
            }
            composable<Screen.AddToken> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.AddToken>()
                val addToken: AddTokenUseCase = koinInject()
                val accountRepository: com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository = koinInject()
                val networkConfig: com.ultrabytecoder.kryptakeep.data.NetworkConfig = koinInject()
                val viewModel = remember(route.walletId) {
                    AddTokenViewModel(route.walletId, addToken, accountRepository, networkConfig)
                }
                AddTokenScreen(
                    navController = navController,
                    viewModel = viewModel,
                    preselectedTokenAddress = route.preselectedTokenAddress,
                    preselectedTokenType = route.preselectedTokenType,
                    requireManualSelection = route.requireManualSelection
                )
            }
            composable<Screen.ExportMnemonic> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.ExportMnemonic>()
                val getMnemonic: GetMnemonicUseCase = koinInject()
                val verifyPin: VerifyPinUseCase = koinInject()
                val checkPinStatus: CheckPinStatusUseCase = koinInject()
                val viewModel = remember(route.walletId) {
                    ExportMnemonicViewModel(
                        route.walletId, getMnemonic, verifyPin, checkPinStatus
                    )
                }
                ExportMnemonicScreen(navController, viewModel)
            }
            composable<Screen.ManageWallets> {
                val getWallets: GetWalletsUseCase = koinInject()
                val deleteWallet: DeleteWalletUseCase = koinInject()
                val renameWallet: RenameWalletUseCase = koinInject()
                val viewModel = remember {
                    ManageWalletsViewModel(getWallets, deleteWallet, renameWallet)
                }
                ManageWalletsScreen(navController, viewModel)
            }
            composable<Screen.SetupPin> {
                val setupPin: SetupPinUseCase = koinInject()
                val viewModel = remember { SetupPinViewModel(setupPin) }
                PinScreenSetup(navController, viewModel)
            }
            composable<Screen.EnterPin> {
                val verifyPin: VerifyPinUseCase = koinInject()
                val getWallets: GetWalletsUseCase = koinInject()
                val syncUseCase: SyncUseCase = koinInject()
                val checkPinStatus: CheckPinStatusUseCase = koinInject()
                val viewModel = remember { EnterPinViewModel(verifyPin, getWallets, syncUseCase, checkPinStatus) }
                PinScreenEnter(navController, viewModel)
            }
            composable<Screen.Settings> {
                val settingsStorage: com.ultrabytecoder.kryptakeep.data.SettingsStorage = koinInject()
                val viewModel = remember { SettingsViewModel(settingsStorage) }
                SettingsScreen(navController, viewModel)
            }
            composable<Screen.ChangePin> {
                val changePin: ChangePinUseCase = koinInject()
                val viewModel = remember { ChangePinViewModel(changePin) }
                ChangePinScreen(navController, viewModel)
            }
            composable<Screen.CustomNodes> {
                val settingsStorage: com.ultrabytecoder.kryptakeep.data.SettingsStorage = koinInject()
                val networkConfig: com.ultrabytecoder.kryptakeep.data.NetworkConfig = koinInject(named("raw"))
                val viewModel = remember { CustomNodesViewModel(settingsStorage, networkConfig) }
                CustomNodesScreen(navController, viewModel)
            }
        }
    }
}
