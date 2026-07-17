package com.cardwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cardwallet.navigation.AppNavGraph
import com.cardwallet.ui.theme.CardWalletTheme
import dagger.hilt.android.AndroidEntryPoint

// FLAG_SECURE is added in Phase 4 (IMPLEMENTATION_PLAN.md §7) — deliberately not
// yet, so on-device visual verification of the glass UI stays possible during
// early phases.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CardWalletTheme {
                AppNavGraph()
            }
        }
    }
}
