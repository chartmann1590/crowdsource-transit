package com.charles.crowdtransit.app.domain.routing

import com.charles.crowdtransit.model.Leg
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Kotlin twin of web/src/routing/router.test.ts — same fixture network
 * (docs/routing/fixtures/network.json, on the test resources path), same assertions.
 */
class TransitRouterTest {

    @JsonClass(generateAdapter = false)
    data class NetworkStop(val key: String, val name: String, val lat: Double, val lng: Double)

    @JsonClass(generateAdapter = false)
    data class NetworkRoute(
        val onestopId: String,
        val shortName: String,
        val longName: String,
        val color: String,
        val agency: String,
        val mode: String,
    )

    @JsonClass(generateAdapter = false)
    data class NetworkTripStop(val key: String, val min: Int)

    @JsonClass(generateAdapter = false)
    data class NetworkTrip(
        val tripIntId: Long,
        val route: String,
        val gtfsTripId: String,
        val headsign: String,
        val startOffsetMin: Int,
        val stops: List<NetworkTripStop>,
    )

    @JsonClass(generateAdapter = false)
    data class NetworkStale(val stopKey: String, val route: String, val tripIntId: Long, val depUtc: String)

    @JsonClass(generateAdapter = false)
    data class Network(
        val stops: List<NetworkStop>,
        val routes: List<NetworkRoute>,
        val trips: List<NetworkTrip>,
        val staleDepartures: List<NetworkStale>,
    )

    private val network: Network = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(Network::class.java)
        .fromJson(
            checkNotNull(javaClass.classLoader?.getResourceAsStream("network.json")) {
                "network.json missing from test resources"
            }.bufferedReader().readText(),
        )!!

    /** In-memory GtfsDataSource over network.json; trip times anchored to [baseMs]. */
    private inner class FixtureDataSource(private val baseMs: Long) : GtfsDataSource {

        private fun route(id: String): RouteMeta {
            val r = network.routes.first { it.onestopId == id }
            return RouteMeta(r.onestopId, r.shortName, r.longName, r.color, r.agency, r.mode)
        }

        private fun stop(key: String): NetworkStop = network.stops.first { it.key == key }

        override suspend fun stopsNear(lat: Double, lng: Double, radiusM: Int, limit: Int): List<StopCandidate> =
            network.stops
                .filter { TransitRouter.metersBetween(lat, lng, it.lat, it.lng) <= radiusM }
                .take(limit)
                .map { StopCandidate(it.key, it.name, it.lat, it.lng) }

        override suspend fun departures(stopKey: String, notBeforeUtcMs: Long, windowSec: Int): List<DepartureOption> {
            val out = mutableListOf<DepartureOption>()
            for (trip in network.trips) {
                val idx = trip.stops.indexOfFirst { it.key == stopKey }
                if (idx < 0 || idx == trip.stops.size - 1) continue
                out.add(
                    DepartureOption(
                        route = route(trip.route),
                        headsign = trip.headsign,
                        tripIntId = trip.tripIntId,
                        boardStopSequence = idx,
                        depUtcMs = baseMs + (trip.startOffsetMin + trip.stops[idx].min) * 60_000L,
                    ),
                )
            }
            for (stale in network.staleDepartures) {
                if (stale.stopKey != stopKey) continue
                out.add(
                    DepartureOption(
                        route = route(stale.route),
                        headsign = "Ghost",
                        tripIntId = stale.tripIntId,
                        boardStopSequence = 0,
                        depUtcMs = Instant.parse(stale.depUtc).toEpochMilli(),
                    ),
                )
            }
            return out
        }

        override suspend fun tripDetails(routeOnestopId: String, tripIntId: Long): TripDetails? {
            val trip = network.trips.firstOrNull { it.tripIntId == tripIntId } ?: return null
            val daySec = 8 * 3600
            return TripDetails(
                gtfsTripId = trip.gtfsTripId,
                headsign = trip.headsign,
                stopTimes = trip.stops.mapIndexed { i, s ->
                    val st = stop(s.key)
                    val sec = daySec + (trip.startOffsetMin + s.min) * 60
                    TripStopTime(
                        seq = i,
                        gtfsStopId = s.key,
                        stopKey = s.key,
                        name = st.name,
                        lat = st.lat,
                        lng = st.lng,
                        arrivalSec = sec,
                        departureSec = sec,
                    )
                },
            )
        }
    }

    private val router = TransitRouter()

    private fun base(): Long = System.currentTimeMillis() / 1000 * 1000

    private val origin1 = Triple("Origin", 40.6996, -74.0)
    private val dest1 = Triple("Destination", 40.7154, -74.0)

