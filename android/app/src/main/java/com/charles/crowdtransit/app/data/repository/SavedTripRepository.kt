package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.TripPlan
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved trips at savedTrips/{uid}/{tripId}: origin + destination only (no departure/
 * arrival times or legs). A saved trip is a place-to-place shortcut, not a frozen
 * schedule snapshot — schedules change day to day, so opening a saved trip always
 * re-plans from the current time to show accurate, up-to-date options (web twin:
 * web/src/firebase/savedTrips.ts). Client-side cap keeps RTDB Spark-safe.
 */
@Singleton
class SavedTripRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {
    companion object {
        const val MAX_SAVED_TRIPS = 30
    }

    data class SavedTrip(
        val tripId: String,
        val fromName: String,
        val fromLat: Double,
        val fromLng: Double,
        val toName: String,
        val toLat: Double,
        val toLng: Double,
        val createdAt: Long,
    )

    val isSignedIn: Boolean get() = auth.currentUser != null

    fun observeSavedTrips(): Flow<List<SavedTrip>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return db.reference.child("savedTrips/$uid").observeAsFlow()
            .map { snapshot ->
                snapshot?.children?.mapNotNull { it.toSavedTrip() }?.sortedByDescending { it.createdAt }
                    ?: emptyList()
            }
    }

    /** Saves the trip's origin/destination only — never its computed times. */
    suspend fun saveTrip(plan: TripPlan): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val listRef = db.reference.child("savedTrips/$uid")
        val existing = listRef.get().await()
        if (existing.childrenCount >= MAX_SAVED_TRIPS) {
            throw IllegalStateException("Saved trip limit reached ($MAX_SAVED_TRIPS). Delete one first.")
        }
        val tripRef = listRef.push()
        tripRef.setValue(
            mapOf(
                "createdAt" to System.currentTimeMillis(),
                "fromName" to plan.from.name,
                "fromLat" to plan.from.lat,
                "fromLng" to plan.from.lng,
                "toName" to plan.to.name,
                "toLat" to plan.to.lat,
                "toLng" to plan.to.lng,
            ),
        ).await()
        return tripRef.key!!
    }

    suspend fun deleteTrip(tripId: String) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        db.reference.child("savedTrips/$uid/$tripId").removeValue().await()
    }

    private fun DataSnapshot.toSavedTrip(): SavedTrip? {
        val tripId = key ?: return null
        val fromLat = (child("fromLat").value as? Number)?.toDouble() ?: return null
        val fromLng = (child("fromLng").value as? Number)?.toDouble() ?: return null
        val toLat = (child("toLat").value as? Number)?.toDouble() ?: return null
        val toLng = (child("toLng").value as? Number)?.toDouble() ?: return null
        return SavedTrip(
            tripId = tripId,
            fromName = child("fromName").value as? String ?: "",
            fromLat = fromLat,
            fromLng = fromLng,
            toName = child("toName").value as? String ?: "",
            toLat = toLat,
            toLng = toLng,
            createdAt = child("createdAt").value as? Long ?: 0L,
        )
    }
}
