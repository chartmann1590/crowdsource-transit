package com.charles.crowdtransit.app.domain.navigation

import com.charles.crowdtransit.app.domain.routing.TransitRouter
import com.charles.crowdtransit.app.util.PolylineCodec
import com.charles.crowdtransit.model.Leg
import com.charles.crowdtransit.model.TripPlan
import java.time.Instant

/**
 * Pure turn-by-turn state machine (docs/routing/router-spec.md companion). Feed it GPS
 * fixes via [onLocation]; it advances through the plan's legs and reports the current
 * instruction, alight alerts, and off-route detection. No Android dependencies —
 * unit-tested with synthetic traces in NavigationEngineTest.
 *
 * Phases: WalkingToStop → WaitingToBoard → Riding → (next leg …) → WalkingToDestination
 * → Arrived. Off-route: perpendicular distance to the active leg geometry above the
 * phase threshold for 3 consecutive fixes.
 */
class NavigationEngine(val plan: TripPlan) {

    companion object {
        const val WAYPOINT_REACHED_M = 30.0
        const val BOARD_DEPARTED_M = 70.0
        const val ALIGHT_ALERT_M = 400.0
        const val OFF_ROUTE_WALK_M = 75.0
        const val OFF_ROUTE_TRANSIT_M = 250.0
        const val OFF_ROUTE_CONSECUTIVE = 3
    }

    sealed class Phase {
        data class Walking(val legIndex: Int, val toDestination: Boolean) : Phase()
        data class WaitingToBoard(val legIndex: Int) : Phase()
        data class Riding(val legIndex: Int, val stopsRemaining: Int, val alightSoon: Boolean) : Phase()
        object Arrived : Phase()
    }

    data class NavState(
        val phase: Phase,
        val instruction: String,
        val nextInstruction: String?,
        val distanceToNextM: Int,
        val offRoute: Boolean,
        val arrived: Boolean,
    )

    private var legIndex = 0
    private var waiting = false
    private var offRouteStreak = 0
    private var lastState: NavState = initialState()

    val currentState: NavState get() = lastState

    private fun initialState(): NavState {
        val leg = plan.legs.firstOrNull() ?: return NavState(Phase.Arrived, "Arrived", null, 0, false, true)
        return NavState(
            phase = if (leg.isWalk) Phase.Walking(0, plan.legs.size == 1) else Phase.WaitingToBoard(0),
            instruction = legInstruction(0),
            nextInstruction = legInstructionOrNull(1),
            distanceToNextM = 0,
            offRoute = false,
            arrived = false,
        )
    }

    fun onLocation(lat: Double, lng: Double, nowMs: Long): NavState {
        while (legIndex < plan.legs.size) {
            val leg = plan.legs[legIndex]
            val advanced = if (leg.isWalk) walkStep(leg, lat, lng) else transitStep(leg, lat, lng, nowMs)
            if (!advanced) break
            legIndex++
            waiting = leg.isWalk && legIndex < plan.legs.size && plan.legs[legIndex].isTransit
            offRouteStreak = 0
        }

        if (legIndex >= plan.legs.size) {
            lastState = NavState(Phase.Arrived, "You've arrived at ${plan.to.name.ifBlank { "your destination" }}", null, 0, false, true)
            return lastState
        }

        val leg = plan.legs[legIndex]
        val offRoute = updateOffRoute(leg, lat, lng)
        lastState = buildState(leg, lat, lng, offRoute)
        return lastState
    }

    /** Reset off-route tracking after the user accepts a reroute into a new engine. */
    fun clearOffRoute() {
        offRouteStreak = 0
    }

    private fun walkStep(leg: Leg, lat: Double, lng: Double): Boolean {
        val to = leg.to ?: return true
        return TransitRouter.metersBetween(lat, lng, to.lat, to.lng) <= WAYPOINT_REACHED_M
    }

    /** True once the rider has alighted at (or passed) the leg's alight stop. */
    private fun transitStep(leg: Leg, lat: Double, lng: Double, nowMs: Long): Boolean {
        val stops = leg.stops.orEmpty()
        if (stops.size < 2) return true
        val alight = stops.last()
        if (TransitRouter.metersBetween(lat, lng, alight.lat, alight.lng) <= WAYPOINT_REACHED_M) return true

        if (waiting) {
            val board = stops.first()
            val depMs = leg.board?.depUtc?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            val awayFromBoard = TransitRouter.metersBetween(lat, lng, board.lat, board.lng) > BOARD_DEPARTED_M
            // Boarding inferred: we've moved away from the board stop around/after the
            // scheduled departure (waiting at the stop never triggers this).
            if (awayFromBoard && (depMs == null || nowMs >= depMs - 120_000)) {
                waiting = false
            }
        }
        return false
    }

