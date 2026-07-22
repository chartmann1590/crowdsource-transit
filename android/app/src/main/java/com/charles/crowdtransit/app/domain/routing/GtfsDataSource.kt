package com.charles.crowdtransit.app.domain.routing

/**
 * All I/O the trip router needs, pluggable per docs/routing/router-spec.md.
 * Implementations: TransitlandDataSource (live API, data/routing/) and — later —
 * RoomGtfsDataSource (offline downloaded GTFS). Implementations throw
 * [RateLimitedException] to abort the whole search when throttled.
 */
interface GtfsDataSource {
    suspend fun stopsNear(lat: Double, lng: Double, radiusM: Int, limit: Int): List<StopCandidate>

    suspend fun departures(stopKey: String, notBeforeUtcMs: Long, windowSec: Int): List<DepartureOption>

    /** null when the trip can't be fetched (e.g. frequency-based synthetic trips) — caller skips it. */
    suspend fun tripDetails(routeOnestopId: String, tripIntId: Long): TripDetails?
}

class RateLimitedException : Exception("transit data rate limit exceeded")

data class PlanRequest(
    val fromName: String,
    val fromLat: Double,
    val fromLng: Double,
    val toName: String,
    val toLat: Double,
    val toLng: Double,
    /** Epoch ms; null = now. */
    val departAtMs: Long? = null,
)

data class StopCandidate(
    /** Key accepted by [GtfsDataSource.departures] (Transitland: onestop_id or integer id string). */
    val key: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

data class RouteMeta(
    val onestopId: String,
    val shortName: String,
    val longName: String,
    val color: String,
    val agency: String,
    /** Itinerary mode string: bus | subway | train | tram | ferry | transit */
    val mode: String,
)

data class DepartureOption(
    val route: RouteMeta,
    val headsign: String,
    /** Transitland internal integer trip id — the /routes/{key}/trips/{id} path key. */
    val tripIntId: Long,
    /** Position of this stop within the trip's stop sequence. */
    val boardStopSequence: Int,
    val depUtcMs: Long,
)

data class TripStopTime(
    val seq: Int,
    val gtfsStopId: String,
    /** Transitland integer stop id as string; usable as a [StopCandidate.key] for transfers. */
    val stopKey: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** Seconds since local service-day midnight; may exceed 86400. */
    val arrivalSec: Int,
    val departureSec: Int,
)

data class LngLat(val lng: Double, val lat: Double)

data class TripDetails(
    val gtfsTripId: String,
    val headsign: String,
    val stopTimes: List<TripStopTime>,
    /** Shape points with any elevation element stripped. */
    val shape: List<LngLat>? = null,
)
