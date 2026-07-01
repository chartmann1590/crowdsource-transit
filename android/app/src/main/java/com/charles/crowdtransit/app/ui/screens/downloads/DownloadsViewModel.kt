package com.charles.crowdtransit.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.local.entities.CachedAgencyEntity
import com.charles.crowdtransit.app.data.remote.TransitlandOperator
import com.charles.crowdtransit.app.data.repository.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val query: String = "",
    val searchResults: List<TransitlandOperator> = emptyList(),
    val isSearching: Boolean = false,
    val downloadingOnestopId: String? = null,
    val downloadedCount: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val downloadedAgencies: StateFlow<List<CachedAgencyEntity>> =
        offlineRepository.observeDownloadedAgencies()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val query = _uiState.value.query
        if (query.length < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val results = offlineRepository.searchOperators(query)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, error = "Search failed: ${e.message}") }
            }
        }
    }

    fun download(operator: TransitlandOperator) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingOnestopId = operator.onestopId, downloadedCount = 0, error = null) }
            try {
                offlineRepository.downloadAgency(
                    onestopId = operator.onestopId,
                    name = operator.name ?: operator.shortName ?: operator.onestopId,
                    onProgress = { count -> _uiState.update { it.copy(downloadedCount = count) } },
                )
                _uiState.update { it.copy(downloadingOnestopId = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(downloadingOnestopId = null, error = "Download failed: ${e.message}") }
            }
        }
    }

    fun remove(onestopId: String) {
        viewModelScope.launch {
            offlineRepository.removeAgency(onestopId)
        }
    }
}
