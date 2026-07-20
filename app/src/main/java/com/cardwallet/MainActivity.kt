package com.cardwallet

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.data.session.SessionState
import com.cardwallet.data.session.SessionStateHolder
import com.cardwallet.data.settings.SettingsStore
import com.cardwallet.data.settings.ThemeMode
import com.cardwallet.features.lock.LockScreen
import com.cardwallet.navigation.AppNavGraph
import com.cardwallet.ui.theme.CardWalletTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FragmentActivity, not ComponentActivity: BiometricPrompt requires it.
 *
 * The lock gate lives HERE, not in navigation (F2.6): while the session is
 * locked the composition contains only the lock flow, so no destination can be
 * reached by any route.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var session: SessionStateHolder

    @Inject
    lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // F6.2: permanent, not a setting — blocks screenshots AND blanks the
        // app-switcher thumbnail. A toggle here would be a footgun.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            CardWalletTheme(darkTheme = darkTheme) {
                val sessionState by session.state.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    when (sessionState) {
                        SessionState.Locked -> LockScreen()
                        SessionState.Unlocked -> AppNavGraph()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        session.onAppForegrounded()
    }

    override fun onStop() {
        session.onAppBackgrounded()
        super.onStop()
    }
}
