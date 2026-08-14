package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.service.EncryptionService
import fr.acinq.bitcoin.MnemonicCode

class CreateWalletUseCase(
    private val walletRepository: WalletRepository,
    private val encryptionService: EncryptionService
) {
    suspend operator fun invoke(
        name: String,
        mnemonic: CharArray,
        passphrase: CharArray = CharArray(0)
    ): Long {
        // Convert to strings ONLY for the MnemonicCode library (immutable Strings cannot be wiped)
        val mnemonicStr = mnemonic.concatToString()
        val passphraseStr = passphrase.concatToString()

        var seed: ByteArray? = null
        var mnemonicBytes: ByteArray? = null

        return try {
            MnemonicCode.validate(mnemonicStr)
            seed = MnemonicCode.toSeed(mnemonicStr, passphraseStr)
            mnemonicBytes = mnemonicStr.encodeToByteArray()

            val encryptedSeed = encryptionService.encrypt(seed)
            val encryptedMnemonic = encryptionService.encrypt(mnemonicBytes)
            walletRepository.insertWallet(name, encryptedSeed, encryptedMnemonic)
        } finally {
            seed?.wipe()
            mnemonicBytes?.wipe()
        }
    }
}