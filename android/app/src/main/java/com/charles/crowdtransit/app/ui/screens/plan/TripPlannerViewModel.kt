package com.charles.crowdtransit.app.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.app.domain.routing.PlanRequest
import com.charles.crowdtransit.app.domain.routing.PlanTripUseCase
import com.charles.crowdtransit.app.domain.routing.RateLimitedException
import com.charles.crowdtransit.model.TripPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One endpoint (origin or destination) of the trip being planned. */
data class PlannerPlace(
    val name: String,
    val lat: Double,
    val lng: Double,
    val isMyLocation: Boolean = false,
)

data class PlaceSuggestion(val name: String, val detail: String, val lat: Double, val lng: Double)

data class TripPlannerUiState(
    val origin: PlannerPlace? = null,
    val destination: PlannerPlace? = null,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val searchQuery: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    /** Which field the search box is editing: true = origin, false = destination. */
    val editingOrigin: Boolean = false,
    val searching: Boolean = false,
    val planning: Boolean = false,
    val plans: List<TripPlan> = emptyList(),
    val planned: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TripPlannerViewModel @Inject constructor(
    private val planTrip: PlanTripUseCase,
    private val stopRepository: StopRepository,
    private val session: TripSessionHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripPlannerUiState())
    val uiState: StateFlow<TripPlannerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        session.pendingDestination.value?.let { pending ->
            _uiState.update {
                it.copy(destination = PlannerPlace(pending.name, pending.lat, pending.lng))
            }
            session.pendingDestination.value = null
        }
    }

    fun onLocationUpdate(lat: Double, lng: Double) {
        _uiState.update { state ->
            val origin = state.origin
            state.copy(
                userLat = lat,
                userLng = lng,
                // Keep a "My location" origin following the fix; set it as default origin once.
                origin = when {
                    origin == null || origin.isMyLocation ->
                        PlannerPlace("My location", lat, lng, isMyLocation = true)
                    else -> origin
                },
            )
        }
    }

    fun startEditing(origin: Boolean) {
        _uiState.update { it.copy(editingOrigin = origin, searchQuery = "", suggestions = emptyList()) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _uiState.update { it.copy(searching = true) }
            val stops = runCatching { stopRepository.searchStops(query) }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(
                    searching = false,
                    suggestions = stops.map {
                        PlaceSuggestion(
                            name = it.name,
                            detail = listOf(it.state, it.country).filter { p -> p.isNotBlank() }.joinToString(", "),
                            lat = it.lat,
                            lng = it.lng,
                        )
                    },
                )
            }
        }
    }

    fun selectSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update { state ->
            val place = PlannerPlace(suggestion.name, suggestion.lat, suggestion.lng)
            if (state.editingOrigin) state.copy(origin = place, searchQuery = "", suggestions = emptyList())
            else state.copy(destination = place, searchQuery = "", suggestions = emptyList())
        }
    }

    fun useMyLocation() {
        _uiState.update { state ->
            val lat = state.userLat ?: return@update state
            val lng = state.userLng ?: return@update state
            val place = PlannerPlace("My location", lat, lng, isMyLocation = true)
            if (state.editingOrigin) state.copy(origin = place, searchQuery = "", suggestions = emptyList())
            else state.copy(destination = place, searchQuery = "", suggestions = emptyList())
        }
    }

    fun swapEndpoints() {
        _uiState.update { it.copy(origin = it.destination, destination = it.origin) }
    }

    fun plan() {
        val state = _uiState.value
        val origin = state.origin ?: return
        val destination = state.destination ?: return
        _uiState.update { it.copy(planning = true, planned = false, plans = emptyList(), error = null) }
        viewModelScope.launch {
            try {
                val plans = planTrip(
                    PlanRequest(
                        fromName = origin.name,
                        fromLat = origin.lat,
                        fromLng = origin.lng,
                        toName = destination.name,
                        toLat = destination.lat,
                        toLng = destination.lng,
                    ),
                )
                _uiState.update { it.copy(planning = false, planned = true, plans = plans) }
            } catch (_: RateLimitedException) {
                _uiState.update {
                    it.copy(
                        planning = false,
                        planned = true,
                        error = "Transit data is busy right now — please try again in a minute.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(planning = false, planned = true, error = "Couldn't plan this trip. Check your connection and try again.")
                }
            }
        }
    }

    fun openPlan(plan: TripPlan) {
        session.selectPlan(plan)
    }
}
