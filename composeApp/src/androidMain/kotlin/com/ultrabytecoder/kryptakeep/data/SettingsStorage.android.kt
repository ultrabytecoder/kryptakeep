package com.ultrabytecoder.kryptakeep.data

import android.content.Context

actual class SettingsStorage actual constructor(context: Any?) {
    private val prefs =
        (context as Context).getSharedPreferences("kryptakeep_settings", Context.MODE_PRIVATE)

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }
}