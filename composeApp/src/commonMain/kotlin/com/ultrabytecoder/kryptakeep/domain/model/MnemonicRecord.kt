package com.ultrabytecoder.kryptakeep.domain.model

/**
 * Stored mnemonic material for a wallet: the AES-GCM ciphertext (wrapped with a
 * PIN-derived KEK, independent of the database DEK) and the PBKDF2 salt used to
 * derive that KEK.
 */
data class MnemonicRecord(
    val encryptedMnemonic: ByteArray?,
    val mnemonicSalt: ByteArray?
)