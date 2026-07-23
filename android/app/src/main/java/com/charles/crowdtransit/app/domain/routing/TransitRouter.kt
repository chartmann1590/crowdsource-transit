package com.charles.crowdtransit.app.domain.routing

import com.charles.crowdtransit.app.util.PolylineCodec
import com.charles.crowdtransit.model.AlightPoint
import com.charles.crowdtransit.model.BoardPoint
import com.charles.crowdtransit.model.Leg
import com.charles.crowdtransit.model.Place
import com.charles.crowdtransit.model.RouteRef
import com.charles.crowdtransit.model.TRIP_PLAN_VERSION
import com.charles.crowdtransit.model.TripPlan
import com.charles.crowdtransit.model.TripRef
import com.charles.crowdtransit.model.TripStop
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bounded RAPTOR-style transit search per docs/routing/router-spec.md — the Kotlin twin
 * of web/src/routing/router.ts, verified against the same docs/routing/fixtures/network.json.
 * Pure: all I/O via [GtfsDataSource]. Transit legs are contiguous slices of real trips
 * (the correctness guarantee). Walking legs are straight-line estimates here;
 * PlanTripUseCase refines the winners through the ORS proxy.
 */
@Singleton
class TransitRouter @Inject constructor() {

    companion object {
        const val CANDIDATE_RADIUS_M = 800
        const val MAX_CANDIDATES = 5
        private const val DEDUPE_M = 25.0
        private const val WINDOW_SEC = 7200
        private const val DEPS_PER_ROUND = 10
        private const val DEST_WALK_M = 600.0
        private const val TRANSFER_MIN_SEC = 120
        private const val TRANSFER_WALK_M = 300.0
        private const val FRONTIER = 6
        private const val FRONTIER_DEDUPE_M = 150.0
        // Every frontier stop gets the nearby-stop walk-transfer check, not just a subset —
        // a bus's outbound and inbound directions are frequently different physical stop
        // objects a few meters apart, so skipping this for lower-ranked frontier stops
        // silently misses the correct-direction boarding right next to a stop we reached.
        private const val NEARBY_TRANSFER_FRONTIER = FRONTIER
        private const val MAX_TRANSFERS = 2
        const val WALK_SPEED_MPS = 1.33
        const val WALK_DETOUR = 1.3
        private const val MAX_RESULTS = 3
        private const val BOARD_MATCH_M = 100.0
        private const val STALE_DEPARTURE_MS = 36L * 3600 * 1000
        private const val ARRIVE_BY_WINDOW_MS = WINDOW_SEC * 1000L
        private const val ARRIVE_BY_MAX_ATTEMPTS = 3

        fun walkSeconds(meters: Double): Int = ((meters * WALK_DETOUR) / WALK_SPEED_MPS).roundToInt()

        fun metersBetween(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(bLat - aLat)
            val dLng = Math.toRadians(bLng - aLng)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        fun toIso(ms: Long): String = Instant.ofEpochSecond((ms / 1000.0).roundToLong()).toString()
    }

    private data class RideSegment(
        val dep: DepartureOption,
        val trip: TripDetails,
        val boardIndex: Int,
        val alightIndex: Int,
        val boardDepUtcMs: Long,
        /** Straight-line walk that precedes this ride (from previous alight stop or the origin). */
        val walkFromName: String,
        val walkFromLat: Double,
        val walkFromLng: Double,
        val walkMeters: Double,
    )

    private data class Reached(
        val stop: TripStopTime,
        val arrUtcMs: Long,
        val path: List<RideSegment>,
    )

    private data class Emitted(
        val plan: TripPlan,
        val arrMs: Long,
        val transfers: Int,
        val routeKey: String,
    )

    private fun stopTimeUtcMs(seg: RideSegment, index: Int): Long {
        val board = seg.trip.stopTimes[seg.boardIndex]
        val st = seg.trip.stopTimes[index]
        return seg.boardDepUtcMs + (st.arrivalSec - board.departureSec) * 1000L
    }

    private suspend fun candidateStops(source: GtfsDataSource, lat: Double, lng: Double): List<StopCandidate> {
        val stops = source.stopsNear(lat, lng, CANDIDATE_RADIUS_M, 20)
        val sorted = stops
            .map { it to metersBetween(lat, lng, it.lat, it.lng) }
            .filter { it.second <= CANDIDATE_RADIUS_M }
            .sortedBy { it.second }
        val kept = mutableListOf<StopCandidate>()
        for ((s, _) in sorted) {
            if (kept.size >= MAX_CANDIDATES) break
            if (kept.any { metersBetween(it.lat, it.lng, s.lat, s.lng) < DEDUPE_M }) continue
            kept.add(s)
        }
        return kept
    }

    /** Board index = stop_sequence match, else nearest stop within BOARD_MATCH_M; -1 = unusable. */
    private fun findBoardIndex(trip: TripDetails, dep: DepartureOption, atLat: Double, atLng: Double): Int {
        val bySeq = trip.stopTimes.indexOfFirst { it.seq == dep.boardStopSequence }
        if (bySeq >= 0 &&
            metersBetween(trip.stopTimes[bySeq].lat, trip.stopTimes[bySeq].lng, atLat, atLng) <= BOARD_MATCH_M
        ) {
            return bySeq
        }
        var best = -1
        var bestD = BOARD_MATCH_M
        trip.stopTimes.forEachIndexed { i, st ->
            val d = metersBetween(st.lat, st.lng, atLat, atLng)
            if (d <= bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun sliceShape(trip: TripDetails, boardIndex: Int, alightIndex: Int): String? {
        val shape = trip.shape ?: return null
        if (shape.size < 2) return null

        fun nearestIndex(lat: Double, lng: Double): Int {
            var best = 0
            var bestD = Double.MAX_VALUE
            shape.forEachIndexed { i, p ->
                val d = metersBetween(lat, lng, p.lat, p.lng)
                if (d < bestD) {
                    bestD = d
                    best = i
                }
            }
            return best
        }

        val b = trip.stopTimes[boardIndex]
        val a = trip.stopTimes[alightIndex]
        val from = nearestIndex(b.lat, b.lng)
        val to = nearestIndex(a.lat, a.lng)
        if (to <= from) return null
        return PolylineCodec.encode(shape.subList(from, to + 1))
    }

    private fun buildWalkLeg(
        fromName: String,
        fromLat: Double,
        fromLng: Double,
        toName: String,
        toLat: Double,
        toLng: Double,
        depMs: Long,
        meters: Double,
    ): Leg = Leg(
        t = Leg.LEG_WALK,
        from = Place(fromName, fromLat, fromLng),
        to = Place(toName, toLat, toLng),
        dep = toIso(depMs),
        arr = toIso(depMs + walkSeconds(meters) * 1000L),
        distM = meters.roundToInt(),
    )

    private fun buildTransitLeg(seg: RideSegment): Leg {
        val board = seg.trip.stopTimes[seg.boardIndex]
        val alight = seg.trip.stopTimes[seg.alightIndex]
        val stops = (seg.boardIndex..seg.alightIndex).map { i ->
            val st = seg.trip.stopTimes[i]
            TripStop(
                id = st.stopKey,
                name = st.name,
                lat = st.lat,
                lng = st.lng,
                arrUtc = toIso(stopTimeUtcMs(seg, i)),
            )
        }
        return Leg(
            t = Leg.LEG_TRANSIT,
            mode = seg.dep.route.mode,
            route = RouteRef(
                onestopId = seg.dep.route.onestopId,
                short = seg.dep.route.shortName,
                long = seg.dep.route.longName,
                color = seg.dep.route.color,
                agency = seg.dep.route.agency,
            ),
            trip = TripRef(
                tripId = seg.trip.gtfsTripId,
                headsign = seg.dep.headsign.ifEmpty { seg.trip.headsign },
            ),
            board = BoardPoint(
                stopId = board.stopKey,
                name = board.name,
                lat = board.lat,
                lng = board.lng,
                depUtc = toIso(seg.boardDepUtcMs),
            ),
            alight = AlightPoint(
                stopId = alight.stopKey,
                name = alight.name,
                lat = alight.lat,
                lng = alight.lng,
                arrUtc = toIso(stopTimeUtcMs(seg, seg.alightIndex)),
            ),
            stops = stops,
            shapePoly = sliceShape(seg.trip, seg.boardIndex, seg.alightIndex),
        )
    }

    private fun assemblePlan(
        req: PlanRequest,
        path: List<RideSegment>,
        finalWalkMeters: Double,
        plannedAtMs: Long,
    ): TripPlan {
        val legs = mutableListOf<Leg>()
        for (seg in path) {
            val board = seg.trip.stopTimes[seg.boardIndex]
            val walkDepMs = seg.boardDepUtcMs - walkSeconds(seg.walkMeters) * 1000L
            legs.add(
                buildWalkLeg(
                    seg.walkFromName, seg.walkFromLat, seg.walkFromLng,
                    board.name, board.lat, board.lng,
                    walkDepMs, seg.walkMeters,
                ),
            )
            legs.add(buildTransitLeg(seg))
        }
        val last = path.last()
        val alight = last.trip.stopTimes[last.alightIndex]
        val alightMs = stopTimeUtcMs(last, last.alightIndex)
        legs.add(
            buildWalkLeg(
                alight.name, alight.lat, alight.lng,
                req.toName, req.toLat, req.toLng,
                alightMs, finalWalkMeters,
            ),
        )
        return TripPlan(
            v = TRIP_PLAN_VERSION,
            plannedAt = toIso(plannedAtMs),
            from = Place(req.fromName, req.fromLat, req.fromLng),
            to = Place(req.toName, req.toLat, req.toLng),
            legs = legs,
        )
    }

    private suspend fun departuresFor(
        source: GtfsDataSource,
        stop: StopCandidate,
        notBeforeUtcMs: Long,
        nowMs: Long,
    ): List<DepartureOption> {
        val deps = source.departures(stop.key, notBeforeUtcMs, WINDOW_SEC)
        val filtered = deps.filter {
            it.depUtcMs >= notBeforeUtcMs &&
                it.depUtcMs <= notBeforeUtcMs + WINDOW_SEC * 1000L &&
                kotlin.math.abs(it.depUtcMs - nowMs) <= STALE_DEPARTURE_MS
        }
        // Earliest departure per (route, headsign) bounds the trip-detail fetches.
        val earliest = LinkedHashMap<String, DepartureOption>()
        for (d in filtered) {
            val key = "${d.route.onestopId}|${d.headsign}"
            val cur = earliest[key]
            if (cur == null || d.depUtcMs < cur.depUtcMs) earliest[key] = d
        }
        return earliest.values.toList()
    }

    /** Dispatches to a depart-at search, or an arrive-by search that walks the depart
     * time backward from the target until it finds itineraries landing at or before it
     * (bounded to ARRIVE_BY_MAX_ATTEMPTS windows to cap API usage). */
    suspend fun planTrips(source: GtfsDataSource, req: PlanRequest): List<TripPlan> {
        val arriveByMs = req.arriveByMs
        if (arriveByMs != null) {
            for (attempt in 0 until ARRIVE_BY_MAX_ATTEMPTS) {
                val departAtMs = arriveByMs - ARRIVE_BY_WINDOW_MS * (attempt + 1)
                val plans = planTripsDepartAt(source, req.copy(departAtMs = departAtMs))
                val onTime = plans.filter { Instant.parse(it.legs.last().arr).toEpochMilli() <= arriveByMs }
                if (onTime.isNotEmpty()) {
                    return onTime
                        .sortedByDescending { Instant.parse(it.legs.last().arr).toEpochMilli() }
                        .take(MAX_RESULTS)
                }
            }
            return emptyList()
        }
        return planTripsDepartAt(source, req)
    }

    private suspend fun planTripsDepartAt(source: GtfsDataSource, req: PlanRequest): List<TripPlan> {
        val nowMs = System.currentTimeMillis()
        val departAtMs = req.departAtMs ?: nowMs

        val originCands = candidateStops(source, req.fromLat, req.fromLng)
        val destCands = candidateStops(source, req.toLat, req.toLng)
        if (originCands.isEmpty() || destCands.isEmpty()) return emptyList()

        val results = mutableListOf<Emitted>()
        val bestArrival = HashMap<String, Long>()

        fun tryEmit(path: List<RideSegment>) {
            val last = path.last()
            val alight = last.trip.stopTimes[last.alightIndex]
            val dDest = metersBetween(alight.lat, alight.lng, req.toLat, req.toLng)
            if (dDest > DEST_WALK_M) return
            val plan = assemblePlan(req, path, dDest, nowMs)
            val arrMs = Instant.parse(plan.legs.last().arr).toEpochMilli()
            results.add(
                Emitted(
                    plan = plan,
                    arrMs = arrMs,
                    transfers = path.size - 1,
                    routeKey = path.joinToString(">") { it.dep.route.onestopId },
                ),
            )
        }

        suspend fun expand(
            dep: DepartureOption,
            boardAtLat: Double,
            boardAtLng: Double,
            pathSoFar: List<RideSegment>,
            walkFromName: String,
            walkFromLat: Double,
            walkFromLng: Double,
            walkMeters: Double,
            reached: MutableList<Reached>,
        ) {
            if (pathSoFar.any { it.dep.route.onestopId == dep.route.onestopId && it.dep.headsign == dep.headsign }) {
                return
            }
            val trip = source.tripDetails(dep.route.onestopId, dep.tripIntId) ?: return
            val boardIndex = findBoardIndex(trip, dep, boardAtLat, boardAtLng)
            if (boardIndex < 0 || boardIndex >= trip.stopTimes.size - 1) return
            for (i in boardIndex + 1 until trip.stopTimes.size) {
                val seg = RideSegment(
                    dep = dep,
                    trip = trip,
                    boardIndex = boardIndex,
                    alightIndex = i,
                    boardDepUtcMs = dep.depUtcMs,
                    walkFromName = walkFromName,
                    walkFromLat = walkFromLat,
                    walkFromLng = walkFromLng,
                    walkMeters = walkMeters,
                )
                val st = trip.stopTimes[i]
                val arrMs = stopTimeUtcMs(seg, i)
                tryEmit(pathSoFar + seg)
                val prev = bestArrival[st.stopKey]
                if (prev == null || arrMs < prev) {
                    bestArrival[st.stopKey] = arrMs
                    reached.add(Reached(st, arrMs, pathSoFar + seg))
                }
            }
        }

        // Round 0: board near the origin.
        var reached = mutableListOf<Reached>()
        data class Round0(val dep: DepartureOption, val cand: StopCandidate, val walkMeters: Double)
        val round0 = mutableListOf<Round0>()
        for (cand in originCands) {
            val walkMeters = metersBetween(req.fromLat, req.fromLng, cand.lat, cand.lng)
            val deps = departuresFor(source, cand, departAtMs + walkSeconds(walkMeters) * 1000L, nowMs)
            for (dep in deps) round0.add(Round0(dep, cand, walkMeters))
        }
        round0.sortBy { it.dep.depUtcMs }
        for ((dep, cand, walkMeters) in round0.take(DEPS_PER_ROUND)) {
            expand(
                dep, cand.lat, cand.lng, emptyList(),
                req.fromName, req.fromLat, req.fromLng, walkMeters, reached,
            )
        }

        // Transfer rounds.
        for (round in 0 until MAX_TRANSFERS) {
            val ranked = reached.sortedBy {
                it.arrUtcMs +
                    walkSeconds(metersBetween(it.stop.lat, it.stop.lng, req.toLat, req.toLng)) * 1000L
            }
            // A single boarding corridor reaches many stops a block apart, which used to
            // fill the whole frontier with near-duplicate locations and crowd out the one
            // stop that actually connects to a different route. Keep only the best-ranked
            // reach within each FRONTIER_DEDUPE_M cluster before taking the top FRONTIER.
            val frontier = mutableListOf<Reached>()
            for (r in ranked) {
                if (frontier.size >= FRONTIER) break
                if (frontier.any { metersBetween(it.stop.lat, it.stop.lng, r.stop.lat, r.stop.lng) < FRONTIER_DEDUPE_M }) continue
                frontier.add(r)
            }
            reached = mutableListOf()
            for ((fi, r) in frontier.withIndex()) {
                data class BoardStop(val stop: StopCandidate, val walkMeters: Double)
                val boardStops = mutableListOf(
                    BoardStop(StopCandidate(r.stop.stopKey, r.stop.name, r.stop.lat, r.stop.lng), 0.0),
                )
                if (fi < NEARBY_TRANSFER_FRONTIER) {
                    val nearby = source.stopsNear(r.stop.lat, r.stop.lng, TRANSFER_WALK_M.toInt(), 10)
                    for (n in nearby) {
                        if (n.key == r.stop.stopKey) continue
                        val d = metersBetween(r.stop.lat, r.stop.lng, n.lat, n.lng)
                        if (d <= TRANSFER_WALK_M) boardStops.add(BoardStop(n, d))
                    }
                }
                data class Option(val dep: DepartureOption, val stop: StopCandidate, val walkMeters: Double)
                val options = mutableListOf<Option>()
                for ((stop, walkMeters) in boardStops) {
                    val notBefore = r.arrUtcMs + TRANSFER_MIN_SEC * 1000L + walkSeconds(walkMeters) * 1000L
                    val deps = departuresFor(source, stop, notBefore, nowMs)
                    for (dep in deps) options.add(Option(dep, stop, walkMeters))
                }
                options.sortBy { it.dep.depUtcMs }
                for ((dep, stop, walkMeters) in options.take(DEPS_PER_ROUND)) {
                    expand(
                        dep, stop.lat, stop.lng, r.path,
                        r.stop.name, r.stop.lat, r.stop.lng, walkMeters, reached,
                    )
                }
            }
            if (reached.isEmpty()) break
        }

        results.sortWith(compareBy({ it.arrMs }, { it.transfers }))
        val seen = mutableSetOf<String>()
        val out = mutableListOf<TripPlan>()
        for (r in results) {
            if (!seen.add(r.routeKey)) continue
            out.add(r.plan)
            if (out.size >= MAX_RESULTS) break
        }
        return out
    }
}
