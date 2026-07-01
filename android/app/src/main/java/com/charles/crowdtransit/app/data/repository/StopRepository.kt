package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.app.data.remote.TransitlandApi
import com.charles.crowdtransit.app.data.remote.TransitlandStop
import com.charles.crowdtransit.app.data.remote.gtfsRouteTypeToTransitType
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val MAX_RADIUS_METERS = 10_000
private const val MAX_TRANSIT_TYPE_LOOKUPS = 15

@Singleton
class StopRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val transitlandApi: TransitlandApi,
    private val offlineRepository: OfflineRepository,
) {

    private fun statsFrom(snapshot: DataSnapshot?): Triple<Long, Long, Long> = Triple(
        snapshot?.child("ratingSum")?.getValue(Long::class.java) ?: 0L,
        snapshot?.child("ratingCount")?.getValue(Long::class.java) ?: 0L,
        snapshot?.child("commentCount")?.getValue(Long::class.java) ?: 0L,
    )

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLng = (lng2 - lng1) * Math.PI / 180.0
        val a = sin(dLat / 2).pow(2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private suspend fun transitTypesFor(stop: TransitlandStop): List<String> {
        val coordinates = stop.geometry?.coordinates.orEmpty()
        val lng = coordinates.getOrNull(0) ?: return emptyList()
        val lat = coordinates.getOrNull(1) ?: return emptyList()
        return try {
            transitlandApi.getRoutesNear(lat, lng).routes
                .mapNotNull { gtfsRouteTypeToTransitType(it.routeType).takeIf { t -> t != "transit" } }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun enrichedStop(remote: TransitlandStop): Stop {
        val stopId = remote.onestopId ?: ""
        val statsSnapshot = db.reference.child("stopStats").child(stopId).get().await()
        val (ratingSum, ratingCount, commentCount) = statsFrom(statsSnapshot)
        val transitTypes = transitTypesFor(remote)
        return remote.toStop(ratingSum, ratingCount, commentCount).copy(transitTypes = transitTypes)
    }

    fun observeStop(stopId: String): Flow<Stop?> =
        db.reference.child("stopStats").child(stopId)
            .observeAsFlow()
            .map { snapshot ->
                val remote = transitlandApi.getStopByOnestopId(stopId).stops.firstOrNull() ?: return@map null
                val (ratingSum, ratingCount, commentCount) = statsFrom(snapshot)
                val transitTypes = transitTypesFor(remote)
                remote.toStop(ratingSum, ratingCount, commentCount).copy(transitTypes = transitTypes)
            }

    suspend fun getStopsNearby(lat: Double, lng: Double, radiusKm: Double): List<Stop> {
        return try {
            coroutineScope {
                val radiusMeters = (radiusKm * 1000).toInt().coerceIn(1, MAX_RADIUS_METERS)
                val remoteStops = transitlandApi.getStopsNearby(lat, lng, radiusMeters).stops
                val sortedByDistance = remoteStops.sortedBy { remote ->
                    val coords = remote.geometry?.coordinates.orEmpty()
                    val stopLng = coords.getOrNull(0) ?: 0.0
                    val stopLat = coords.getOrNull(1) ?: 0.0
                    haversineKm(lat, lng, stopLat, stopLng)
                }
                sortedByDistance.take(MAX_TRANSIT_TYPE_LOOKUPS).map { remote ->
                    async { enrichedStop(remote) }
                }.map { it.await() }
            }
        } catch (e: Exception) {
            offlineRepository.getCachedStopsNearby(lat, lng, radiusKm)
        }
    }

    suspend fun searchStops(query: String): List<Stop> {
        return try {
            coroutineScope {
                val remoteStops = transitlandApi.searchStops(query).stops
                remoteStops.map { remote ->
                    async { enrichedStop(remote) }
                }.map { it.await() }
            }
        } catch (e: Exception) {
            offlineRepository.searchCachedStops(query)
        }
    }

    suspend fun getStop(stopId: String): Stop? {
        return try {
            val remote = transitlandApi.getStopByOnestopId(stopId).stops.firstOrNull() ?: return null
            enrichedStop(remote)
        } catch (e: Exception) {
            offlineRepository.getCachedStop(stopId)
        }
    }

    suspend fun getStopsByIds(ids: List<String>): List<Stop> = ids.mapNotNull { getStop(it) }
}
