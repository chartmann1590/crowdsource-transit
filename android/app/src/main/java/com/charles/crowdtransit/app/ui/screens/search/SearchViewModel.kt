package com.charles.crowdtransit.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.StopRepository
import com.charles.crowdtransit.model.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Stop> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val stopRepository: StopRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var searchVersion = 0

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false, error = null) }
            return
        }
        val thisVersion = ++searchVersion
        searchJob = viewModelScope.launch {
            delay(300)
            if (thisVersion != searchVersion || !isActive) return@launch
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val results = stopRepository.searchStops(query)
                if (thisVersion == searchVersion) {
                    _uiState.update { it.copy(results = results, isSearching = false) }
                }
            } catch (e: Exception) {
                if (thisVersion == searchVersion) {
                    _uiState.update { it.copy(isSearching = false, error = "Search failed: ${e.message}") }
                }
            }
        }
    }

    fun search() {
        // handled by onQueryChanged with debounce
    }
}
