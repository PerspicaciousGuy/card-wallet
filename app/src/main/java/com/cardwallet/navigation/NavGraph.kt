package com.cardwallet.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardwallet.features.about.AboutScreen
import com.cardwallet.features.cards.detail.CardDetailScreen
import com.cardwallet.features.cards.form.CardFormScreen
import com.cardwallet.features.home.HomeScreen
import com.cardwallet.features.settings.changepin.ChangePinScreen

private const val SLIDE_DURATION_MS = 300

/**
 * Graph for the UNLOCKED app only — MainActivity renders it exclusively while
 * the session is unlocked (F2.6). Screens receive navigation lambdas, never
 * the controller (compose-rules §7).
 *
 * Forward navigation slides in from the right and back pops it away to the
 * right. Declared once on the NavHost so every destination inherits it —
 * per-destination overrides would let the two directions drift apart.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val slide = tween<IntOffset>(SLIDE_DURATION_MS)

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, slide)
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, slide)
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, slide)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, slide)
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenCard = { id -> navController.navigate(CardDetailRoute(id)) },
                onAddCard = { navController.navigate(CardFormRoute()) },
                onChangePin = { navController.navigate(ChangePinRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
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
        composable<ChangePinRoute> {
            ChangePinScreen(
                onDone = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
            )
        }
        composable<AboutRoute> {
            AboutScreen(onClose = { navController.popBackStack() })
        }
    }
}
