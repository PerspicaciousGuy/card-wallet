package com.cardwallet.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardwallet.features.home.HomeScreen
import com.cardwallet.features.lock.LockScreen

/**
 * Root graph. The lock gate is stubbed open in Phase 0: the app starts at
 * [HomeRoute]. Phase 1 flips the start destination to [LockRoute] and gates
 * centrally on session state (IMPLEMENTATION_PLAN.md §3 rule 2) — never with
 * per-screen checks.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<LockRoute> {
            LockScreen()
        }
        composable<HomeRoute> {
            HomeScreen()
        }
    }
}
