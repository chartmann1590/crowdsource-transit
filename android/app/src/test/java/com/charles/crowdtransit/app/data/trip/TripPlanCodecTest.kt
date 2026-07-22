package com.charles.crowdtransit.app.data.trip

import com.charles.crowdtransit.model.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlanCodecTest {

    private val codec = TripPlanCodec()

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "missing test resource $name (docs/routing/fixtures should be on the test resources path)"
        }.bufferedReader().readText()

    private fun assertGoldenFixture(baseName: String) {
        val fromJson = codec.fromJson(resource("$baseName.json"))
        val fromBlob = codec.decode(resource("$baseName.blob.txt"))
        assertEquals("golden blob must decode to the fixture plan", fromJson, fromBlob)
        assertEquals("round-trip", fromJson, codec.decode(codec.encode(fromJson)))
    }

    @Test
    fun `golden fixture plan-direct decodes and round-trips`() = assertGoldenFixture("plan-direct")

    @Test
    fun `golden fixture plan-transfer decodes and round-trips`() = assertGoldenFixture("plan-transfer")

    @Test
    fun `transit legs keep full stop sequence`() {
        val plan = codec.fromJson(resource("plan-direct.json"))
        val transit = plan.legs.single { it.isTransit }
        val stops = requireNotNull(transit.stops)
        assertEquals(11, stops.size)
        assertEquals(transit.board?.stopId, stops.first().id)
        assertEquals(transit.alight?.stopId, stops.last().id)
        assertTrue(plan.legs.first().isWalk && plan.legs.last().isWalk)
        assertEquals(Leg.LEG_TRANSIT, transit.t)
    }

    @Test
    fun `unsupported version is rejected`() {
        val futureVersion = resource("plan-direct.json").replaceFirst("\"v\": 1", "\"v\": 99")
        assertThrows(UnsupportedTripPlanVersion::class.java) { codec.fromJson(futureVersion) }
    }
}
