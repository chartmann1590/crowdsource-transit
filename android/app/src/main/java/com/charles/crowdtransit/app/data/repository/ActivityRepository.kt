package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.ActivityEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Live check-ins & quick reports. Live for 90 minutes, client-filtered by timestamp — no server TTL. */
private val LIVE_WINDOW_MS = TimeUnit.MINUTES.toMillis(90)

@Singleton
class ActivityRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    /** Emits only events from the last 90 minutes, newest first. */
    fun observeActivity(stopId: String): Flow<List<ActivityEvent>> =
        db.reference.child("activity/$stopId").observeAsFlow().map { snapshot ->
            val cutoff = System.currentTimeMillis() - LIVE_WINDOW_MS
            snapshot?.children?.mapNotNull { child ->
                child.getValue<ActivityEvent>()?.copy(activityId = child.key ?: "")
            }?.filter { it.timestamp >= cutoff }
                ?.sortedByDescending { it.timestamp }
                ?: emptyList()
        }

    suspend fun postActivity(stopId: String, type: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val ref = db.reference.child("activity/$stopId").push()
        val event = ActivityEvent(
            activityId = ref.key ?: "",
            uid = user.uid,
            type = type,
            timestamp = System.currentTimeMillis(),
        )
        ref.setValue(event).await()
    }

    suspend fun checkIn(stopId: String) = postActivity(stopId, com.charles.crowdtransit.model.ActivityType.CHECKIN)
}
