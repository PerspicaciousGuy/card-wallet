package com.cardwallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.data.session.SessionState
import com.cardwallet.data.session.SessionStateHolder
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
 * reached by any route. FLAG_SECURE is added in Phase 4 (kept off so visual
 * verification stays possible during Phases 1–3).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var session: SessionStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CardWalletTheme {
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
