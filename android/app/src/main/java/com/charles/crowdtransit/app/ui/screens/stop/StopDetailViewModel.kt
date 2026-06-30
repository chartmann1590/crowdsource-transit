package com.charles.crowdtransit.app.ui.screens.stop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.CommentRepository
import com.charles.crowdtransit.app.data.repository.RatingRepository
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.model.Comment
import com.charles.crowdtransit.model.Rating
import com.charles.crowdtransit.model.Stop
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
)

@HiltViewModel
class StopDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stopRepository: StopRepository,
    private val commentRepository: CommentRepository,
    private val ratingRepository: RatingRepository,
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
    }

    fun markHelpful(commentId: String) {
        viewModelScope.launch {
            commentRepository.markHelpful("stops", stopId, commentId)
        }
    }

    fun loadStop(stopId: String) {
        // kept for backwards compatibility with existing calls
    }
}
