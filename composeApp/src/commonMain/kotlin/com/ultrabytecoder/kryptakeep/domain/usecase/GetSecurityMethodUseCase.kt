package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod
import kotlinx.coroutines.flow.StateFlow

class GetSecurityMethodUseCase(
    private val pinRepository: PinRepository
) {
    operator fun invoke(): StateFlow<SecurityMethod?> = pinRepository.securityMethodFlow
}
