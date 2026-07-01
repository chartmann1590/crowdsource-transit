package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.GeoFireHelper
import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.Stop
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val geoFireHelper: GeoFireHelper,
) {

    fun observeStop(stopId: String): Flow<Stop?> =
        db.reference.child("stops").child(stopId)
            .observeAsFlow()
            .map { it?.getValue<Stop>() }

    suspend fun getStopsNearby(lat: Double, lng: Double, radiusKm: Double): List<Stop> {
        val nearbyIds = geoFireHelper.queryRadius(lat, lng, radiusKm)
        return nearbyIds.mapNotNull { stopId ->
            db.reference.child("stops").child(stopId).get().await().getValue<Stop>()
        }
    }

    suspend fun getStop(stopId: String): Stop? =
        db.reference.child("stops").child(stopId)
            .get().await()
            .getValue<Stop>()

    suspend fun getStopsByIds(ids: List<String>): List<Stop> =
        ids.mapNotNull { getStop(it) }
}
