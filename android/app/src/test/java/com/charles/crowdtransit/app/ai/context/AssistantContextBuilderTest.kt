package com.charles.crowdtransit.app.ai.context

import com.charles.crowdtransit.app.data.trip.ItineraryTextFormatter
import com.charles.crowdtransit.app.data.trip.TripPlanCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden fixtures shared with NavigationEngineTest (docs/routing/fixtures). */
class AssistantContextBuilderTest {

    private val codec = TripPlanCodec()
    private val formatter = ItineraryTextFormatter(codec)

    private val plan = codec.fromJson(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("plan-direct.json")) {
            "plan-direct.json missing from test resources"
        }.bufferedReader().readText(),
    )

    @Test
    fun `no trip planned or in progress`() {
        val text = AssistantContextBuilder.build(
            itineraryTextFormatter = formatter,
            activePlan = null,
            navInstruction = null,
            navNextInstruction = null,
            navDistanceToNextM = null,
            navOffRoute = false,
            selectedPlan = null,
            useImperial = true,
            nowLocalTime = "09:00",
        )
        assertTrue(text.contains("no trip planned or in progress"))
    }

    @Test
    fun `planned but not started trip includes itinerary text`() {
        val text = AssistantContextBuilder.build(
            itineraryTextFormatter = formatter,
            activePlan = null,
            navInstruction = null,
            navNextInstruction = null,
            navDistanceToNextM = null,
            navOffRoute = false,
            selectedPlan = plan,
            useImperial = true,
            nowLocalTime = "09:00",
        )
        assertTrue(text.contains("planned (but not started)"))
        assertTrue(text.contains(plan.from.name))
        assertTrue(text.contains(plan.to.name))
    }

    @Test
    fun `actively navigating includes live instruction and off-route flag`() {
        val text = AssistantContextBuilder.build(
            itineraryTextFormatter = formatter,
            activePlan = plan,
            navInstruction = "Get off at Whitehall (next stop)",
            navNextInstruction = "You've arrived",
            navDistanceToNextM = 120,
            navOffRoute = true,
            selectedPlan = null,
            useImperial = true,
            nowLocalTime = "15:12",
        )
        assertTrue(text.contains("actively navigating"))
        assertTrue(text.contains("Get off at Whitehall (next stop)"))
        assertTrue(text.contains("You've arrived"))
        assertTrue(text.contains("120m"))
        assertTrue(text.contains("off-route"))
    }

    @Test
    fun `on-route navigation omits the off-route warning`() {
        val text = AssistantContextBuilder.build(
            itineraryTextFormatter = formatter,
            activePlan = plan,
            navInstruction = "Ride to Whitehall — 2 stops to go",
            navNextInstruction = null,
            navDistanceToNextM = 800,
            navOffRoute = false,
            selectedPlan = null,
            useImperial = true,
            nowLocalTime = "15:05",
        )
        assertFalse(text.contains("off-route"))
    }

    @Test
    fun `static instruction carries the guardrails and is stable`() {
        val text = AssistantContextBuilder.STATIC_INSTRUCTION
        assertTrue(text.contains("Never invent them"))
        assertTrue(text.contains("not affiliated with any government or transit agency"))
        // No per-turn state (current time, trip data) may leak in here — this string
        // must stay identical across turns or LiteRtAssistantEngine.conversationFor()
        // tears down the model's Conversation and loses all chat memory.
        assertFalse(text.contains("current local time"))
        assertFalse(text.contains("trip planned"))
    }
}
