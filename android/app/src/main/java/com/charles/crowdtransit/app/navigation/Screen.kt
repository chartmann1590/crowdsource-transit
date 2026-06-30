package com.charles.crowdtransit.app.navigation

sealed class Screen(val route: String) {
    object MapHome : Screen("map_home")
    object StopDetail : Screen("stop/{stopId}") {
        fun createRoute(stopId: String) = "stop/$stopId"
    }
    object RouteDetail : Screen("route/{routeId}") {
        fun createRoute(routeId: String) = "route/$routeId"
    }
    object Search : Screen("search")
    object Profile : Screen("profile")
    object Onboarding : Screen("onboarding")
    object AddStop : Screen("add_stop")
    object RateStop : Screen("rate/{stopId}") {
        fun createRoute(stopId: String) = "rate/$stopId"
    }
}
