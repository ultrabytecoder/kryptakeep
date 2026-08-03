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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ultrabytecoder.kryptakeep.navigation.Screen
import com.ultrabytecoder.kryptakeep.ui.screens.AccountDetailsScreen
import com.ultrabytecoder.kryptakeep.ui.screens.AccountsListScreen
import com.ultrabytecoder.kryptakeep.ui.screens.CreateAccountScreen
import com.ultrabytecoder.kryptakeep.ui.screens.CreateWalletScreen
import com.ultrabytecoder.kryptakeep.ui.screens.ExportMnemonicScreen
import com.ultrabytecoder.kryptakeep.ui.screens.ManageWalletsScreen
import com.ultrabytecoder.kryptakeep.ui.screens.PinScreenEnter
import com.ultrabytecoder.kryptakeep.ui.screens.PinScreenSetup
import com.ultrabytecoder.kryptakeep.ui.screens.SendScreen
import com.ultrabytecoder.kryptakeep.ui.screens.TransactionSentScreen
import com.ultrabytecoder.kryptakeep.ui.theme.KryptaKeepTheme
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AccountDetailsViewModel
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncManager
import com.ultrabytecoder.kryptakeep.ui.viewmodel.AccountsListViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateAccountViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.CreateWalletViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.ExportMnemonicViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.ManageWalletsViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SendViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.SetupPinViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.EnterPinViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.StartupViewModel
import com.ultrabytecoder.kryptakeep.ui.viewmodel.StartupState
import org.koin.compose.koinInject
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.EstimateFeeUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SendUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncAccountUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateAccountUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountAddressUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetMnemonicUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.DeleteWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.RenameWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SetupPinUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository

@Composable
fun App() {
    KryptaKeepTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Screen.Startup
        ) {
            composable<Screen.Startup> {
                val getWallets: GetWalletsUseCase = koinInject()
                val syncUseCase: SyncUseCase = koinInject()
                val checkPinStatus: CheckPinStatusUseCase = koinInject()
                val viewModel = remember { StartupViewModel(getWallets, syncUseCase, checkPinStatus) }
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
                    is StartupState.NeedsWallet -> {
                        LaunchedEffect(currentState) {
                            navController.navigate(Screen.CreateWallet) {
                                popUpTo(Screen.Startup) { inclusive = true }
                            }
                        }
                    }
                    is StartupState.NeedsPinSetup -> {
                        LaunchedEffect(currentState) {
                            navController.navigate(Screen.SetupPin) {
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
            composable<Screen.CreateWallet> {
                val createWallet: CreateWalletUseCase = koinInject()
                val viewModel = remember { CreateWalletViewModel(createWallet) }
                CreateWalletScreen(navController, viewModel)
            }
            composable<Screen.AccountsList> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.AccountsList>()
                val getAccounts: GetAccountsUseCase = koinInject()
                val getWallets: GetWalletsUseCase = koinInject()
                val syncUseCase: SyncUseCase = koinInject()
                val syncManager: SyncManager = koinInject()
                val viewModel = remember(route.walletId) {
                    AccountsListViewModel(route.walletId, getAccounts, getWallets, syncUseCase, syncManager)
                }
                AccountsListScreen(navController, viewModel)
            }
            composable<Screen.AccountDetails> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.AccountDetails>()
                val getAccounts: GetAccountsUseCase = koinInject()
                val getAccountAddress: GetAccountAddressUseCase = koinInject()
                val transactionRepository: TransactionRepository = koinInject()
                val viewModel = remember(route.accountId) {
                    AccountDetailsViewModel(route.accountId, getAccounts, getAccountAddress, transactionRepository)
                }
                AccountDetailsScreen(navController, viewModel)
            }
            composable<Screen.Send> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Send>()
                val getAccounts: GetAccountsUseCase = koinInject()
                val send: SendUseCase = koinInject()
                val estimateFee: EstimateFeeUseCase = koinInject()
                val syncAccount: SyncAccountUseCase = koinInject()
                val viewModel = remember(route.accountId) {
                    SendViewModel(route.accountId, getAccounts, send, estimateFee, syncAccount)
                }
                SendScreen(navController, viewModel)
            }
            composable<Screen.TransactionSent> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.TransactionSent>()
                TransactionSentScreen(navController, route.txId)
            }
            composable<Screen.CreateAccount> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.CreateAccount>()
                val createAccount: CreateAccountUseCase = koinInject()
                val accountRepository: com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository = koinInject()
                val networkConfig: com.ultrabytecoder.kryptakeep.data.NetworkConfig = koinInject()
                val viewModel = remember(route.walletId) {
                    CreateAccountViewModel(route.walletId, createAccount, accountRepository, networkConfig)
                }
                CreateAccountScreen(navController, viewModel)
            }
            composable<Screen.ExportMnemonic> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.ExportMnemonic>()
                val getMnemonic: GetMnemonicUseCase = koinInject()
                val viewModel = remember(route.walletId) {
                    ExportMnemonicViewModel(route.walletId, getMnemonic)
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
                val getWallets: GetWalletsUseCase = koinInject()
                val syncUseCase: SyncUseCase = koinInject()
                val viewModel = remember { SetupPinViewModel(setupPin, getWallets, syncUseCase) }
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
        }
    }
}
