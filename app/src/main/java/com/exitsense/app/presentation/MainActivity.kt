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
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesDataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ExitSenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Null until the first DataStore emission arrives (sub-millisecond on warm
                    // storage); render nothing rather than blocking the main thread.
                    val isSetupComplete by remember {
                        preferencesDataStore.userPreferences.map { it.isSetupComplete }
                    }.collectAsStateWithLifecycle(initialValue = null)

                    if (isSetupComplete != null) {
                        val navController = rememberNavController()
                        AppNavGraph(
                            navController = navController,
                            startDestination = if (isSetupComplete == true) Screen.Home.route
                                               else Screen.SetupWizard.route
                        )
                    }
                }
            }
        }
    }
}
