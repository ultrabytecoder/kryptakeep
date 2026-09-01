package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.SecureMnemonicCode
import com.ultrabytecoder.kryptakeep.security.toPinBytes
import com.ultrabytecoder.kryptakeep.security.wipe

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
 *
 * The recovery phrase never materializes as an immutable String: validation and
 * seed derivation run on [CharArray]s through [SecureMnemonicCode] (F-7).
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
        }
    }

    private fun validateAndDerive(
        mnemonic: CharArray,
        passphrase: CharArray
    ): Pair<ByteArray, ByteArray> {
        SecureMnemonicCode.validate(mnemonic)
        val seed = SecureMnemonicCode.toSeed(mnemonic, passphrase)
        val mnemonicBytes = mnemonic.toPinBytes()
        return seed to mnemonicBytes
    }
}
