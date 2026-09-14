package com.ultrabytecoder.kryptakeep

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.ultrabytecoder.kryptakeep.ui.keyboard.platform.SystemKeyboardBlockerContext
import com.ultrabytecoder.kryptakeep.ui.util.applySecureFlag

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        window.applySecureFlag()

        SystemKeyboardBlockerContext.register(this)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())

        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-assert IME suppression on every resume: a config change, a
        // multi-window transition, or a dismissed system dialog can re-show the
        // soft keyboard, which would defeat the secure-keyboard threat model.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}