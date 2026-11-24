package com.charles.crowdtransit.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.repository.AuthRepository
import com.charles.crowdtransit.app.data.repository.GamificationRepository
import com.charles.crowdtransit.model.Level
import com.charles.crowdtransit.model.UserProfile
import com.charles.crowdtransit.model.UserStats
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
    val stats: UserStats = UserStats(),
    val badges: Map<String, Boolean> = emptyMap(),
    val level: Level = Level.PEDESTRIAN,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val accountDeleted: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val gamificationRepository: GamificationRepository,
    private val db: FirebaseDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        val firebaseUser = authRepository.currentUser
        _uiState.update { it.copy(user = firebaseUser) }
        if (firebaseUser != null) {
            loadProfile(firebaseUser.uid)
            observeGamification(firebaseUser.uid)
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

    private fun observeGamification(uid: String) {
        viewModelScope.launch {
            gamificationRepository.observeUserStats(uid).collect { stats ->
                val s = stats ?: UserStats()
                _uiState.update {
                    it.copy(
                        stats = s,
                        level = Level.forPoints(s.points),
                        reviewCount = s.reviewCount,
                        stopsAdded = s.stopsAdded,
                    )
                }
            }
        }
        viewModelScope.launch {
            gamificationRepository.observeBadges(uid).collect { badges ->
                _uiState.update { it.copy(badges = badges) }
            }
        }
    }

    /** Whether the delete-account dialog should ask for the account password. */
    val canReauthenticateWithPassword: Boolean
        get() = authRepository.canReauthenticateWithPassword()

    private val isGoogleOnlyAccount: Boolean
        get() = authRepository.isGoogleOnlyAccount()

    /**
     * Deletes the account (Profile -> Delete Account). When [password] is
     * given it re-authenticates first, which covers Firebase's recent-login
     * requirement for password accounts.
     */
    fun deleteAccount(password: String?) {
        viewModelScope.launch {
            // Google-only accounts cannot be re-authenticated in this app
            // (it has no Google sign-in flow), so route them to the web
            // deletion page BEFORE anything is deleted.
            if (isGoogleOnlyAccount) {
                _uiState.update {
                    it.copy(
                        deleteError = "This account uses Google sign-in, which the app cannot re-verify. To delete the account, use the Account Deletion page linked from the Privacy Policy instead.",
                    )
                }
                return@launch
            }
            // Password accounts must confirm with their password, so the
            // recent-login guard in the repository passes before any data
            // cleanup starts.
            if (canReauthenticateWithPassword && password.isNullOrEmpty()) {
                _uiState.update {
                    it.copy(deleteError = "Enter your password to confirm deletion.")
                }
                return@launch
            }
            _uiState.update { it.copy(isDeleting = true, deleteError = null) }
            try {
                if (!password.isNullOrEmpty()) {
                    authRepository.reauthenticateWithPassword(password)
                }
                authRepository.deleteAccount()
                _uiState.update { it.copy(isDeleting = false, accountDeleted = true) }
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = "For your security, Google needs a fresh sign-in before an account can be deleted. Sign out, sign back in, then try again.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = e.message ?: "Account deletion failed. Please try again.",
                    )
                }
            }
        }
    }

    fun dismissDeleteError() {
        _uiState.update { it.copy(deleteError = null) }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
