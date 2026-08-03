package com.ultrabytecoder.kryptakeep.data

expect class SettingsStorage(context: Any? = null) {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
}