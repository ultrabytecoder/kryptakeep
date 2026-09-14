package com.ultrabytecoder.kryptakeep.ui.keyboard.platform

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.lang.ref.WeakReference

object SystemKeyboardBlockerContext {
    private var activityRef: WeakReference<Activity>? = null

    fun register(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun getActivity(): Activity? = activityRef?.get()

    fun clear() {
        activityRef = null
    }
}

/**
 * Suppresses the system soft keyboard for the current activity: sets the window
 * to always-hide IME and dismisses any showing IME insets.
 *
 * Called from [MainActivity.onCreate] and re-asserted on every [onResume]
 * (config change / multi-window / dismissed dialogs can re-show the keyboard).
 * Deliberately does NOT touch decor-fits-system-windows — the activity is
 * edge-to-edge and this must not fight that.
 */
actual fun blockSystemKeyboard() {
    SystemKeyboardBlockerContext.getActivity()?.let { activity ->
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            hide(WindowInsetsCompat.Type.ime())
        }
    }
}
