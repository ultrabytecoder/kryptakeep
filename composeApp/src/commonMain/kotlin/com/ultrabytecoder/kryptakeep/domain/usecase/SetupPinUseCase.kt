package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository

class SetupPinUseCase(
    private val pinRepository: PinRepository
) {
    suspend operator fun invoke(pin: CharArray) {
        pinRepository.setupPin(pin)
    }
}