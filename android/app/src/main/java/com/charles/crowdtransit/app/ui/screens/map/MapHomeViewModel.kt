package com.charles.crowdtransit.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.model.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapHomeUiState(
    val nearbyStops: List<Stop> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userLat: Double? = null,
    val userLng: Double? = null,
)

@HiltViewModel
class MapHomeViewModel @Inject constructor(
    private val stopRepository: StopRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapHomeUiState())
    val uiState: StateFlow<MapHomeUiState> = _uiState.asStateFlow()

    fun onLocationUpdate(lat: Double, lng: Double) {
        _uiState.update { it.copy(userLat = lat, userLng = lng) }
        loadNearbyStops(lat, lng)
    }

    private fun loadNearbyStops(lat: Double, lng: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val stops = stopRepository.getStopsNearby(lat, lng, radiusKm = 1.0)
                val sorted = stops.sortedBy { stop ->
                    val dlat = stop.lat - lat
                    val dlng = stop.lng - lng
                    dlat * dlat + dlng * dlng
                }
                _uiState.update { it.copy(nearbyStops = sorted, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
