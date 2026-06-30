package com.charles.crowdtransit.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.charles.crowdtransit.app.ui.screens.auth.OnboardingScreen
import com.charles.crowdtransit.app.ui.screens.crowdsource.AddStopScreen
import com.charles.crowdtransit.app.ui.screens.map.MapHomeScreen
import com.charles.crowdtransit.app.ui.screens.profile.ProfileScreen
import com.charles.crowdtransit.app.ui.screens.route.RouteDetailScreen
import com.charles.crowdtransit.app.ui.screens.search.SearchScreen
import com.charles.crowdtransit.app.ui.screens.stop.RateStopScreen
import com.charles.crowdtransit.app.ui.screens.stop.StopDetailScreen

@Composable
fun CrowdTransitNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route,
) {
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
