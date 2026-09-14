package com.ultrabytecoder.kryptakeep.ui.keyboard.platform

import platform.UIKit.UIApplication

actual fun blockSystemKeyboard() {
    UIApplication.sharedApplication.keyWindow?.endEditing(true)
}
