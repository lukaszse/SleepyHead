package com.example.androidapp.framework.infra.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidapp.framework.adapter.input.HrViewModel

/**
 * Top-level navigation host for the application.
 *
 * Routes:
 * - [Route.HR] — main screen with device scan / live HR + HRV pager.
 * - [Route.HISTORY] — past HRV session history.
 *
 * @param viewModel Shared [HrViewModel] instance used across all screens.
 */
@Composable
fun AppNavigation(viewModel: HrViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.HR) {
        composable(Route.HR) {
            HrScreen(
                viewModel = viewModel,
                onNavigateToHistory = { navController.navigate(Route.HISTORY) }
            )
        }
        composable(Route.HISTORY) {
            HrvHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Centralised route constants for navigation.
 */
object Route {
    const val HR = "hr"
    const val HISTORY = "history"
}

