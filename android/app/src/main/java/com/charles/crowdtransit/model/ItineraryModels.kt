package com.charles.crowdtransit.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Shared itinerary interchange format, v1. Mirrors docs/routing/itinerary-spec.md and the
 * TypeScript types in web/src/types/itinerary.ts — the two implementations are verified
 * against the same golden fixtures in docs/routing/fixtures/.
 */

const val TRIP_PLAN_VERSION = 1

@JsonClass(generateAdapter = true)
data class TripPlan(
    val v: Int = TRIP_PLAN_VERSION,
    @Json(name = "planned_at") val plannedAt: String,
    val from: Place,
    val to: Place,
    val legs: List<Leg>,
)

@JsonClass(generateAdapter = true)
data class Place(
    val name: String = "",
    val lat: Double,
    val lng: Double,
)

/**
 * A single itinerary leg. Discriminated by [t]: "w" = walk, "r" = ride (transit).
 * Kept as one class (rather than a sealed hierarchy) so plain Moshi codegen can handle it
 * without a polymorphic adapter dependency; use [isWalk]/[isTransit] and the nullable
 * fields for the matching variant.
 */
@JsonClass(generateAdapter = true)
data class Leg(
    val t: String,

    // Walk fields ("w")
    val from: Place? = null,
    val to: Place? = null,
    val dep: String? = null,
    val arr: String? = null,
    @Json(name = "dist_m") val distM: Int? = null,
    val poly: String? = null,
    val steps: List<WalkStep>? = null,

    // Transit fields ("r")
    val mode: String? = null,
    val route: RouteRef? = null,
    val trip: TripRef? = null,
    val board: BoardPoint? = null,
    val alight: AlightPoint? = null,
    val stops: List<TripStop>? = null,
    @Json(name = "shape_poly") val shapePoly: String? = null,
) {
    val isWalk: Boolean get() = t == LEG_WALK
    val isTransit: Boolean get() = t == LEG_TRANSIT

    companion object {
        const val LEG_WALK = "w"
        const val LEG_TRANSIT = "r"
    }
}

@JsonClass(generateAdapter = true)
data class WalkStep(
    val text: String,
    @Json(name = "dist_m") val distM: Int,
)

@JsonClass(generateAdapter = true)
data class RouteRef(
    @Json(name = "onestop_id") val onestopId: String,
    val short: String = "",
    val long: String = "",
    val color: String = "",
    val agency: String = "",
)

@JsonClass(generateAdapter = true)
data class TripRef(
    @Json(name = "trip_id") val tripId: String,
    val headsign: String = "",
)

@JsonClass(generateAdapter = true)
data class BoardPoint(
    @Json(name = "stop_id") val stopId: String,
    val name: String = "",
    val lat: Double,
    val lng: Double,
    @Json(name = "dep_utc") val depUtc: String,
)

@JsonClass(generateAdapter = true)
data class AlightPoint(
    @Json(name = "stop_id") val stopId: String,
    val name: String = "",
    val lat: Double,
    val lng: Double,
    @Json(name = "arr_utc") val arrUtc: String,
)

@JsonClass(generateAdapter = true)
data class TripStop(
    val id: String,
    val name: String = "",
    val lat: Double,
    val lng: Double,
    @Json(name = "arr_utc") val arrUtc: String,
)
