package com.ultrabytecoder.kryptakeep

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.di.appModule
import com.ultrabytecoder.kryptakeep.di.platformModule
import com.ultrabytecoder.kryptakeep.security.SessionLockNotifier
import com.ultrabytecoder.kryptakeep.security.SessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MyApplication : Application(), KoinComponent {

    // The activity currently in the foreground, or null if none of our activities
    // is visible (e.g. the app is backgrounded). Used to distinguish a real
    // background event from an in-app activity covering MainActivity (QR scanner).
    //
    // Only ever ASSIGNED on resume, never cleared on pause: the lifecycle guarantee
    // A.onPause -> B.onResume -> A.onStop means that when an activity stops and it
    // is STILL the top one, no other activity took over — the app is backgrounding.
    // (Clearing it in onActivityPaused broke the lock: MainActivity.onStop then saw
    // topActivity == null and never locked — NEW-1.)
    @Volatile
    var topActivity: Activity? = null
        private set

    private val sessionManager: SessionManager by inject()

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                topActivity = activity
                // User activity resets the session idle timeout (F-5).
                sessionManager.registerActivity()
            }

            override fun onActivityStopped(activity: Activity) {
                // The app went to the background (Home pressed, app switcher, or the
                // zxing QR scanner being covered): lock the session (close the
                // SQLCipher database and zero the DEK) and tell the UI to return to
                // the PIN unlock screen. Configuration changes are exempt so the
                // user stays unlocked while rotating.
                if (!activity.isChangingConfigurations && topActivity === activity) {
                    sessionManager.lock()
                    SessionLockNotifier.notifyLocked()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        val networkConfig = if (BuildConfig.IS_TESTNET) NetworkConfig.testnet(BuildConfig.ETHERSCAN_API_KEY) else NetworkConfig.mainnet(BuildConfig.ETHERSCAN_API_KEY)

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MyApplication)
            modules(appModule(networkConfig), platformModule)
        }
    }
}
