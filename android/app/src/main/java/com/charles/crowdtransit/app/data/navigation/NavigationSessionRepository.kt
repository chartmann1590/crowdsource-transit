package com.charles.crowdtransit.app.data.navigation

import com.charles.crowdtransit.app.domain.navigation.NavigationEngine
import com.charles.crowdtransit.model.TripPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the active navigation session, shared by
 * NavigationService (writes GPS fixes) and the navigation UI (renders state) without
 * binder plumbing. One session at a time.
 */
@Singleton
class NavigationSessionRepository @Inject constructor() {

    private val _state = MutableStateFlow<NavigationEngine.NavState?>(null)
    val state: StateFlow<NavigationEngine.NavState?> = _state.asStateFlow()

    private val _lastFix = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastFix: StateFlow<Pair<Double, Double>?> = _lastFix.asStateFlow()

    private var engine: NavigationEngine? = null
    var lastRerouteMs: Long = 0L
        private set

    val activePlan: TripPlan? get() = engine?.plan
    val isActive: Boolean get() = engine != null

    fun start(plan: TripPlan) {
        engine = NavigationEngine(plan)
        _state.value = engine?.currentState
    }

    /** Replace the plan mid-session (accepted reroute). */
    fun reroute(plan: TripPlan, nowMs: Long = System.currentTimeMillis()) {
        lastRerouteMs = nowMs
        start(plan)
    }

    fun onLocation(lat: Double, lng: Double, nowMs: Long = System.currentTimeMillis()) {
        _lastFix.value = lat to lng
        _state.value = engine?.onLocation(lat, lng, nowMs)
    }

    fun stop() {
        engine = null
        _state.value = null
        _lastFix.value = null
    }
}
