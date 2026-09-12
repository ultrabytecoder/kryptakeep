package com.ultrabytecoder.kryptakeep.di

import com.ultrabytecoder.kryptakeep.data.DatabaseDriverFactory
import com.ultrabytecoder.kryptakeep.data.DatabaseProvider
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.data.PinRepositoryImpl
import com.ultrabytecoder.kryptakeep.data.RemoteFiatQuoteProvider
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.data.applyCustomNodes
import com.ultrabytecoder.kryptakeep.domain.provider.FiatQuoteProvider
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.domain.usecase.AddTokenUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.ChangePinUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateAccountUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateTokenUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.EstimateFeeUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountAddressUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetMnemonicUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.DeleteWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.RenameWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SendUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetSecurityMethodUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SetSecurityMethodUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SetupPinUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncAccountUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncManager
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.security.KeyManager
import com.ultrabytecoder.kryptakeep.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun appModule(networkConfig: NetworkConfig) = module {
    single<NetworkConfig>(named("raw")) { networkConfig }
    single<NetworkConfig> { get<NetworkConfig>(named("raw")).applyCustomNodes(get()) }

    // Security: envelope key management + lazy session (DB opens only after unlock)
    single { KeyManager(get()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { SessionManager(get(), get()) }
    single<DatabaseProvider> { get<SessionManager>() }

    single<AccountRepository> { com.ultrabytecoder.kryptakeep.data.AccountRepository(get()) }
    single<UtxoRepository> { com.ultrabytecoder.kryptakeep.data.UtxoRepository(get()) }
    single<TransactionRepository> { com.ultrabytecoder.kryptakeep.data.TransactionRepository(get()) }
    single<WalletRepository> { com.ultrabytecoder.kryptakeep.data.WalletRepository(get()) }
    single<KeyProvider> { com.ultrabytecoder.kryptakeep.data.KeyProviderImpl(get()) }

    factory { CreateWalletUseCase(get()) }
    factory { GetMnemonicUseCase(get()) }
    factory { GetAccountsUseCase(get()) }
    factory { CreateAccountUseCase(get(), get()) }
    factory { AddTokenUseCase(get()) }
    factory { CreateTokenUseCase(get(), get(), get()) }
    factory { EstimateFeeUseCase(get(), get(), get(), get(), get()) }
    factory { SendUseCase(get(), get(), get(), get(), get()) }
    factory { GetAccountAddressUseCase(get(), get(), get(), get(), get()) }
    factory { GetWalletsUseCase(get()) }
    factory { DeleteWalletUseCase(get(), get(), get(), get()) }
    factory { RenameWalletUseCase(get()) }
    single { SyncManager() }
    factory { SyncUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SyncAccountUseCase(get(), get(), get(), get(), get(), get()) }

    single<PinRepository> { PinRepositoryImpl(get(), get(), get(), get()) }
    factory { CheckPinStatusUseCase(get()) }
    factory { GetSecurityMethodUseCase(get()) }
    factory { SetSecurityMethodUseCase(get()) }
    factory { SetupPinUseCase(get()) }
    factory { VerifyPinUseCase(get()) }
    factory { ChangePinUseCase(get()) }

    single<FiatQuoteProvider> { RemoteFiatQuoteProvider(get()) }
}