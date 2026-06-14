package com.exitsense.app.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Profiles : Screen("profiles")
    object AddEditProfile : Screen("add_edit_profile?profileId={profileId}") {
        fun createRoute(profileId: Long? = null) =
            if (profileId != null) "add_edit_profile?profileId=$profileId"
            else "add_edit_profile?profileId=-1"
    }
    object History : Screen("history")
    object Settings : Screen("settings")
    object Integrations : Screen("integrations")
    object SetupWizard : Screen("setup_wizard")
}
