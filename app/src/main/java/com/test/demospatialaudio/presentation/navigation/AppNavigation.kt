package com.test.demospatialaudio.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.test.demospatialaudio.DemoSpatialAudioApp
import com.test.demospatialaudio.presentation.canvas.CanvasScreen
import com.test.demospatialaudio.presentation.onboarding.OnboardingScreen
import com.test.demospatialaudio.presentation.presets.PresetsListScreen
import com.test.demospatialaudio.presentation.recordings.RecordingsScreen
import com.test.demospatialaudio.presentation.settings.SettingsScreen
import kotlinx.coroutines.flow.first

object Routes {
    const val ONBOARDING = "onboarding"
    const val PRESETS = "presets"
    const val CANVAS = "canvas/{presetId}"
    const val RECORDINGS = "recordings"
    const val SETTINGS = "settings"

    fun canvas(presetId: String) = "canvas/$presetId"
}

@Composable
fun AppNavigation(
    pendingAction: PendingAction? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as DemoSpatialAudioApp
    val onboardingDone by app.preferences.onboardingDone.collectAsState(initial = true)

    val start = if (!onboardingDone) Routes.ONBOARDING else Routes.PRESETS

    LaunchedEffect(pendingAction) {
        if (pendingAction == null) return@LaunchedEffect
        if (!onboardingDone) return@LaunchedEffect
        when (pendingAction) {
            PendingAction.NewSpace -> {
                // Stay on presets, the screen will show empty/new
            }
            PendingAction.Resume -> {
                val lastId = app.preferences.lastPresetId.first()
                if (lastId != null) {
                    navController.navigate(Routes.canvas(lastId))
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.PRESETS) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PRESETS) {
            PresetsListScreen(
                onPresetClick = { presetId ->
                    navController.navigate(Routes.canvas(presetId))
                },
                onRecordingsClick = {
                    navController.navigate(Routes.RECORDINGS)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.CANVAS,
            arguments = listOf(navArgument("presetId") { type = NavType.StringType })
        ) { backStackEntry ->
            val presetId = backStackEntry.arguments?.getString("presetId") ?: return@composable
            CanvasScreen(
                presetId = presetId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RECORDINGS) {
            RecordingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onReplayOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.PRESETS) { inclusive = false }
                    }
                }
            )
        }
    }
}

enum class PendingAction { NewSpace, Resume }
