package com.ultrabytecoder.kryptakeep.data

import platform.Foundation.NSUserDefaults

actual class SettingsStorage actual constructor(context: Any?) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}