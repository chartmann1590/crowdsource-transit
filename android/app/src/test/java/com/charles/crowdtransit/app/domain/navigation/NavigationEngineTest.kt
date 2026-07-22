package com.charles.crowdtransit.app.domain.navigation

import com.charles.crowdtransit.app.data.trip.TripPlanCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Drives the engine with synthetic GPS traces over the plan-direct golden fixture. */
class NavigationEngineTest {

    private val plan = TripPlanCodec().fromJson(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("plan-direct.json")) {
            "plan-direct.json missing from test resources"
        }.bufferedReader().readText(),
    )

    // Fixture timeline: walk dep 15:02, board 15:10, alight (Whitehall) 15:28, arrive 15:35.
    private fun t(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `full on-route trip advances through every phase to arrival`() {
        val engine = NavigationEngine(plan)
        val walk = plan.legs[0]
        val ride = plan.legs[1]
        val stops = ride.stops!!

        // Walking to the first stop.
        var state = engine.onLocation(walk.from!!.lat, walk.from!!.lng, t("2026-07-22T15:02:00Z"))
        assertTrue(state.phase is NavigationEngine.Phase.Walking)

        // Reached the board stop -> waiting to board.
        state = engine.onLocation(stops.first().lat, stops.first().lng, t("2026-07-22T15:06:00Z"))
        assertTrue(state.phase is NavigationEngine.Phase.WaitingToBoard)

        // Still waiting at departure time — waiting is not "boarded".
        state = engine.onLocation(stops.first().lat, stops.first().lng, t("2026-07-22T15:10:00Z"))
        assertTrue(state.phase is NavigationEngine.Phase.WaitingToBoard)
        assertFalse(state.offRoute)

        // Moving along the stop sequence after departure -> riding.
        state = engine.onLocation(stops[2].lat, stops[2].lng, t("2026-07-22T15:14:00Z"))
        val riding = state.phase as NavigationEngine.Phase.Riding
        assertEquals(stops.size - 1 - 2, riding.stopsRemaining)
        assertFalse(riding.alightSoon)

        // One stop before the alight stop -> alight alert.
        state = engine.onLocation(stops[stops.size - 2].lat, stops[stops.size - 2].lng, t("2026-07-22T15:27:00Z"))
        val alerting = state.phase as NavigationEngine.Phase.Riding
        assertTrue(alerting.alightSoon)
        assertTrue(state.instruction.startsWith("Get off at"))

        // At the alight stop -> final walk.
        state = engine.onLocation(stops.last().lat, stops.last().lng, t("2026-07-22T15:28:00Z"))
        assertTrue(state.phase is NavigationEngine.Phase.Walking)
        assertTrue((state.phase as NavigationEngine.Phase.Walking).toDestination)

        // At the destination -> arrived.
        state = engine.onLocation(plan.to.lat, plan.to.lng, t("2026-07-22T15:35:00Z"))
        assertTrue(state.arrived)
        assertEquals(NavigationEngine.Phase.Arrived, state.phase)
    }

    @Test
    fun `off-route walking triggers after three consecutive far fixes`() {
        val engine = NavigationEngine(plan)
        // ~1 km east of the walking leg.
        val farLng = plan.legs[0].from!!.lng + 0.012
        val lat = plan.legs[0].from!!.lat

        var state = engine.onLocation(lat, farLng, t("2026-07-22T15:02:00Z"))
        assertFalse(state.offRoute)
        state = engine.onLocation(lat, farLng, t("2026-07-22T15:02:05Z"))
        assertFalse(state.offRoute)
        state = engine.onLocation(lat, farLng, t("2026-07-22T15:02:10Z"))
        assertTrue(state.offRoute)

        // Coming back on-route clears the streak.
        state = engine.onLocation(plan.legs[0].from!!.lat, plan.legs[0].from!!.lng, t("2026-07-22T15:03:00Z"))
        assertFalse(state.offRoute)
    }

    @Test
    fun `single far fix does not flag off-route`() {
        val engine = NavigationEngine(plan)
        val state = engine.onLocation(plan.legs[0].from!!.lat, plan.legs[0].from!!.lng + 0.012, t("2026-07-22T15:02:00Z"))
        assertFalse(state.offRoute)
        val next = engine.onLocation(plan.legs[0].from!!.lat, plan.legs[0].from!!.lng, t("2026-07-22T15:02:05Z"))
        assertFalse(next.offRoute)
    }
}
