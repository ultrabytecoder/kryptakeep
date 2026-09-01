package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod

class SetupPinUseCase(
    private val pinRepository: PinRepository
) {
    suspend operator fun invoke(pin: CharArray, method: SecurityMethod) {
        pinRepository.setupPin(pin, method)
    }
}
