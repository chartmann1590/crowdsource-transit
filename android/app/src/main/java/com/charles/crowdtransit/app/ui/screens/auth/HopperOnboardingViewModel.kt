package com.charles.crowdtransit.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HopperOnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferencesStore,
) : ViewModel() {
    fun enable() {
        viewModelScope.launch {
            userPreferences.setAssistantEnabled(true)
            userPreferences.setAssistantOnboardingSeen(true)
        }
    }
}
