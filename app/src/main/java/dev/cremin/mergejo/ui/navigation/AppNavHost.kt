package dev.cremin.mergejo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.cremin.mergejo.ui.screen.detail.PrDetailScreen
import dev.cremin.mergejo.ui.screen.list.PrListScreen
import dev.cremin.mergejo.ui.screen.login.LoginScreen
import dev.cremin.mergejo.ui.screen.settings.SettingsScreen

@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val isAuthorized by rootViewModel.isAuthorized.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Cross the auth boundary in either direction, without yanking the user off
    // an inner screen (e.g. detail) on unrelated recompositions.
    LaunchedEffect(isAuthorized) {
        val current = navController.currentBackStackEntry?.destination?.route
        if (!isAuthorized && current != Destinations.LOGIN) {
            navController.navigate(Destinations.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        } else if (isAuthorized && current == Destinations.LOGIN) {
            navController.navigate(Destinations.LIST) {
                popUpTo(Destinations.LOGIN) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isAuthorized) Destinations.LIST else Destinations.LOGIN,
    ) {
        composable(Destinations.LOGIN) {
            LoginScreen()
        }
        composable(Destinations.LIST) {
            PrListScreen(
                onOpenPr = { prId -> navController.navigate(Destinations.detail(prId)) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
            )
        }
        composable(
            route = Destinations.DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_PR_ID) { type = NavType.StringType }),
        ) {
            PrDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
