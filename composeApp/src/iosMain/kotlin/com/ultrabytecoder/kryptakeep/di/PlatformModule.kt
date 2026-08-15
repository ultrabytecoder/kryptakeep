package com.ultrabytecoder.kryptakeep.di

import com.ultrabytecoder.kryptakeep.data.DatabaseDriverFactory
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import org.koin.dsl.module

val platformModule = module {
    single { DatabaseDriverFactory() }
    single { SettingsStorage() }
}
