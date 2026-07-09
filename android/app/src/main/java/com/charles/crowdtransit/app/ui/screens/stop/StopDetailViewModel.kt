package com.charles.crowdtransit.app.ui.screens.stop

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.ActivityRepository
import com.charles.crowdtransit.app.data.repository.CommentRepository
import com.charles.crowdtransit.app.data.repository.GamificationRepository
import com.charles.crowdtransit.app.data.repository.PhotoRepository
import com.charles.crowdtransit.app.data.repository.RatingRepository
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.model.ActivityEvent
import com.charles.crowdtransit.model.Comment
import com.charles.crowdtransit.model.PointAction
import com.charles.crowdtransit.model.Rating
import com.charles.crowdtransit.model.ServedDeparture
import com.charles.crowdtransit.model.ServedRoute
import com.charles.crowdtransit.model.Stop
import com.charles.crowdtransit.model.StopPhoto
import com.charles.crowdtransit.app.data.repository.TransitlandRateLimitException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopDetailUiState(
    val stop: Stop? = null,
    val comments: List<Comment> = emptyList(),
    val userRating: Rating? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val activity: List<ActivityEvent> = emptyList(),
    val photos: List<StopPhoto> = emptyList(),
    val hasCheckedInRecently: Boolean = false,
    val pointsBurst: Int? = null,
    val servedRoutes: List<ServedRoute> = emptyList(),
    val upcoming: List<ServedDeparture> = emptyList(),
    val isLoadingSchedule: Boolean = false,
    val scheduleRateLimited: Boolean = false,
    val agencies: List<String> = emptyList(),
    val resolvedStopId: String? = null,
    val resolvedStopName: String? = null,
)

