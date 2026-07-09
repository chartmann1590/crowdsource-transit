package com.charles.crowdtransit.app.ui.screens.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.RouteRepository
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.model.Route
import com.charles.crowdtransit.model.RouteStopSummary
import com.charles.crowdtransit.model.RouteWithStops
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteDetailUiState(
    val route: Route? = null,
    val routeWithStops: RouteWithStops? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val stopRepository: StopRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteDetailUiState())
    val uiState: StateFlow<RouteDetailUiState> = _uiState.asStateFlow()

    fun loadRoute(routeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (routeId.startsWith("r-")) {
                val route = stopRepository.getRouteWithStops(routeId)
                _uiState.update {
                    it.copy(
                        routeWithStops = route,
                        isLoading = false,
                        error = if (route == null) "Route not found" else null,
                    )
                }
                return@launch
            }
            try {
                routeRepository.observeRoute(routeId).collect { route ->
                    _uiState.update { it.copy(route = route, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    /** Resolves a route stop's onestop_id from its coordinates so the screen can navigate to it. */
    suspend fun resolveStopId(stop: RouteStopSummary): String? =
        stopRepository.resolveStopIdNear(stop.lat, stop.lng)
}
