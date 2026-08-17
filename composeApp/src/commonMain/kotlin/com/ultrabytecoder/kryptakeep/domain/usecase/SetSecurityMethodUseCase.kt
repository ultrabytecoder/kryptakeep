package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.data.SettingsKeys
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod

class SetSecurityMethodUseCase(
    private val settingsStorage: SettingsStorage
) {
    operator fun invoke(method: SecurityMethod) {
        settingsStorage.putString(SettingsKeys.SECURITY_METHOD, method.name)
    }
}
