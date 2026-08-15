package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.AesGcmAuthenticationException
import com.ultrabytecoder.kryptakeep.security.HardwareKeyInvalidatedException
import com.ultrabytecoder.kryptakeep.security.MnemonicCipher
import com.ultrabytecoder.kryptakeep.security.wipe

class GetMnemonicUseCase(
    private val walletRepository: WalletRepository
) {
    /**
     * Returns the stored mnemonic as a [CharArray] (wrapped with a PIN-derived KEK,
     * independent of the SQLCipher DEK). Returns null when the PIN is wrong, when the
     * mnemonic was never stored, or when the record is missing/corrupt.
     * The caller owns the returned array and must wipe it when done.
     */
    suspend operator fun invoke(walletId: Long, pin: CharArray): CharArray? {
        val record = walletRepository.getMnemonicRecord(walletId) ?: return null
        val encrypted = record.encryptedMnemonic ?: return null
        val storedSalt = record.mnemonicSalt ?: return null

        var plain: ByteArray? = null
        var mnemonic: CharArray? = null
        return try {
            val result = MnemonicCipher.decrypt(encrypted, pin, storedSalt)
            plain = result.plain
            mnemonic = CharArray(plain.size) { plain[it].toInt().toChar() }
            if (result.wasLegacy) {
                migrateLegacySalt(walletId, mnemonic, pin)
            }
            mnemonic
        } catch (e: AesGcmAuthenticationException) {
            null
        } catch (e: HardwareKeyInvalidatedException) {
            null
        } finally {
            plain?.wipe()
        }
    }

    /**
     * Wallets created before the salt became hardware-wrapped stored a plaintext
     * salt. On a successful decrypt we transparently re-wrap it, so the mnemonic
     * gets the same hardware binding as newly created wallets.
     */
    private suspend fun migrateLegacySalt(walletId: Long, mnemonic: CharArray, pin: CharArray) {
        var mnemonicBytes: ByteArray? = null
        var encrypted: ByteArray? = null
        var storedSalt: ByteArray? = null
        try {
            // Convert the CharArray straight to bytes — no immutable String copy
            // of the recovery phrase on the heap (NEW-4).
            mnemonicBytes = ByteArray(mnemonic.size) { mnemonic[it].code.toByte() }
            val (enc, salt) = MnemonicCipher.encrypt(mnemonicBytes, pin)
            encrypted = enc
            storedSalt = salt
            walletRepository.updateMnemonic(walletId, encrypted, storedSalt)
        } catch (_: Exception) {
            // Migration is best-effort: the mnemonic stays readable via the legacy salt.
        } finally {
            mnemonicBytes?.wipe()
            encrypted?.wipe()
            storedSalt?.wipe()
        }
    }
}