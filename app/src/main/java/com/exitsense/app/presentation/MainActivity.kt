package com.exitsense.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.presentation.navigation.AppNavGraph
import com.exitsense.app.presentation.navigation.Screen
import com.exitsense.app.presentation.theme.ExitSenseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesDataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read setup state synchronously on startup to choose the correct start destination.
        // Only done once; subsequent preference changes are handled by ViewModels.
        val isSetupComplete = runBlocking {
            preferencesDataStore.userPreferences.first().isSetupComplete
        }

        setContent {
            ExitSenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (isSetupComplete) Screen.Home.route
                                           else Screen.SetupWizard.route

                    AppNavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
