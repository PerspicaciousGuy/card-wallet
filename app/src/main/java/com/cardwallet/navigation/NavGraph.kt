package com.cardwallet.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardwallet.features.cards.detail.CardDetailScreen
import com.cardwallet.features.cards.form.CardFormScreen
import com.cardwallet.features.home.HomeScreen

/**
 * Graph for the UNLOCKED app only — MainActivity renders it exclusively while
 * the session is unlocked (F2.6). Screens receive navigation lambdas, never
 * the controller (compose-rules §7).
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenCard = { id -> navController.navigate(CardDetailRoute(id)) },
                onAddCard = { navController.navigate(CardFormRoute()) },
            )
        }
        composable<CardDetailRoute> {
            CardDetailScreen(
                onEdit = { id -> navController.navigate(CardFormRoute(id)) },
                onClose = { navController.popBackStack() },
            )
        }
        composable<CardFormRoute> {
            CardFormScreen(
                onClose = { navController.popBackStack() },
            )
        }
    }
}
