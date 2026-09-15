package com.ultrabytecoder.kryptakeep.ui.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ultrabytecoder.kryptakeep.ui.keyboard.platform.SystemKeyboardBlockerContext

actual fun readPlatformClipboardText(): String? = runCatching {
    val activity = SystemKeyboardBlockerContext.getActivity() ?: return null
    val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    clip.getItemAt(0).text?.toString()
}.getOrNull()

actual fun clearPlatformClipboard() {
    runCatching {
        val activity = SystemKeyboardBlockerContext.getActivity() ?: return
        val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
