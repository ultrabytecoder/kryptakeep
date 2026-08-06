package com.ultrabytecoder.kryptakeep.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class AndroidScreenshotProtector(
    private val activity: Activity
) : ScreenshotProtector {

    override fun enable() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun disable() {
        val window = activity.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
actual fun rememberScreenshotProtector(): ScreenshotProtector {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    return remember(activity) { AndroidScreenshotProtector(activity) }
}

private fun Context.findActivity(): Activity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No Activity found in context chain — ScreenshotProtector must be used inside an Activity-hosted Compose hierarchy.")
}