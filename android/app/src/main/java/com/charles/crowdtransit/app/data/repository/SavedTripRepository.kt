package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.app.data.trip.TripPlanCodec
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
 * Saved trips at savedTrips/{uid}/{tripId}: { plan: <encoded blob>, createdAt, fromName,
 * toName }. Same wire format as share links, so web and app read each other's saves
 * (web twin: web/src/firebase/savedTrips.ts). Client-side cap keeps RTDB Spark-safe.
 */
@Singleton
class SavedTripRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val codec: TripPlanCodec,
) {
    companion object {
        const val MAX_SAVED_TRIPS = 30
    }

    data class SavedTrip(
        val tripId: String,
        val fromName: String,
        val toName: String,
        val createdAt: Long,
        /** Encoded blob (decode lazily — lists don't need the full plan). */
        val plan: String,
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
                "plan" to codec.encode(plan),
                "createdAt" to System.currentTimeMillis(),
                "fromName" to plan.from.name,
                "toName" to plan.to.name,
            ),
        ).await()
        return tripRef.key!!
    }

    suspend fun deleteTrip(tripId: String) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        db.reference.child("savedTrips/$uid/$tripId").removeValue().await()
    }

    fun decode(trip: SavedTrip): TripPlan? = runCatching { codec.decode(trip.plan) }.getOrNull()

    private fun DataSnapshot.toSavedTrip(): SavedTrip? {
        val tripId = key ?: return null
        val plan = child("plan").value as? String ?: return null
        return SavedTrip(
            tripId = tripId,
            fromName = child("fromName").value as? String ?: "",
            toName = child("toName").value as? String ?: "",
            createdAt = child("createdAt").value as? Long ?: 0L,
            plan = plan,
        )
    }
}
