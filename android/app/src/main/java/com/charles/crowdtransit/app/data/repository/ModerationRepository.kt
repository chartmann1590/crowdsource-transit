package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User blocking for community content (Play UGC policy). Blocks are stored
 * per account at users/{uid}/blockedUsers/{blockedUid} (value: the blocked
 * user's display name, for the Settings list). Blocked users' reviews,
 * photos and check-ins are hidden from the blocking user; the blocked user
 * is never notified.
 */
@Singleton
class ModerationRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    data class BlockedUser(val userId: String, val displayName: String)

    fun observeBlockedUsers(): Flow<List<BlockedUser>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return db.reference.child("users/$uid/blockedUsers").observeAsFlow()
            .map { snapshot ->
                snapshot?.children?.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    BlockedUser(id, child.getValue(String::class.java) ?: "Rider")
                } ?: emptyList()
            }
    }

    suspend fun blockUser(userId: String, displayName: String) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        require(userId != uid) { "You cannot block yourself" }
        db.reference.child("users/$uid/blockedUsers/$userId").setValue(displayName).await()
    }

    suspend fun unblockUser(userId: String) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        db.reference.child("users/$uid/blockedUsers/$userId").removeValue().await()
    }
}