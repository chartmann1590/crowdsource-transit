package com.charles.crowdtransit.app.domain.routing

import com.charles.crowdtransit.app.data.remote.OrsApi
import com.charles.crowdtransit.app.data.remote.OrsWalkRequest
import com.charles.crowdtransit.app.data.routing.RoomGtfsDataSource
import com.charles.crowdtransit.app.data.routing.TransitlandDataSource
import com.charles.crowdtransit.app.util.PolylineCodec
import com.charles.crowdtransit.model.Leg
import com.charles.crowdtransit.model.TripPlan
import com.charles.crowdtransit.model.WalkStep
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Orchestrator: run the pure [TransitRouter], then refine the winning itineraries'
 * walking legs through the ORS proxy (street-following polylines + step instructions).
 * ORS failures leave the straight-line estimates in place — the plan always succeeds
 * if the router did. Mirrors web/src/routing/planTrip.ts.
 */
@Singleton
class PlanTripUseCase @Inject constructor(
    private val router: TransitRouter,
    private val transitlandSource: TransitlandDataSource,
    private val roomSource: RoomGtfsDataSource,
    private val orsApi: OrsApi,
) {

    suspend operator fun invoke(req: PlanRequest): List<TripPlan> {
        // Offline-first: when both trip ends have downloaded GTFS stops nearby, route
        // entirely on-device (zero Transitland calls). Sources are never mixed within
        // one search.
        val source = if (offlineCovers(req)) roomSource else transitlandSource
        var plans = router.planTrips(source, req)
        if (plans.isEmpty() && source === roomSource) {
            // Downloaded data may be stale/sparse — fall back to live.
            plans = router.planTrips(transitlandSource, req)
        }
        return plans.map { refineWalkLegs(it) }
    }

    private suspend fun offlineCovers(req: PlanRequest): Boolean = try {
        val radiusM = req.maxWalkToStopM ?: TransitRouter.DEFAULT_CANDIDATE_RADIUS_M
        roomSource.stopsNear(req.fromLat, req.fromLng, radiusM, 5).isNotEmpty() &&
            roomSource.stopsNear(req.toLat, req.toLng, radiusM, 5).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private suspend fun refineWalkLegs(plan: TripPlan): TripPlan {
        val legs = plan.legs.toMutableList()
        for (i in legs.indices) {
            val leg = legs[i]
            if (!leg.isWalk) continue
            val from = leg.from ?: continue
            val to = leg.to ?: continue
            val walk = fetchWalk(from.lat, from.lng, to.lat, to.lng) ?: continue

            var dep = leg.dep
            var arr = leg.arr
            val next = legs.getOrNull(i + 1)
            val prev = legs.getOrNull(i - 1)
            if (next != null && next.isTransit) {
                // Walk that ends at a boarding: arrive when the vehicle departs, leave just in time.
                val boardMs = Instant.parse(next.board!!.depUtc).toEpochMilli()
                arr = TransitRouter.toIso(boardMs)
                dep = TransitRouter.toIso(boardMs - walk.durationSec * 1000L)
            } else if (prev != null && prev.isTransit) {
                // Final walk: start when the vehicle arrives.
                val alightMs = Instant.parse(prev.alight!!.arrUtc).toEpochMilli()
                dep = TransitRouter.toIso(alightMs)
                arr = TransitRouter.toIso(alightMs + walk.durationSec * 1000L)
            }
            legs[i] = leg.copy(
                dep = dep,
                arr = arr,
                distM = walk.distanceM,
                poly = walk.poly,
                steps = walk.steps.ifEmpty { leg.steps },
            )
        }
        return plan.copy(legs = legs)
    }

    private data class WalkResult(
        val distanceM: Int,
        val durationSec: Int,
        val poly: String,
        val steps: List<WalkStep>,
    )

    /** null on any failure — callers keep their straight-line estimate. */
    private suspend fun fetchWalk(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): WalkResult? {
        return try {
            val response = orsApi.walk(
                OrsWalkRequest(coordinates = listOf(listOf(fromLng, fromLat), listOf(toLng, toLat))),
            )
            val feature = response.features.firstOrNull() ?: return null
            val coords = feature.geometry?.coordinates?.takeIf { it.isNotEmpty() } ?: return null
            val summary = feature.properties?.summary ?: return null
            val distance = summary.distance ?: return null
            val duration = summary.duration ?: return null
            WalkResult(
                distanceM = distance.roundToInt(),
                durationSec = duration.roundToInt(),
                poly = PolylineCodec.encode(
                    coords.filter { it.size >= 2 }.map { LngLat(it[0], it[1]) },
                ),
                steps = feature.properties.segments.flatMap { seg ->
                    seg.steps.mapNotNull { s ->
                        s.instruction?.takeIf { it.isNotBlank() }?.let {
                            // way_points[0] is the maneuver point's index into the route geometry.
                            val pointIdx = (s.wayPoints?.firstOrNull() ?: 0).coerceIn(0, coords.size - 1)
                            val point = coords[pointIdx]
                            WalkStep(
                                text = it,
                                distM = (s.distance ?: 0.0).roundToInt(),
                                lat = point.getOrElse(1) { 0.0 },
                                lng = point.getOrElse(0) { 0.0 },
                            )
                        }
                    }
                },
            )
        } catch (_: Exception) {
            null
        }
    }
}
