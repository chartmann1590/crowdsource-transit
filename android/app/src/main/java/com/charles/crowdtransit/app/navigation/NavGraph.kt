package com.charles.crowdtransit.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.charles.crowdtransit.app.ui.screens.auth.OnboardingScreen
import com.charles.crowdtransit.app.ui.screens.crowdsource.AddStopScreen
import com.charles.crowdtransit.app.ui.screens.downloads.DownloadsScreen
import com.charles.crowdtransit.app.ui.screens.login.LoginScreen
import com.charles.crowdtransit.app.ui.screens.map.MapHomeScreen
import com.charles.crowdtransit.app.ui.screens.profile.ProfileScreen
import com.charles.crowdtransit.app.ui.screens.route.RouteDetailScreen
import com.charles.crowdtransit.app.ui.screens.search.SearchScreen
import com.charles.crowdtransit.app.ui.screens.settings.SettingsScreen
import com.charles.crowdtransit.app.ui.screens.stop.RateStopScreen
import com.charles.crowdtransit.app.ui.screens.stop.StopDetailScreen

@Composable
fun CrowdTransitNavGraph(
    navController: NavHostController = rememberNavController(),
    navGraphViewModel: NavGraphViewModel = hiltViewModel(),
) {
    val hasCompletedOnboarding by navGraphViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val onboardingStatus = hasCompletedOnboarding
    if (onboardingStatus == null) {
        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val startDestination = if (onboardingStatus) Screen.MapHome.route else Screen.Onboarding.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.MapHome.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.MapHome.route) {
            MapHomeScreen(
                onStopClick = { navController.navigate(Screen.StopDetail.createRoute(it)) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onAddStopClick = { navController.navigate(Screen.AddStop.route) },
            )
        }
        composable(
            route = Screen.StopDetail.route,
            arguments = listOf(navArgument("stopId") { type = NavType.StringType }),
        ) { backStack ->
            val stopId = backStack.arguments?.getString("stopId") ?: return@composable
            StopDetailScreen(
                stopId = stopId,
                onBack = { navController.popBackStack() },
                onRouteClick = { navController.navigate(Screen.RouteDetail.createRoute(it)) },
                onRateClick = { navController.navigate(Screen.RateStop.createRoute(stopId)) },
            )
        }
        composable(
            route = Screen.RouteDetail.route,
            arguments = listOf(navArgument("routeId") { type = NavType.StringType }),
        ) { backStack ->
            val routeId = backStack.arguments?.getString("routeId") ?: return@composable
            RouteDetailScreen(
                routeId = routeId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onStopClick = { navController.navigate(Screen.StopDetail.createRoute(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onSignInClick = { navController.navigate(Screen.Login.route) },
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() },
            )
        }
        composable(Screen.AddStop.route) {
            AddStopScreen(
                userLat = 0.0,
                userLng = 0.0,
                onBack = { navController.popBackStack() },
                onSubmit = { _, _, _, _, _, _, _ ->
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Screen.RateStop.route,
            arguments = listOf(navArgument("stopId") { type = NavType.StringType }),
        ) { backStack ->
            val stopId = backStack.arguments?.getString("stopId") ?: return@composable
            RateStopScreen(
                stopId = stopId,
                stopName = "Stop",
                onBack = { navController.popBackStack() },
                onSubmit = { _, _, _, _, _ ->
                    navController.popBackStack()
                },
            )
        }
    }
}
