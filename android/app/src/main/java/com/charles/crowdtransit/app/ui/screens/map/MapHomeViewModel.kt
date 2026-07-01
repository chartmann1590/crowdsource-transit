package com.charles.crowdtransit.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.model.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

private const val MIN_RELOAD_DISTANCE_KM = 0.3

data class MapHomeUiState(
    val nearbyStops: List<Stop> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val distances: Map<String, Float> = emptyMap(),
    val useImperialUnits: Boolean = false,
)

private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val R = 6371.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLng = (lng2 - lng1) * PI / 180.0
    val a = sin(dLat / 2).pow(2) +
        cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

@HiltViewModel
class MapHomeViewModel @Inject constructor(
    private val stopRepository: StopRepository,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapHomeUiState())
    val uiState: StateFlow<MapHomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastLoadedLat: Double? = null
    private var lastLoadedLng: Double? = null

    init {
        viewModelScope.launch {
            try {
                preferencesStore.useImperialUnits.collect { useImperial ->
                    _uiState.update { it.copy(useImperialUnits = useImperial) }
                }
            } catch (_: Exception) { }
        }
    }

    fun onLocationUpdate(lat: Double, lng: Double) {
        _uiState.update { it.copy(userLat = lat, userLng = lng) }
        val prevLat = lastLoadedLat
        val prevLng = lastLoadedLng
        val shouldReload = prevLat == null || prevLng == null ||
            haversineKm(prevLat, prevLng, lat, lng) > MIN_RELOAD_DISTANCE_KM
        if (shouldReload) {
            loadNearbyStops(lat, lng)
        }
    }

    private fun loadNearbyStops(lat: Double, lng: Double) {
        loadJob?.cancel()
        lastLoadedLat = lat
        lastLoadedLng = lng
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val stops = stopRepository.getStopsNearby(lat, lng, radiusKm = 5.0)
                android.util.Log.d("MapVM", "Loaded ${stops.size} stops for $lat, $lng")
                val withDistances = stops.map { stop ->
                    stop to haversineKm(lat, lng, stop.lat, stop.lng)
                }
                val sorted = withDistances.sortedBy { (_, dist) -> dist }
                val distanceMap = sorted.associate { (stop, dist) ->
                    stop.stopId to (dist * 1000).toFloat()
                }
                _uiState.update {
                    it.copy(
                        nearbyStops = sorted.map { (stop, _) -> stop },
                        distances = distanceMap,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
