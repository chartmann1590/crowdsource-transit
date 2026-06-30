package com.charles.crowdtransit.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.AuthRepository
import com.charles.crowdtransit.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ProfileUiState(
    val user: FirebaseUser? = null,
    val profile: UserProfile? = null,
    val reviewCount: Long = 0,
    val stopsAdded: Long = 0,
    val helpfulVotes: Long = 0,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val db: FirebaseDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        val firebaseUser = authRepository.currentUser
        _uiState.update { it.copy(user = firebaseUser) }
        if (firebaseUser != null) {
            loadProfile(firebaseUser.uid)
        }
    }

    private fun loadProfile(uid: String) {
        viewModelScope.launch {
            try {
                val snap = db.reference.child("users/$uid").get().await()
                val profile = snap.getValue<UserProfile>()
                _uiState.update {
                    it.copy(
                        profile = profile,
                        reviewCount = profile?.stats?.reviewCount ?: 0,
                        stopsAdded = profile?.stats?.stopsAdded ?: 0,
                        helpfulVotes = profile?.stats?.helpfulVotes ?: 0,
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
