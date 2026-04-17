// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keel.datastore.OnboardingStore
import com.keel.ui.screen.AgentDebugScreen
import com.keel.ui.screen.ChatScreen
import com.keel.ui.screen.DashboardScreen
import com.keel.ui.screen.OnboardingScreen
import com.keel.ui.screen.SettingsScreen
import com.keel.ui.screen.TransactionDetailScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat"
    const val TRANSACTION_DETAIL = "transaction/{id}"
    const val SETTINGS = "settings"
    const val AGENT_DEBUG = "agent_debug"

    fun transactionDetail(id: Long) = "transaction/$id"
}

/**
 * Root navigation host.
 *
 * Start destination is determined by [OnboardingStore.isCompleteFlow]:
 *  - false → "onboarding" (first launch, no model downloaded yet)
 *  - true  → "dashboard" (returning user)
 *
 * This composable is the single source of truth for navigation — all screen-to-screen
 * transitions happen here via [NavController], not in individual screens.
 */
@Composable
fun KeelNavHost(onboardingStore: OnboardingStore) {
    val navController = rememberNavController()
    val isOnboardingComplete by onboardingStore.isCompleteFlow
        .collectAsStateWithLifecycle(initialValue = false)

    val startDestination = if (isOnboardingComplete) Routes.DASHBOARD else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onNavigateToDashboard = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToChat = { navController.navigate(Routes.CHAT) },
                onNavigateToTransaction = { id ->
                    navController.navigate(Routes.transactionDetail(id))
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CHAT) {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            TransactionDetailScreen(
                transactionId = id,
                onNavigateBack = { navController.popBackStack() },
                onAskKeel = { query ->
                    navController.navigate(Routes.CHAT)
                    // Pre-fill is handled via KeelViewModel — navigate then query will be cleared
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDebug = { navController.navigate(Routes.AGENT_DEBUG) },
            )
        }

        composable(Routes.AGENT_DEBUG) {
            AgentDebugScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
