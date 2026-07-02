package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.Badges
import com.charles.crowdtransit.model.LeaderboardEntry
import com.charles.crowdtransit.model.PointAction
import com.charles.crowdtransit.model.UserStats
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamificationRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun observeUserStats(uid: String): Flow<UserStats?> =
        db.reference.child("users/$uid/stats").observeAsFlow().map { it?.getValue<UserStats>() }

    fun observeBadges(uid: String): Flow<Map<String, Boolean>> =
        db.reference.child("users/$uid/badges").observeAsFlow().map { snapshot ->
            snapshot?.children?.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                key to (child.getValue(Boolean::class.java) ?: true)
            }?.toMap() ?: emptyMap()
        }

    fun observeLeaderboard(): Flow<List<Pair<String, LeaderboardEntry>>> =
        db.reference.child("leaderboard").observeAsFlow().map { snapshot ->
            snapshot?.children?.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                val entry = child.getValue<LeaderboardEntry>() ?: return@mapNotNull null
                uid to entry
            }?.sortedByDescending { it.second.points }?.take(50) ?: emptyList()
        }

    /**
     * Awards points for a successful contribution, fans out to /users/{uid}/stats and
     * /leaderboard/{uid}, bumps the relevant contribution counter, updates the daily streak,
     * and awards any badges newly unlocked. Call this right after the write it rewards succeeds.
     */
    suspend fun awardPoints(action: PointAction) {
        val user = auth.currentUser ?: return
        val uid = user.uid

        val profileSnap = db.reference.child("users/$uid").get().await()
        val displayName = if (user.isAnonymous) {
            "Anonymous Rider"
        } else {
            profileSnap.child("displayName").value as? String ?: user.displayName ?: "Rider"
        }

        val counterField = when (action) {
            PointAction.NEW_STOP -> "stopsAdded"
            PointAction.REVIEW -> "reviewCount"
            PointAction.PHOTO -> "photoCount"
            PointAction.CHECK_IN -> "checkinCount"
        }

        val statsRef = db.reference.child("users/$uid/stats")
        incrementAndAwait(statsRef.child("points"), action.points.toLong())
        incrementAndAwait(statsRef.child(counterField), 1L)
        updateStreak(uid)

        val newPoints = statsRef.child("points").get().await().getValue(Long::class.java) ?: 0L
        db.reference.child("leaderboard/$uid").setValue(
            LeaderboardEntry(displayName = displayName, points = newPoints)
        ).await()

        awardBadgesIfEligible(uid, action, statsRef)
    }

    private suspend fun updateStreak(uid: String) {
        val today = dayFormat.format(Date())
        val statsRef = db.reference.child("users/$uid/stats")
        val lastDate = statsRef.child("lastContributionDate").get().await().value as? String ?: ""
        if (lastDate == today) return // already contributed today, streak unchanged

        val yesterday = dayFormat.format(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)))
        val newStreak = if (lastDate == yesterday) {
            (statsRef.child("streakCount").get().await().getValue(Long::class.java) ?: 0L) + 1
        } else {
            1L
        }
        statsRef.child("lastContributionDate").setValue(today).await()
        statsRef.child("streakCount").setValue(newStreak).await()

        if (newStreak >= 7) {
            db.reference.child("users/$uid/badges/${Badges.SEVEN_DAY_STREAK}").setValue(true).await()
        }
    }

    private suspend fun awardBadgesIfEligible(
        uid: String,
        action: PointAction,
        statsRef: com.google.firebase.database.DatabaseReference,
    ) {
        val badgesRef = db.reference.child("users/$uid/badges")
        when (action) {
            PointAction.REVIEW -> {
                val count = statsRef.child("reviewCount").get().await().getValue(Long::class.java) ?: 0L
                if (count >= 1) badgesRef.child(Badges.FIRST_REVIEW).setValue(true).await()
                if (count >= 25) badgesRef.child(Badges.TWENTY_FIVE_REVIEWS).setValue(true).await()
            }
            PointAction.NEW_STOP -> {
                val count = statsRef.child("stopsAdded").get().await().getValue(Long::class.java) ?: 0L
                if (count >= 1) badgesRef.child(Badges.FIRST_STOP).setValue(true).await()
                if (count >= 10) badgesRef.child(Badges.TEN_STOPS).setValue(true).await()
            }
            PointAction.PHOTO -> {
                val count = statsRef.child("photoCount").get().await().getValue(Long::class.java) ?: 0L
                if (count >= 1) badgesRef.child(Badges.FIRST_PHOTO).setValue(true).await()
            }
            PointAction.CHECK_IN -> { /* no dedicated badge */ }
        }
    }

    private suspend fun incrementAndAwait(ref: com.google.firebase.database.DatabaseReference, delta: Long) {
        suspendCancellableCoroutine<Unit> { cont ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    currentData.value = (currentData.getValue(Long::class.java) ?: 0L) + delta
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
            })
        }
    }
}
