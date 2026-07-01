package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.app.data.remote.TransitlandApi
import com.charles.crowdtransit.app.data.remote.toStop
import com.charles.crowdtransit.model.Stop
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_RADIUS_METERS = 10_000

@Singleton
class StopRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val transitlandApi: TransitlandApi,
) {

    private fun statsFrom(snapshot: DataSnapshot?): Triple<Long, Long, Long> = Triple(
        snapshot?.child("ratingSum")?.getValue(Long::class.java) ?: 0L,
        snapshot?.child("ratingCount")?.getValue(Long::class.java) ?: 0L,
        snapshot?.child("commentCount")?.getValue(Long::class.java) ?: 0L,
    )

    private suspend fun fetchStop(stopId: String): Stop? {
        val remote = transitlandApi.getStopByOnestopId(stopId).stops.firstOrNull() ?: return null
        val statsSnapshot = db.reference.child("stopStats").child(stopId).get().await()
        val (ratingSum, ratingCount, commentCount) = statsFrom(statsSnapshot)
        return remote.toStop(ratingSum, ratingCount, commentCount)
    }

    fun observeStop(stopId: String): Flow<Stop?> =
        db.reference.child("stopStats").child(stopId)
            .observeAsFlow()
            .map { snapshot ->
                val remote = transitlandApi.getStopByOnestopId(stopId).stops.firstOrNull() ?: return@map null
                val (ratingSum, ratingCount, commentCount) = statsFrom(snapshot)
                remote.toStop(ratingSum, ratingCount, commentCount)
            }

    suspend fun getStopsNearby(lat: Double, lng: Double, radiusKm: Double): List<Stop> = coroutineScope {
        val radiusMeters = (radiusKm * 1000).toInt().coerceIn(1, MAX_RADIUS_METERS)
        val remoteStops = transitlandApi.getStopsNearby(lat, lng, radiusMeters).stops
        remoteStops.map { remote ->
            async {
                val stopId = remote.onestopId ?: ""
                val statsSnapshot = db.reference.child("stopStats").child(stopId).get().await()
                val (ratingSum, ratingCount, commentCount) = statsFrom(statsSnapshot)
                remote.toStop(ratingSum, ratingCount, commentCount)
            }
        }.map { it.await() }
    }

    suspend fun searchStops(query: String): List<Stop> = coroutineScope {
        val remoteStops = transitlandApi.searchStops(query).stops
        remoteStops.map { remote ->
            async {
                val stopId = remote.onestopId ?: ""
                val statsSnapshot = db.reference.child("stopStats").child(stopId).get().await()
                val (ratingSum, ratingCount, commentCount) = statsFrom(statsSnapshot)
                remote.toStop(ratingSum, ratingCount, commentCount)
            }
        }.map { it.await() }
    }

    suspend fun getStop(stopId: String): Stop? = fetchStop(stopId)

    suspend fun getStopsByIds(ids: List<String>): List<Stop> = ids.mapNotNull { getStop(it) }
}
