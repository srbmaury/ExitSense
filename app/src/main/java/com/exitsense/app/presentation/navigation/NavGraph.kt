package com.exitsense.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.exitsense.app.presentation.history.HistoryScreen
import com.exitsense.app.presentation.home.HomeScreen
import com.exitsense.app.presentation.profiles.AddEditProfileScreen
import com.exitsense.app.presentation.profiles.ProfilesScreen
import com.exitsense.app.presentation.settings.SettingsScreen
import com.exitsense.app.presentation.setup.SetupWizardScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Profiles.route) {
            ProfilesScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddProfile = { navController.navigate(Screen.AddEditProfile.createRoute()) },
                onEditProfile = { id ->
                    navController.navigate(Screen.AddEditProfile.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.AddEditProfile.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStack ->
            val profileId = backStack.arguments?.getLong("profileId")
                ?.takeIf { it != -1L }
            AddEditProfileScreen(
                profileId = profileId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.SetupWizard.route) {
            SetupWizardScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SetupWizard.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
