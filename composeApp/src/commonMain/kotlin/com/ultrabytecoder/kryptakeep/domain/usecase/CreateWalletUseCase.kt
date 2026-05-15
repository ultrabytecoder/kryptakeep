package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.service.EncryptionService
import fr.acinq.bitcoin.MnemonicCode

class CreateWalletUseCase(
    private val walletRepository: WalletRepository,
    private val encryptionService: EncryptionService
) {
    suspend operator fun invoke(name: String, mnemonic: String): Long {
        MnemonicCode.validate(mnemonic)
        val seed = MnemonicCode.toSeed(mnemonic, "")
        val encryptedSeed = encryptionService.encrypt(seed)
        val encryptedMnemonic = encryptionService.encrypt(mnemonic.encodeToByteArray())
        return walletRepository.insertWallet(name, encryptedSeed, encryptedMnemonic)
    }
}
