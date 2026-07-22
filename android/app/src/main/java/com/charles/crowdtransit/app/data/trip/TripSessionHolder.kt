package com.charles.crowdtransit.app.data.trip

import com.charles.crowdtransit.model.TripPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-scoped hand-off between screens for trip planning. Itineraries are far too big
 * for nav-route arguments, so the planner puts state here and detail/navigation screens
 * read it (same pattern as a shared ViewModel but usable from repositories/services too).
 */
@Singleton
class TripSessionHolder @Inject constructor() {

    /** Destination pre-filled by "Directions to here" entry points (e.g. StopDetail). */
    val pendingDestination = MutableStateFlow<PendingPlace?>(null)

    private val _selectedPlan = MutableStateFlow<TripPlan?>(null)
    val selectedPlan: StateFlow<TripPlan?> = _selectedPlan

    fun selectPlan(plan: TripPlan?) {
        _selectedPlan.value = plan
    }

    data class PendingPlace(val name: String, val lat: Double, val lng: Double)
}