@HiltViewModel
class StopDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stopRepository: StopRepository,
    private val commentRepository: CommentRepository,
    private val ratingRepository: RatingRepository,
    private val activityRepository: ActivityRepository,
    private val photoRepository: PhotoRepository,
    private val gamificationRepository: GamificationRepository,
) : ViewModel() {

    private val stopId: String = checkNotNull(savedStateHandle["stopId"])

    private val _uiState = MutableStateFlow(StopDetailUiState())
    val uiState: StateFlow<StopDetailUiState> = _uiState.asStateFlow()

    private var hasLoadedSchedule = false

    init {
        viewModelScope.launch {
            stopRepository.observeStop(stopId).collect { stop ->
                _uiState.update { it.copy(stop = stop, isLoading = false) }
                if (stop != null) {
                    loadRoutesAndSchedule(stop)
                }
            }
        }
        viewModelScope.launch {
            commentRepository.observeComments("stops", stopId).collect { comments ->
                _uiState.update { it.copy(comments = comments) }
            }
        }
        viewModelScope.launch {
            val rating = ratingRepository.getUserRating("stops", stopId)
            _uiState.update { it.copy(userRating = rating) }
        }
        viewModelScope.launch {
            activityRepository.observeActivity(stopId).collect { events ->
                _uiState.update { it.copy(activity = events) }
            }
        }
        viewModelScope.launch {
            photoRepository.observePhotos(stopId).collect { photos ->
                _uiState.update { it.copy(photos = photos) }
            }
        }
    }

    private suspend fun loadRoutesAndSchedule(currentStop: Stop) {
        if (hasLoadedSchedule) return
        hasLoadedSchedule = true
        _uiState.update { it.copy(isLoadingSchedule = true, scheduleRateLimited = false) }

        val isTransitlandId = currentStop.stopId.startsWith("r-") || currentStop.stopId.startsWith("s-") ||
                currentStop.stopId.startsWith("d-") || currentStop.stopId.startsWith("f-")

        var targetStopId = currentStop.stopId
        var targetStopName = currentStop.name

        var loadedRoutes = emptyList<ServedRoute>()
        var loadedAgencies = emptyList<String>()

        // 1. If Firebase stop, pre-load static agency & routes from Firebase
        if (!isTransitlandId) {
            try {
                val agency = currentStop.agencyId.takeIf { it.isNotBlank() }?.let { stopRepository.getAgency(it) }
                val agencyName = agency?.name ?: ""
                if (agencyName.isNotBlank()) {
                    loadedAgencies = listOf(agencyName)
                }

                loadedRoutes = currentStop.routeIds.keys.mapNotNull { routeId ->
                    stopRepository.getRoute(routeId)?.let { r ->
                        ServedRoute(
                            onestopId = r.routeId,
                            routeId = r.routeId,
                            shortName = r.shortName,
                            longName = r.longName,
                            routeType = null,
                            transitType = r.type,
                            color = r.color,
                            textColor = r.textColor,
                            agencyName = agencyName,
                            nextDepartureTime = null,
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        servedRoutes = loadedRoutes,
                        agencies = loadedAgencies
                    )
                }
            } catch (e: Exception) {
                // Ignore pre-load error, continue to coords resolution
            }

            // Find nearest Transitland stop within 200m (0.2km)
            val nearby = try {
                stopRepository.getStopsNearby(currentStop.lat, currentStop.lng, radiusKm = 0.2)
            } catch (_: Exception) {
                emptyList()
            }
            val nearestPublic = nearby.firstOrNull { s ->
                s.stopId.startsWith("r-") || s.stopId.startsWith("s-") ||
                        s.stopId.startsWith("d-") || s.stopId.startsWith("f-")
            }
            if (nearestPublic != null) {
                targetStopId = nearestPublic.stopId
                targetStopName = nearestPublic.name
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingSchedule = false,
                        scheduleRateLimited = false,
                        resolvedStopId = null,
                        resolvedStopName = null,
                    )
                }
                return
            }
        }

        // 2. Query departures for the target stop (could be the same public stop or resolved public stop)
        try {
            val result = stopRepository.getRoutesAndScheduleForStop(targetStopId)
            val uniqueAgencies = result.routes.map { it.agencyName }.filter { it.isNotBlank() }.distinct()

            _uiState.update {
                it.copy(
                    servedRoutes = if (loadedRoutes.isNotEmpty()) loadedRoutes else if (result.routes.isNotEmpty()) result.routes else it.servedRoutes,
                    upcoming = result.upcoming,
                    isLoadingSchedule = false,
                    scheduleRateLimited = false,
                    resolvedStopId = if (targetStopId != currentStop.stopId) targetStopId else null,
                    resolvedStopName = if (targetStopId != currentStop.stopId) targetStopName else null,
                    agencies = if (loadedAgencies.isNotEmpty()) loadedAgencies else if (uniqueAgencies.isNotEmpty()) uniqueAgencies else it.agencies
                )
            }
        } catch (_: TransitlandRateLimitException) {
            // Check if we can fallback to Transitland static routes
            val fallbackRoutes = if (isTransitlandId) stopRepository.getStaticRoutesForStop(targetStopId) else emptyList()
            val fallbackAgencies = fallbackRoutes.map { it.agencyName }.filter { it.isNotBlank() }.distinct()

            _uiState.update {
                it.copy(
                    servedRoutes = if (loadedRoutes.isNotEmpty()) loadedRoutes else if (fallbackRoutes.isNotEmpty()) fallbackRoutes else it.servedRoutes,
                    upcoming = emptyList(),
                    isLoadingSchedule = false,
                    scheduleRateLimited = true,
                    resolvedStopId = if (targetStopId != currentStop.stopId) targetStopId else null,
                    resolvedStopName = if (targetStopId != currentStop.stopId) targetStopName else null,
                    agencies = if (loadedAgencies.isNotEmpty()) loadedAgencies else if (fallbackAgencies.isNotEmpty()) fallbackAgencies else it.agencies
                )
            }
        } catch (_: Exception) {
            // Check if we can fallback to Transitland static routes
            val fallbackRoutes = if (isTransitlandId) stopRepository.getStaticRoutesForStop(targetStopId) else emptyList()
            val fallbackAgencies = fallbackRoutes.map { it.agencyName }.filter { it.isNotBlank() }.distinct()

            _uiState.update {
                it.copy(
                    servedRoutes = if (loadedRoutes.isNotEmpty()) loadedRoutes else if (fallbackRoutes.isNotEmpty()) fallbackRoutes else it.servedRoutes,
                    upcoming = emptyList(),
                    isLoadingSchedule = false,
                    resolvedStopId = if (targetStopId != currentStop.stopId) targetStopId else null,
                    resolvedStopName = if (targetStopId != currentStop.stopId) targetStopName else null,
                    agencies = if (loadedAgencies.isNotEmpty()) loadedAgencies else if (fallbackAgencies.isNotEmpty()) fallbackAgencies else it.agencies
                )
            }
        }
    }

    fun markHelpful(commentId: String) {
        viewModelScope.launch {
            commentRepository.markHelpful("stops", stopId, commentId)
        }
    }

    fun checkIn() {
        if (_uiState.value.hasCheckedInRecently) return
        viewModelScope.launch {
            try {
                activityRepository.checkIn(stopId)
                gamificationRepository.awardPoints(PointAction.CHECK_IN)
                _uiState.update { it.copy(hasCheckedInRecently = true, pointsBurst = PointAction.CHECK_IN.points) }
            } catch (_: Exception) { }
        }
    }

    fun submitQuickReport(type: String) {
        viewModelScope.launch {
            try {
                activityRepository.postActivity(stopId, type)
                gamificationRepository.awardPoints(PointAction.CHECK_IN)
                _uiState.update { it.copy(pointsBurst = PointAction.CHECK_IN.points) }
            } catch (_: Exception) { }
        }
    }

    fun uploadPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                photoRepository.uploadPhoto(stopId, bitmap)
                gamificationRepository.awardPoints(PointAction.PHOTO)
                _uiState.update { it.copy(pointsBurst = PointAction.PHOTO.points) }
            } catch (_: Exception) { }
        }
    }

    fun clearPointsBurst() {
        _uiState.update { it.copy(pointsBurst = null) }
    }

    fun loadStop(stopId: String) {
        // kept for backwards compatibility with existing calls
    }
}
