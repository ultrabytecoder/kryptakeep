package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.wipe
import fr.acinq.bitcoin.MnemonicCode

/**
 * Creates a wallet. Requires an already unlocked session (the PIN wizard runs first,
 * so the SQLCipher database is open before any wallet exists).
 *
 * Both master_seed and the mnemonic are stored as plain BLOBs — the SQLCipher
 * database itself provides encryption at rest (DEK wrapped by the PIN and the
 * device hardware key, see [com.ultrabytecoder.kryptakeep.security.KeyManager]).
 */
class CreateWalletUseCase(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(
        name: String,
        mnemonic: CharArray,
        passphrase: CharArray = CharArray(0)
    ): Long {
        // ACCEPTED RISK (NEW-11): the MnemonicCode library API only accepts String,
        // so the recovery phrase (and passphrase) must be materialized as immutable
        // Strings that cannot be wiped and persist until GC. The strings are confined
        // to this function's scope and dropped after validation/derivation, but a
        // memory-dump attacker could recover them. Removing this would require forking
        // or wrapping the library to accept CharArray/ByteArray directly.
        val mnemonicStr = mnemonic.concatToString()
        val passphraseStr = passphrase.concatToString()

        var seed: ByteArray? = null
        var mnemonicBytes: ByteArray? = null

        return try {
            MnemonicCode.validate(mnemonicStr)
            seed = MnemonicCode.toSeed(mnemonicStr, passphraseStr)
            mnemonicBytes = mnemonicStr.encodeToByteArray()

            walletRepository.insertWallet(name, seed, mnemonicBytes)
        } finally {
            seed?.wipe()
            mnemonicBytes?.wipe()
        }
    }
}