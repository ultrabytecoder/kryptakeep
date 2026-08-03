package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult

class VerifyPinUseCase(
    private val pinRepository: PinRepository
) {
    suspend operator fun invoke(pin: String): VerifyResult = pinRepository.verifyPin(pin)
}