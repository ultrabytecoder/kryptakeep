package com.ultrabytecoder.kryptakeep.ui.keyboard.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene

actual fun blockSystemKeyboard() {
    // Target the key window of every active window scene (iOS 13+ scenes).
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .forEach { it.keyWindow?.endEditing(true) }
    // Fallback for pre-13 / edge cases where connectedScenes is empty.
    UIApplication.sharedApplication.keyWindow?.endEditing(true)
}
