package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.app.data.remote.TransitlandApi
import com.charles.crowdtransit.app.data.remote.TransitlandStop
import com.charles.crowdtransit.app.data.remote.gtfsRouteTypeToTransitType
import com.charles.crowdtransit.app.data.remote.toRoutesAndSchedule
import com.charles.crowdtransit.app.data.remote.toStop
import com.charles.crowdtransit.app.data.remote.toStaticRoutes
import com.charles.crowdtransit.app.data.remote.RouteAndSchedule
import com.charles.crowdtransit.app.data.remote.toRouteWithStops
import com.charles.crowdtransit.model.RouteWithStops
import com.charles.crowdtransit.model.ServedDeparture
import com.charles.crowdtransit.model.ServedRoute
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
private const val SEARCH_BIAS_RADIUS_METERS = 50_000

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
                val isTransitlandId = stopId.startsWith("r-") || stopId.startsWith("s-") ||
                        stopId.startsWith("d-") || stopId.startsWith("f-")
                var stop: Stop? = null
                if (isTransitlandId) {
                    val remote = try {
                        transitlandApi.getStopByOnestopId(stopId).stops.firstOrNull()
                    } catch (_: Exception) {
                        null
                    }
                    if (remote != null) {
                        val (ratingSum, ratingCount, commentCount) = statsFrom(snapshot)
                        val transitTypes = transitTypesFor(remote)
                        stop = remote.toStop(ratingSum, ratingCount, commentCount).copy(transitTypes = transitTypes)
                    }
                }
                if (stop == null) {
                    val fbStopSnapshot = try {
                        db.reference.child("stops").child(stopId).get().await()
                    } catch (_: Exception) {
                        null
                    }
                    if (fbStopSnapshot != null && fbStopSnapshot.exists()) {
                        val fbStop = fbStopSnapshot.getValue(Stop::class.java)
                        if (fbStop != null) {
                            val (ratingSum, ratingCount, commentCount) = statsFrom(snapshot)
                            stop = fbStop.copy(
                                ratingSum = ratingSum,
                                ratingCount = ratingCount,
                                commentCount = commentCount
                            )
                        }
                    }
                }
                stop
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
                }.map { it.await() }.distinctBy { it.stopId }
            }
        } catch (e: Exception) {
            offlineRepository.getCachedStopsNearby(lat, lng, radiusKm)
        }
    }

    // Transitland's /stops endpoint can return the same onestop_id more than once (e.g.
    // across duplicate feed entries); dedupe since stopId is used as a LazyColumn key
    // downstream and duplicates crash with "Key ... was already used".
    //
    // `near` biases results toward a location: Transitland's text search matches stop
    // names anywhere (e.g. "Schenectady" can hit Brooklyn's "Schenectady Ave" ahead of
    // the actual city of Schenectady, NY), so when a bias point is known we first try a
    // radius-scoped search and only fall back to the unscoped one if that finds nothing —
    // preserving the ability to search for a stop far from the bias point.
    suspend fun searchStops(query: String, near: Pair<Double, Double>? = null): List<Stop> {
        return try {
            coroutineScope {
                val biased = near?.let { (lat, lng) ->
                    transitlandApi.searchStops(query, lat = lat, lon = lng, radiusMeters = SEARCH_BIAS_RADIUS_METERS).stops
                }
                val remoteStops = if (!biased.isNullOrEmpty()) biased else transitlandApi.searchStops(query).stops
                remoteStops.map { remote ->
                    async { enrichedStop(remote) }
                }.map { it.await() }.distinctBy { it.stopId }
            }
        } catch (e: Exception) {
            offlineRepository.searchCachedStops(query)
        }
    }

    suspend fun getStop(stopId: String): Stop? {
        return try {
            val isTransitlandId = stopId.startsWith("r-") || stopId.startsWith("s-") ||
                    stopId.startsWith("d-") || stopId.startsWith("f-")
            var stop: Stop? = null
            if (isTransitlandId) {
                val remote = transitlandApi.getStopByOnestopId(stopId).stops.firstOrNull()
                if (remote != null) {
                    stop = enrichedStop(remote)
                }
            }
            if (stop == null) {
                val fbStopSnapshot = db.reference.child("stops").child(stopId).get().await()
                if (fbStopSnapshot.exists()) {
                    stop = fbStopSnapshot.getValue(Stop::class.java)
                }
            }
            stop ?: offlineRepository.getCachedStop(stopId)
        } catch (e: Exception) {
            offlineRepository.getCachedStop(stopId)
        }
    }

    suspend fun getStopsByIds(ids: List<String>): List<Stop> = ids.mapNotNull { getStop(it) }

    /**
     * Fetches routes serving a stop + upcoming departures via the Transitland
     * stop-departures endpoint. Returns empty/throws gracefully on rate-limit.
     */
    suspend fun getRoutesAndScheduleForStop(onestopStopId: String): RouteAndSchedule {
        return try {
            val response = transitlandApi.getStopDepartures(onestopStopId)
            response.toRoutesAndSchedule()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 429 || e.code() == 403) throw TransitlandRateLimitException(e)
            throw e
        }
    }

    suspend fun getAgency(agencyId: String): com.charles.crowdtransit.model.Agency? = try {
        val snapshot = db.reference.child("agencies").child(agencyId).get().await()
        if (snapshot.exists()) {
            snapshot.getValue(com.charles.crowdtransit.model.Agency::class.java)
        } else null
    } catch (_: Exception) {
        null
    }

    suspend fun getRoute(routeId: String): com.charles.crowdtransit.model.Route? = try {
        val snapshot = db.reference.child("routes").child(routeId).get().await()
        if (snapshot.exists()) {
            snapshot.getValue(com.charles.crowdtransit.model.Route::class.java)
        } else null
    } catch (_: Exception) {
        null
    }

    suspend fun getStaticRoutesForStop(onestopStopId: String): List<ServedRoute> = try {
        val response = transitlandApi.getStopByOnestopId(onestopStopId).stops.firstOrNull()
        response?.toStaticRoutes() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun getRouteWithStops(onestopRouteId: String): RouteWithStops? = try {
        transitlandApi.getRouteWithStops(onestopRouteId).routes.firstOrNull()?.toRouteWithStops()
    } catch (_: Exception) {
        null
    }

    /**
     * Resolves a Transitland stop's onestop_id from its coordinates. Route detail only gives
     * stop name/lat/lng (no onestop_id), so this does a tight-radius nearby lookup to find the
     * matching stop when the user taps into it from a route's stop list.
     */
    suspend fun resolveStopIdNear(lat: Double, lng: Double): String? = try {
        transitlandApi.getStopsNearby(lat, lng, radiusMeters = 50, limit = 1).stops.firstOrNull()?.onestopId
    } catch (_: Exception) {
        null
    }
}

/** Thrown when the Transitland free-tier rate limit is hit. */
class TransitlandRateLimitException(cause: Throwable? = null) : Exception("Transitland rate limited", cause)
