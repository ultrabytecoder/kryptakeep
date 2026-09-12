package com.ultrabytecoder.kryptakeep

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.ultrabytecoder.kryptakeep.ui.util.applySecureFlag

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Prevent screenshots / screen recordings / app-switcher previews of the
        // whole app (wallets, balances, recovery phrases). Skipped in debug builds
        // so scrcpy works during development / QA.
        window.applySecureFlag()

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}