    private fun request(
        from: Triple<String, Double, Double>,
        to: Triple<String, Double, Double>,
        departAtMs: Long,
    ) = PlanRequest(
        fromName = from.first, fromLat = from.second, fromLng = from.third,
        toName = to.first, toLat = to.second, toLng = to.third,
        departAtMs = departAtMs,
    )

    @Test
    fun `finds the direct ride with the full stop sequence`() = runBlocking {
        val base = base()
        val plans = router.planTrips(FixtureDataSource(base), request(origin1, dest1, base))

        assertTrue(plans.isNotEmpty())
        val plan = plans.first()
        val transit = plan.legs.filter { it.isTransit }
        assertEquals(1, transit.size)
        val leg = transit.first()
        assertEquals("r-test-red", leg.route?.onestopId)
        assertEquals(listOf("A", "B", "C", "D"), leg.stops?.map { it.id })
        assertEquals("A", leg.board?.stopId)
        assertEquals("D", leg.alight?.stopId)
        assertEquals(3, plan.legs.size)
        assertTrue(plan.legs.first().isWalk && plan.legs.last().isWalk)
        // ride timing: board at +12 min, alight at +27 min
        assertEquals(base + 12 * 60_000, Instant.parse(leg.board!!.depUtc).toEpochMilli())
        assertEquals(base + 27 * 60_000, Instant.parse(leg.alight!!.arrUtc).toEpochMilli())
    }

    @Test
    fun `rejects the wrong-direction trip and the stale departure`() = runBlocking {
        val base = base()
        val plans = router.planTrips(FixtureDataSource(base), request(origin1, dest1, base))

        for (plan in plans) {
            for (leg in plan.legs.filter { it.isTransit }) {
                assertTrue(leg.route?.onestopId != "r-test-wrong")
                assertTrue(leg.route?.onestopId != "r-test-stale")
            }
        }
    }

    @Test
    fun `finds the one-transfer itinerary via the shared stop`() = runBlocking {
        val base = base()
        val origin2 = Triple("Origin2", 40.7996, -74.1)
        val dest2 = Triple("Destination2", 40.8204, -74.1)
        val plans = router.planTrips(FixtureDataSource(base), request(origin2, dest2, base))

        assertTrue(plans.isNotEmpty())
        val transit = plans.first().legs.filter { it.isTransit }
        assertEquals(listOf("r-test-green", "r-test-blue"), transit.map { it.route?.onestopId })
        // Transfer respects the 120 s minimum: green arrives X at +15, blue departs X at +20.
        val gap = Instant.parse(transit[1].board!!.depUtc).toEpochMilli() -
            Instant.parse(transit[0].alight!!.arrUtc).toEpochMilli()
        assertTrue(gap >= 120_000)
        // legs: walk, ride, walk(transfer, zero-distance same stop), ride, walk
        assertEquals(5, plans.first().legs.size)
    }

    @Test
    fun `arrive-by search finds an itinerary landing at or before the target`() = runBlocking {
        val base = base()
        // Direct ride boards at +12 min, alights at +27 min (see the direct-ride test above).
        val arriveByMs = base + 30 * 60_000
        val plans = router.planTrips(
            FixtureDataSource(base),
            PlanRequest(
                fromName = origin1.first, fromLat = origin1.second, fromLng = origin1.third,
                toName = dest1.first, toLat = dest1.second, toLng = dest1.third,
                arriveByMs = arriveByMs,
            ),
        )

        assertTrue(plans.isNotEmpty())
        for (plan in plans) {
            assertTrue(Instant.parse(plan.legs.last().arr).toEpochMilli() <= arriveByMs)
        }
    }

    @Test
    fun `arrive-by search finds nothing when even the earliest ride misses the target`() = runBlocking {
        val base = base()
        // The direct ride can't alight before +27 min, so a target before boarding is
        // unreachable within the bounded lookback attempts.
        val plans = router.planTrips(
            FixtureDataSource(base),
            PlanRequest(
                fromName = origin1.first, fromLat = origin1.second, fromLng = origin1.third,
                toName = dest1.first, toLat = dest1.second, toLng = dest1.third,
                arriveByMs = base + 5 * 60_000,
            ),
        )
        assertEquals(emptyList<Leg>(), plans)
    }

    @Test
    fun `returns no plans when no stops are near the origin`() = runBlocking {
        val base = base()
        val plans = router.planTrips(
            FixtureDataSource(base),
            request(Triple("Nowhere", 10.0, 10.0), dest1, base),
        )
        assertEquals(emptyList<Leg>(), plans)
    }
}
