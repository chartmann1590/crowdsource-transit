package com.charles.crowdtransit.app.ui.screens.stop

import android.app.Activity
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.CommentRepository
import com.charles.crowdtransit.app.data.repository.GamificationRepository
import com.charles.crowdtransit.app.data.repository.PhotoRepository
import com.charles.crowdtransit.app.data.repository.RatingRepository
import com.charles.crowdtransit.app.data.review.ReviewPrompter
import com.charles.crowdtransit.model.PointAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RateStopUiState(
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RateStopViewModel @Inject constructor(
    private val ratingRepository: RatingRepository,
    private val commentRepository: CommentRepository,
    private val photoRepository: PhotoRepository,
    private val gamificationRepository: GamificationRepository,
    private val reviewPrompter: ReviewPrompter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RateStopUiState())
    val uiState: StateFlow<RateStopUiState> = _uiState.asStateFlow()

    fun submit(
        stopId: String,
        overall: Int,
        subcategories: Map<String, Int>,
        text: String,
        transitType: String,
        isAnonymous: Boolean,
        photo: Bitmap?,
    ) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                ratingRepository.submitRating(
                    targetType = "stops",
                    targetId = stopId,
                    overall = overall,
                    subcategories = subcategories,
                    transitType = transitType,
                    isAnonymous = isAnonymous,
                )
                if (text.isNotBlank()) {
                    commentRepository.addComment(
                        targetType = "stops",
                        targetId = stopId,
                        text = text,
                        rating = overall,
                        transitType = transitType,
                        isAnonymous = isAnonymous,
                    )
                }
                gamificationRepository.awardPoints(PointAction.REVIEW)
                if (photo != null) {
                    photoRepository.uploadPhoto(stopId, photo)
                    gamificationRepository.awardPoints(PointAction.PHOTO)
                }
                _uiState.update { it.copy(isSubmitting = false, submitted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }

    /** Called once the UI has confirmed the rating was submitted and has an Activity to work with. */
    fun maybeRequestReview(activity: Activity) {
        viewModelScope.launch { reviewPrompter.maybeRequestReview(activity) }
    }
}
