package com.charles.crowdtransit.app.data.trip

import com.charles.crowdtransit.model.TripPlan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Human-readable itinerary export + share-link building. Web twins:
 * web/src/utils/{itineraryText,shareLink}.ts. Share URLs carry the payload in the hash
 * so it never reaches server logs.
 */
@Singleton
class ItineraryTextFormatter @Inject constructor(
    private val codec: TripPlanCodec,
) {
    companion object {
        const val SHARE_BASE_URL = "https://chartmann1590.github.io/crowdsource-transit"
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    private fun time(iso: String?): String =
        iso?.let { runCatching { timeFormatter.format(Instant.parse(it)) }.getOrNull() } ?: "--:--"

    fun shareUrl(plan: TripPlan): String {
        // Slim for URLs: drop walk polylines/steps (viewer refetches or draws straight
        // lines); keep the transit stop sequence intact (the correctness guarantee).
        val slim = plan.copy(
            legs = plan.legs.map { leg ->
                if (leg.isWalk) leg.copy(poly = null, steps = null) else leg
            },
        )
        var blob = codec.encode(slim)
        if (blob.length > 2000) {
            blob = codec.encode(slim.copy(legs = slim.legs.map { it.copy(shapePoly = null) }))
        }
        return "$SHARE_BASE_URL/trip#d=$blob"
    }

    fun toText(plan: TripPlan, shareUrl: String? = null, useImperial: Boolean = true): String {
        val lines = mutableListOf<String>()
        val dep = plan.legs.firstOrNull()?.let { it.dep ?: it.board?.depUtc }
        val arr = plan.legs.lastOrNull()?.let { it.arr ?: it.alight?.arrUtc }
        val duration = runCatching { Duration.between(Instant.parse(dep!!), Instant.parse(arr!!)) }.getOrNull()
        val durationText = duration?.let {
            val h = it.toHours()
            val m = it.toMinutes() % 60
            if (h > 0) "${h}h ${m}m" else "$m min"
        } ?: ""

        lines.add("${plan.from.name.ifBlank { "Origin" }} → ${plan.to.name.ifBlank { "Destination" }}")
        lines.add("${time(dep)} – ${time(arr)} ($durationText)")
        lines.add("")

        plan.legs.forEachIndexed { i, leg ->
            val step = i + 1
            if (leg.isTransit) {
                val label = leg.route?.short?.ifBlank { leg.route.long } ?: ""
                val rideStops = (leg.stops?.size ?: 1) - 1
                lines.add(
                    "$step. Take ${leg.mode} $label toward ${leg.trip?.headsign} from ${leg.board?.name} " +
                        "(departs ${time(leg.board?.depUtc)})",
                )
                lines.add(
                    "   Ride $rideStops stop${if (rideStops == 1) "" else "s"}, get off at ${leg.alight?.name} " +
                        "(arrives ${time(leg.alight?.arrUtc)})",
                )
            } else {
                lines.add(
                    "$step. Walk ${com.charles.crowdtransit.app.util.DistanceFormat.format(leg.distM ?: 0, useImperial)} " +
                        "to ${leg.to?.name?.ifBlank { "your destination" }} (${time(leg.dep)}–${time(leg.arr)})",
                )
            }
        }

        if (shareUrl != null) {
            lines.add("")
            lines.add("View this trip: $shareUrl")
        }
        lines.add("")
        lines.add("Planned with CrowdTransit")
        return lines.joinToString("\n")
    }
}
