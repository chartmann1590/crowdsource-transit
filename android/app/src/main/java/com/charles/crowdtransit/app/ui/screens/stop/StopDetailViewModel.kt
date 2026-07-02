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
import com.charles.crowdtransit.model.Stop
import com.charles.crowdtransit.model.StopPhoto
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

    init {
        viewModelScope.launch {
            stopRepository.observeStop(stopId).collect { stop ->
                _uiState.update { it.copy(stop = stop, isLoading = false) }
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
