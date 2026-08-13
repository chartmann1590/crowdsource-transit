package com.charles.crowdtransit.app.ai.context

import com.charles.crowdtransit.app.data.navigation.NavigationSessionRepository
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.data.trip.ItineraryTextFormatter
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.model.TripPlan
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds Hopper's system instruction from whatever the app already knows about the
 * rider's trip — no new plumbing needed, everything here is read from existing
 * singletons (NavigationSessionRepository, TripSessionHolder). Kept as a pure function
 * over explicit inputs ([build]) so it's unit-testable against the shared golden
 * fixtures in docs/routing/fixtures/, the same ones NavigationEngineTest uses.
 */
@Singleton
class AssistantContextBuilder @Inject constructor(
    private val navigationSession: NavigationSessionRepository,
    private val tripSession: TripSessionHolder,
    private val itineraryTextFormatter: ItineraryTextFormatter,
    private val userPreferences: UserPreferencesStore,
) {
    /**
     * The system instruction, identical for the whole chat session — identity and
     * guardrails only, nothing that changes turn to turn. Deliberately excludes the
     * clock and trip state (see [buildCurrent]): LiteRtAssistantEngine.conversationFor()
     * tears down and recreates the model's Conversation — wiping all prior turn history
     * — whenever the system instruction it's holding differs from the current one. This
     * used to embed the current time to the minute, which meant the conversation (and
     * with it, the model's memory of anything said earlier) reset on almost every single
     * message, since a CPU-backend reply already takes 20-30s. Confirmed live on-device:
     * Hopper appeared to have no memory of the previous turn at all.
     */
    fun staticInstruction(): String = STATIC_INSTRUCTION

    /**
     * Live trip/time context, rebuilt fresh every turn and prepended to the user's
     * message text (not the system instruction) so the model always sees current state
     * without forcing [staticInstruction] to change and blowing away conversation memory.
     */
    suspend fun buildCurrent(): String {
        val useImperial = userPreferences.useImperialUnits.first()
        val activePlan = navigationSession.activePlan
        val navState = navigationSession.state.value
        val selectedPlan = tripSession.selectedPlan.value
        return build(
            itineraryTextFormatter = itineraryTextFormatter,
            activePlan = activePlan,
            navInstruction = navState?.instruction,
            navNextInstruction = navState?.nextInstruction,
            navDistanceToNextM = navState?.distanceToNextM,
            navOffRoute = navState?.offRoute ?: false,
            selectedPlan = selectedPlan,
            useImperial = useImperial,
            nowLocalTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
        )
    }

    companion object {
        val STATIC_INSTRUCTION = listOf(
            "You are Hopper, a friendly on-device transit assistant inside the CrowdTransit " +
                "app. You help riders plan trips and understand the trip they're on.",
            "",
            "GUARDRAILS (follow strictly):",
            "- Only state departure times, arrival times, and stop names that appear in the context below or in a tool result. Never invent them.",
            "- If you don't know something, say so and suggest checking the transit agency directly.",
            "- CrowdTransit is not affiliated with any government or transit agency, and neither are you. Never imply otherwise.",
            "- Keep replies short and conversational — this is a chat, not a report.",
            "- If the rider wants to start or end a trip at where they currently are (e.g. \"from here\", " +
                "\"near me\"), pass the exact text \"current location\" as that place name to planTrip — " +
                "don't ask them for an address.",
            "- Each user message is preceded by a '[Current status]' block with the rider's live trip and time context — use it, but don't repeat it verbatim back to the rider.",
        ).joinToString("\n")

        /**
         * Pure — takes every dependency as a parameter so it's unit-testable without the
         * Hilt graph (UserPreferencesStore, in particular, needs an Android Context that
         * plain JVM tests can't provide). [buildCurrent] is the real call site.
         */
        fun build(
            itineraryTextFormatter: ItineraryTextFormatter,
            activePlan: TripPlan?,
            navInstruction: String?,
            navNextInstruction: String?,
            navDistanceToNextM: Int?,
            navOffRoute: Boolean,
            selectedPlan: TripPlan?,
            useImperial: Boolean,
            nowLocalTime: String,
        ): String {
            val lines = mutableListOf<String>()
            lines.add("[Current status]")
            lines.add("The current local time is $nowLocalTime.")

            when {
                activePlan != null && navInstruction != null -> {
                    lines.add("The rider is actively navigating this trip right now:")
                    lines.add(itineraryTextFormatter.toText(activePlan, shareUrl = null, useImperial = useImperial))
                    lines.add("")
                    lines.add("Current instruction: $navInstruction")
                    if (navNextInstruction != null) lines.add("Next instruction: $navNextInstruction")
                    if (navDistanceToNextM != null) lines.add("Distance to next waypoint: ${navDistanceToNextM}m")
                    if (navOffRoute) lines.add("The rider appears to have gone off-route.")
                }

                selectedPlan != null -> {
                    lines.add("The rider has planned (but not started) this trip:")
                    lines.add(itineraryTextFormatter.toText(selectedPlan, shareUrl = null, useImperial = useImperial))
                }

                else -> {
                    lines.add("The rider has no trip planned or in progress right now.")
                }
            }

            return lines.joinToString("\n")
        }
    }
}