    private fun nearestStopIndex(leg: Leg, lat: Double, lng: Double): Int {
        val stops = leg.stops.orEmpty()
        var best = 0
        var bestD = Double.MAX_VALUE
        stops.forEachIndexed { i, s ->
            val d = TransitRouter.metersBetween(lat, lng, s.lat, s.lng)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun activeGeometry(leg: Leg): List<Pair<Double, Double>> = if (leg.isTransit) {
        leg.shapePoly?.let { PolylineCodec.decode(it).map { p -> p.lat to p.lng } }
            ?: leg.stops.orEmpty().map { it.lat to it.lng }
    } else {
        leg.poly?.let { PolylineCodec.decode(it).map { p -> p.lat to p.lng } }
            ?: listOfNotNull(
                leg.from?.let { it.lat to it.lng },
                leg.to?.let { it.lat to it.lng },
            )
    }

    private fun distanceToGeometryM(points: List<Pair<Double, Double>>, lat: Double, lng: Double): Double {
        if (points.isEmpty()) return 0.0
        if (points.size == 1) return TransitRouter.metersBetween(lat, lng, points[0].first, points[0].second)
        var best = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            best = minOf(best, distanceToSegmentM(points[i], points[i + 1], lat, lng))
        }
        return best
    }

    private fun distanceToSegmentM(a: Pair<Double, Double>, b: Pair<Double, Double>, lat: Double, lng: Double): Double {
        // Planar approximation is fine at these scales.
        val ax = a.second
        val ay = a.first
        val bx = b.second
        val by = b.first
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0.0) 0.0 else (((lng - ax) * dx + (lat - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        return TransitRouter.metersBetween(lat, lng, ay + t * dy, ax + t * dx)
    }

    private fun updateOffRoute(leg: Leg, lat: Double, lng: Double): Boolean {
        // Waiting at the board stop is on-route by definition.
        if (waiting && leg.isTransit) {
            offRouteStreak = 0
            return false
        }
        val threshold = if (leg.isTransit) OFF_ROUTE_TRANSIT_M else OFF_ROUTE_WALK_M
        val distance = distanceToGeometryM(activeGeometry(leg), lat, lng)
        offRouteStreak = if (distance > threshold) offRouteStreak + 1 else 0
        return offRouteStreak >= OFF_ROUTE_CONSECUTIVE
    }

    private fun buildState(leg: Leg, lat: Double, lng: Double, offRoute: Boolean): NavState {
        return if (leg.isWalk) {
            val to = leg.to
            val distance = to?.let { TransitRouter.metersBetween(lat, lng, it.lat, it.lng) } ?: 0.0
            val toDestination = legIndex == plan.legs.size - 1
            NavState(
                phase = Phase.Walking(legIndex, toDestination),
                instruction = legInstruction(legIndex),
                nextInstruction = legInstructionOrNull(legIndex + 1),
                distanceToNextM = distance.toInt(),
                offRoute = offRoute,
                arrived = false,
            )
        } else {
            val stops = leg.stops.orEmpty()
            val alight = stops.last()
            val distanceToAlight = TransitRouter.metersBetween(lat, lng, alight.lat, alight.lng)
            if (waiting) {
                val board = stops.first()
                NavState(
                    phase = Phase.WaitingToBoard(legIndex),
                    instruction = legInstruction(legIndex),
                    nextInstruction = legInstructionOrNull(legIndex + 1),
                    distanceToNextM = TransitRouter.metersBetween(lat, lng, board.lat, board.lng).toInt(),
                    offRoute = offRoute,
                    arrived = false,
                )
            } else {
                val nearest = nearestStopIndex(leg, lat, lng)
                val stopsRemaining = (stops.size - 1 - nearest).coerceAtLeast(0)
                val alightSoon = stopsRemaining <= 1 || distanceToAlight <= ALIGHT_ALERT_M
                NavState(
                    phase = Phase.Riding(legIndex, stopsRemaining, alightSoon),
                    instruction = if (alightSoon) {
                        "Get off at ${alight.name}" + if (stopsRemaining == 1) " (next stop)" else ""
                    } else {
                        "Ride to ${alight.name} — $stopsRemaining stops to go"
                    },
                    nextInstruction = legInstructionOrNull(legIndex + 1),
                    distanceToNextM = distanceToAlight.toInt(),
                    offRoute = offRoute,
                    arrived = false,
                )
            }
        }
    }

    private fun legInstruction(index: Int): String {
        val leg = plan.legs.getOrNull(index) ?: return "Arrived"
        return if (leg.isWalk) {
            "Walk to ${leg.to?.name.orEmpty().ifBlank { "your destination" }}"
        } else {
            val label = leg.route?.short?.ifBlank { leg.route.long } ?: ""
            "Take ${leg.mode} $label toward ${leg.trip?.headsign} from ${leg.board?.name}"
        }
    }

    private fun legInstructionOrNull(index: Int): String? =
        if (index < plan.legs.size) legInstruction(index) else null
}
