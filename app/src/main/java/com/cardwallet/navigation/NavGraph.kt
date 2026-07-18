package com.cardwallet.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardwallet.features.home.HomeScreen

/**
 * Graph for the UNLOCKED app only — MainActivity renders it exclusively while
 * the session is unlocked (F2.6). Card detail/form routes join in Phase 3.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            HomeScreen()
        }
    }
}
