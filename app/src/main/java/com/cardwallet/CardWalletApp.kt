package com.cardwallet

import android.app.Application
import com.cardwallet.data.session.SessionStateHolder
import com.cardwallet.data.settings.SettingsStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class CardWalletApp : Application() {
    @Inject
    lateinit var settings: SettingsStore

    @Inject
    lateinit var session: SessionStateHolder

    @Inject
    @Named("app")
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // F6.1: the auto-lock preference feeds the session holder for the app's
        // whole lifetime; the holder itself stays a pure, testable class.
        appScope.launch {
            settings.autoLockTimeout.collect { timeout ->
                session.autoLockTimeoutMillis = timeout.millis
            }
        }
    }
}
