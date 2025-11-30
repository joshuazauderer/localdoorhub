package com.localdoorhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localdoorhub.app.data.local.PreferencesManager
import com.localdoorhub.app.ui.SplashScreen
import com.localdoorhub.app.ui.OnboardingScreen
import com.localdoorhub.app.ui.HomeScreen
import com.localdoorhub.app.ui.EventsScreen
import com.localdoorhub.app.ui.EventDetailScreen
import com.localdoorhub.app.ui.theme.LocalDoorHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalDoorHubTheme {
                LocalDoorHubApp()
            }
        }
    }
}

@Composable
fun LocalDoorHubApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefsManager = PreferencesManager(context)

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onFinished = {
                    if (prefsManager.isOnboardingComplete()) {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    } else {
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("onboarding") {
            OnboardingScreen(
                onFinished = {
                    prefsManager.setOnboardingComplete(true)
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                onNavigateToEvents = {
                    navController.navigate("events")
                },
                onNavigateToEventDetail = { eventId ->
                    navController.navigate("event/$eventId")
                },
                onNavigateToHome = {
                    // Navigate to home, clearing back stack if needed
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("events") {
            EventsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEventDetail = { eventId ->
                    navController.navigate("event/$eventId")
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "event/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            EventDetailScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}

