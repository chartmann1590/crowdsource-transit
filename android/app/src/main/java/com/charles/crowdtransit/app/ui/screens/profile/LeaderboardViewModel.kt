package com.charles.crowdtransit.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.AuthRepository
import com.charles.crowdtransit.app.data.repository.GamificationRepository
import com.charles.crowdtransit.model.LeaderboardEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUiState(
    val entries: List<Pair<String, LeaderboardEntry>> = emptyList(),
    val currentUid: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState(currentUid = authRepository.currentUser?.uid))
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gamificationRepository.observeLeaderboard().collect { entries ->
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            }
        }
    }
}
