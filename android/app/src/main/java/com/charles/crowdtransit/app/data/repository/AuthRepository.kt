package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInAnonymously(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        val user = result.user!!
        ensureUserProfile(user)
        return user
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return if (auth.currentUser?.isAnonymous == true) {
            val result = auth.currentUser!!.linkWithCredential(credential).await()
            val user = result.user!!
            ensureUserProfile(user)
            user
        } else {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user!!
            ensureUserProfile(user)
            user
        }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user!!
    }

    suspend fun createAccount(email: String, password: String, displayName: String): FirebaseUser {
        val result = if (auth.currentUser?.isAnonymous == true) {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            auth.currentUser!!.linkWithCredential(credential).await()
        } else {
            auth.createUserWithEmailAndPassword(email, password).await()
        }
        val user = result.user!!
        val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profileUpdate).await()
        ensureUserProfile(user, displayName)
        return user
    }

    fun signOut() = auth.signOut()

    private suspend fun ensureUserProfile(user: FirebaseUser, displayName: String? = null) {
        val ref = db.reference.child("users/${user.uid}")
        val exists = ref.get().await().exists()
        if (!exists) {
            val name = displayName ?: user.displayName ?: "Rider"
            val initials = name.split(" ").take(2).map { it.firstOrNull()?.uppercaseChar() ?: 'R' }.joinToString("")
            val profile = UserProfile(
                userId = user.uid,
                displayName = name,
                isAnonymous = user.isAnonymous,
                avatarInitials = initials,
                joinedAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis(),
            )
            ref.setValue(profile).await()
        } else {
            ref.child("lastActiveAt").setValue(System.currentTimeMillis()).await()
        }
    }
}
