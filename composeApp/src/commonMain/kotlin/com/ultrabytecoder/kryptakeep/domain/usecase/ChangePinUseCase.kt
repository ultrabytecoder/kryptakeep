package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.ChangePinResult
import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository

class ChangePinUseCase(
    private val pinRepository: PinRepository
) {
    /**
     * Verifies [oldPin] and switches the DEK envelope to [newPin]. On failure the
     * old PIN remains fully functional.
     */
    suspend operator fun invoke(oldPin: CharArray, newPin: CharArray): ChangePinResult =
        pinRepository.changePin(oldPin, newPin)
}