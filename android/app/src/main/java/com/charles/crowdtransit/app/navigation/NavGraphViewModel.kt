package com.charles.crowdtransit.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavGraphViewModel @Inject constructor(
    preferencesStore: UserPreferencesStore,
    private val tripSession: TripSessionHolder,
) : ViewModel() {
    val hasCompletedOnboarding: StateFlow<Boolean?> = preferencesStore.hasCompletedOnboarding
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Pre-fill the planner destination for "Directions to here" entry points. */
    fun setTripDestination(name: String, lat: Double, lng: Double) {
        tripSession.pendingDestination.value = TripSessionHolder.PendingPlace(name, lat, lng)
    }

    /** Fires (with an incrementing counter) when a shared trip arrives via deep link. */
    val sharedPlanEvents: StateFlow<Int> get() = tripSession.sharedPlanEvents
}
