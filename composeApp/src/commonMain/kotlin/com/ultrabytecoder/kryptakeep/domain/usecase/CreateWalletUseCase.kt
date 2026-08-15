package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.gcHint
import com.ultrabytecoder.kryptakeep.security.wipe
import fr.acinq.bitcoin.MnemonicCode

/**
 * Creates a wallet. Requires an already unlocked session (the PIN wizard runs first,
 * so the SQLCipher database is open before any wallet exists).
 *
 * Both the master_seed and the mnemonic are wrapped with the device hardware key
 * (Android Keystore / iOS Secure Enclave, see
 * [com.ultrabytecoder.kryptakeep.security.SecretCipher]) before storage, on top of
 * the SQLCipher database encryption at rest (DEK wrapped by the PIN and the device
 * hardware key, see [com.ultrabytecoder.kryptakeep.security.KeyManager]): even a
 * decrypted database does not reveal the secrets.
 */
class CreateWalletUseCase(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(
        name: String,
        mnemonic: CharArray,
        passphrase: CharArray = CharArray(0)
    ): Long {
        var seed: ByteArray? = null
        var mnemonicBytes: ByteArray? = null

        return try {
            val derived = validateAndDerive(mnemonic, passphrase)
            seed = derived.first
            mnemonicBytes = derived.second

            walletRepository.insertWallet(name, seed, mnemonicBytes)
        } finally {
            seed?.wipe()
            mnemonicBytes?.wipe()
            // The String copies from validateAndDerive are unreachable by now;
            // nudge the collector to reclaim them sooner (they cannot be wiped).
            gcHint()
        }
    }

    /**
     * ACCEPTED RISK (NEW-11): the MnemonicCode library API only accepts String,
     * so the recovery phrase (and passphrase) must be materialized as immutable
     * Strings that cannot be wiped and persist until GC. Removing this would
     * require forking or wrapping the library to accept CharArray/ByteArray.
     *
     * The Strings are confined to this helper: they become unreachable the
     * moment it returns, i.e. before the slower database insert runs, which
     * keeps the exposure window as small as the library allows.
     */
    private fun validateAndDerive(
        mnemonic: CharArray,
        passphrase: CharArray
    ): Pair<ByteArray, ByteArray> {
        val mnemonicStr = mnemonic.concatToString()
        val passphraseStr = passphrase.concatToString()

        MnemonicCode.validate(mnemonicStr)
        val seed = MnemonicCode.toSeed(mnemonicStr, passphraseStr)
        val mnemonicBytes = mnemonicStr.encodeToByteArray()
        return seed to mnemonicBytes
    }
}