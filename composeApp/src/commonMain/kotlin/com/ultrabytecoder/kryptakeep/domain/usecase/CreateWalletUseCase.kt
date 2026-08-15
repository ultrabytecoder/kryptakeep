package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.MnemonicCipher
import com.ultrabytecoder.kryptakeep.security.wipe
import fr.acinq.bitcoin.MnemonicCode

/**
 * Creates a wallet. Requires an already unlocked session (the PIN wizard runs first,
 * so the SQLCipher database is open before any wallet exists).
 *
 * master_seed is stored as a plain BLOB — the SQLCipher database itself provides
 * encryption at rest. The mnemonic, however, is wrapped with a KEK derived from the
 * user's PIN via PBKDF2 (independent of the database DEK), so a wrong PIN yields no
 * mnemonic even when the database is already unlocked, and the mnemonic survives
 * DB-level compromise only if the PIN is also known. The mnemonic PBKDF2 salt is
 * hardware-wrapped before storage ([MnemonicCipher]), so offline PIN brute-force
 * against the mnemonic requires the device key.
 */
class CreateWalletUseCase(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(
        name: String,
        mnemonic: CharArray,
        passphrase: CharArray = CharArray(0),
        pin: CharArray
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
        var encryptedMnemonic: ByteArray? = null
        var storedSalt: ByteArray? = null

        return try {
            MnemonicCode.validate(mnemonicStr)
            seed = MnemonicCode.toSeed(mnemonicStr, passphraseStr)
            mnemonicBytes = mnemonicStr.encodeToByteArray()
            val (encrypted, salt) = MnemonicCipher.encrypt(mnemonicBytes, pin)
            encryptedMnemonic = encrypted
            storedSalt = salt

            walletRepository.insertWallet(name, seed, encryptedMnemonic, storedSalt)
        } finally {
            seed?.wipe()
            mnemonicBytes?.wipe()
            encryptedMnemonic?.wipe()
            storedSalt?.wipe()
        }
    }
}