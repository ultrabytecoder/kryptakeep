package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import kotlinx.coroutines.flow.StateFlow

class CheckPinStatusUseCase(
    private val pinRepository: PinRepository
) {
    operator fun invoke(): StateFlow<PinState> = pinRepository.pinStateFlow
}