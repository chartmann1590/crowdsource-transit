package com.charles.crowdtransit.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.data.repository.ModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: UserPreferencesStore,
    private val moderationRepository: ModerationRepository,
) : ViewModel() {

    val useImperialUnits: StateFlow<Boolean> = preferencesStore.useImperialUnits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleUnits() {
        viewModelScope.launch {
            preferencesStore.setUseImperialUnits(!useImperialUnits.value)
        }
    }

    /** Users this account has blocked (Play UGC policy); managed in Settings. */
    val blockedUsers: StateFlow<List<ModerationRepository.BlockedUser>> =
        moderationRepository.observeBlockedUsers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            try {
                moderationRepository.unblockUser(userId)
            } catch (_: Exception) { }
        }
    }
}
