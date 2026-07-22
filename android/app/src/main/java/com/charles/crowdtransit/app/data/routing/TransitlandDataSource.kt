package com.charles.crowdtransit.app.data.routing

import com.charles.crowdtransit.app.data.remote.TransitlandApi
import com.charles.crowdtransit.app.data.remote.gtfsRouteTypeToTransitType
import com.charles.crowdtransit.app.domain.routing.DepartureOption
import com.charles.crowdtransit.app.domain.routing.GtfsDataSource
import com.charles.crowdtransit.app.domain.routing.LngLat
import com.charles.crowdtransit.app.domain.routing.RateLimitedException
import com.charles.crowdtransit.app.domain.routing.RouteMeta
import com.charles.crowdtransit.app.domain.routing.StopCandidate
import com.charles.crowdtransit.app.domain.routing.TripDetails
import com.charles.crowdtransit.app.domain.routing.TripStopTime
import retrofit2.HttpException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Live Transitland-backed [GtfsDataSource] with in-memory TTL caches
 * (docs/routing/router-spec.md: departures 5 min, trips 24 h, stopsNear 10 min).
 * HTTP 429/403 → [RateLimitedException] so the whole search aborts cleanly.
 */
@Singleton
class TransitlandDataSource @Inject constructor(
    private val api: TransitlandApi,
) : GtfsDataSource {

    class Cached<V>(val value: V)

    private class TtlCache<V>(private val ttlMs: Long, private val maxEntries: Int = 200) {
        private class Entry<V>(val cached: Cached<V>, val expiresAt: Long)

        private val map = LinkedHashMap<String, Entry<V>>()

        @Synchronized
        fun get(key: String): Cached<V>? {
            val hit = map[key] ?: return null
            if (System.currentTimeMillis() > hit.expiresAt) {
                map.remove(key)
                return null
            }
            return hit.cached
        }

        @Synchronized
        fun put(key: String, value: V) {
            if (map.size >= maxEntries) map.remove(map.keys.first())
            map[key] = Entry(Cached(value), System.currentTimeMillis() + ttlMs)
        }
    }

    private val stopsCache = TtlCache<List<StopCandidate>>(10L * 60 * 1000)
    private val depsCache = TtlCache<List<DepartureOption>>(5L * 60 * 1000)
    private val tripCache = TtlCache<TripDetails?>(24L * 3600 * 1000, 100)

    private inline fun <T> mapRateLimit(block: () -> T): T = try {
        block()
    } catch (e: HttpException) {
        if (e.code() == 429 || e.code() == 403) throw RateLimitedException() else throw e
    }

    override suspend fun stopsNear(lat: Double, lng: Double, radiusM: Int, limit: Int): List<StopCandidate> {
        val key = "%.4f,%.4f,%d,%d".format(lat, lng, radiusM, limit)
        stopsCache.get(key)?.let { return it.value }
        val response = mapRateLimit { api.getStopsNearby(lat, lng, radiusM, limit) }
        val seen = mutableSetOf<String>()
        val result = response.stops.mapNotNull { s ->
            val id = s.onestopId ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val coords = s.geometry?.coordinates.orEmpty()
            StopCandidate(
                key = id,
                name = s.stopName ?: id,
                lat = coords.getOrNull(1) ?: return@mapNotNull null,
                lng = coords.getOrNull(0) ?: return@mapNotNull null,
            )
        }
        stopsCache.put(key, result)
        return result
    }

    override suspend fun departures(stopKey: String, notBeforeUtcMs: Long, windowSec: Int): List<DepartureOption> {
        // Transitland's `next` window starts at "now"; extend it to cover a future notBefore
        // (the router filters by notBefore afterwards). Bucket the cache key so nearby
        // notBefore values share one fetch.
        val nextSec = (maxOf(60.0, ceil((notBeforeUtcMs - System.currentTimeMillis()) / 1000.0)).toInt() + windowSec)
            .coerceAtMost(86400)
        val key = "$stopKey:${nextSec / 1800}"
        depsCache.get(key)?.let { return it.value }

        val response = mapRateLimit { api.getStopDepartures(stopKey, nextSeconds = nextSec) }
        val result = mutableListOf<DepartureOption>()
        for (stop in response.stops) {
            for (dep in stop.departures) {
                val route = dep.trip.route ?: continue
                val onestopId = route.onestopId ?: continue
                val tripIntId = dep.trip.id ?: continue
                val seq = dep.stopSequence ?: continue
                val iso = dep.departure?.estimatedUtc ?: dep.departure?.scheduledUtc ?: continue
                val depUtcMs = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: continue
                val transitType = gtfsRouteTypeToTransitType(route.routeType)
                result.add(
                    DepartureOption(
                        route = RouteMeta(
                            onestopId = onestopId,
                            shortName = route.routeShortName.orEmpty(),
                            longName = route.routeLongName.orEmpty(),
                            color = normalizeRouteColor(route.routeColor, transitType),
                            agency = route.agency?.agencyName.orEmpty(),
                            mode = transitType,
                        ),
                        headsign = dep.trip.tripHeadsign ?: dep.stopHeadsign.orEmpty(),
                        tripIntId = tripIntId,
                        boardStopSequence = seq,
                        depUtcMs = depUtcMs,
                    ),
                )
            }
        }
        depsCache.put(key, result)
        return result
    }

    override suspend fun tripDetails(routeOnestopId: String, tripIntId: Long): TripDetails? {
        val key = "$routeOnestopId/$tripIntId"
        tripCache.get(key)?.let { return it.value }

        val raw = mapRateLimit {
            runCatching { api.getTrip(routeOnestopId, tripIntId) }.getOrElse { e ->
                if (e is HttpException && (e.code() == 429 || e.code() == 403)) throw RateLimitedException()
                null
            }
        }
        val detail = raw?.trips?.firstOrNull()
        var result: TripDetails? = null
        if (detail?.stopTimes != null) {
            val stopTimes = detail.stopTimes.mapNotNull { st ->
                val arrivalSec = parseGtfsSeconds(st.arrivalTime) ?: parseGtfsSeconds(st.departureTime)
                val departureSec = parseGtfsSeconds(st.departureTime) ?: arrivalSec
                val stop = st.stop
                val coords = stop?.geometry?.coordinates
                if (arrivalSec == null || departureSec == null || st.stopSequence == null ||
                    coords == null || coords.size < 2 || stop.id == null
                ) {
                    return@mapNotNull null
                }
                TripStopTime(
                    seq = st.stopSequence,
                    gtfsStopId = stop.stopId.orEmpty(),
                    stopKey = stop.id.toString(),
                    name = stop.stopName.orEmpty(),
                    lat = coords[1],
                    lng = coords[0],
                    arrivalSec = arrivalSec,
                    departureSec = departureSec,
                )
            }.sortedBy { it.seq }
            if (stopTimes.size >= 2) {
                // Strip any 3rd (elevation) element from shape points.
                val shape = detail.shape?.geometry?.coordinates
                    ?.filter { it.size >= 2 }
                    ?.map { LngLat(it[0], it[1]) }
                    ?.takeIf { it.isNotEmpty() }
                result = TripDetails(
                    gtfsTripId = detail.tripId.orEmpty(),
                    headsign = detail.tripHeadsign.orEmpty(),
                    stopTimes = stopTimes,
                    shape = shape,
                )
            }
        }
        tripCache.put(key, result)
        return result
    }

    private fun parseGtfsSeconds(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(":")
        if (parts.size != 3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val s = parts[2].toIntOrNull() ?: return null
        return h * 3600 + m * 60 + s
    }

    private fun normalizeRouteColor(raw: String?, transitType: String): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isNotEmpty()) return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return when (transitType) {
            "bus" -> "#00A862"
            "train" -> "#2563EB"
            "subway" -> "#8B2FC9"
            "ferry" -> "#0891B2"
            "tram" -> "#EA7317"
            else -> "#00A862"
        }
    }
}
