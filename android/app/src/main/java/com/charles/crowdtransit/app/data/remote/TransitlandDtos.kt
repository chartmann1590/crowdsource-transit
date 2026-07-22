package com.charles.crowdtransit.app.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransitlandStopsResponse(
    val stops: List<TransitlandStop> = emptyList(),
    val meta: TransitlandMeta? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandMeta(
    val after: Long? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandStop(
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "stop_id") val stopId: String? = null,
    @Json(name = "stop_name") val stopName: String? = null,
    @Json(name = "stop_desc") val stopDesc: String? = null,
    val geometry: TransitlandGeometry? = null,
    val place: TransitlandPlace? = null,
    @Json(name = "route_stops") val routeStops: List<TransitlandRouteStop>? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandRouteStop(
    val route: TransitlandRouteInfo? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandGeometry(
    val type: String = "",
    val coordinates: List<Double> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandPlace(
    @Json(name = "adm0_name") val countryName: String? = null,
    @Json(name = "adm1_name") val stateName: String? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandRoutesResponse(
    val routes: List<TransitlandRoute> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandRoute(
    @Json(name = "route_type") val routeType: Int? = null,
)

// === Route detail (route + its ordered stops) ===

@JsonClass(generateAdapter = true)
data class TransitlandFullRoutesResponse(
    val routes: List<TransitlandFullRoute> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandFullRoute(
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "route_id") val routeId: String? = null,
    @Json(name = "route_short_name") val routeShortName: String? = null,
    @Json(name = "route_long_name") val routeLongName: String? = null,
    @Json(name = "route_type") val routeType: Int? = null,
    @Json(name = "route_color") val routeColor: String? = null,
    @Json(name = "route_text_color") val routeTextColor: String? = null,
    val agency: TransitlandAgencyInfo? = null,
    @Json(name = "route_stops") val routeStops: List<TransitlandRouteStopEntry>? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandRouteStopEntry(
    val stop: TransitlandRouteStopPoint? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandRouteStopPoint(
    @Json(name = "stop_id") val stopId: String? = null,
    @Json(name = "stop_name") val stopName: String? = null,
    val geometry: TransitlandGeometry? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandOperatorsResponse(
    val operators: List<TransitlandOperator> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandOperator(
    @Json(name = "onestop_id") val onestopId: String = "",
    val name: String? = null,
    @Json(name = "short_name") val shortName: String? = null,
    val feeds: List<TransitlandOperatorFeed>? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandOperatorFeed(
    @Json(name = "onestop_id") val onestopId: String? = null,
    val spec: String? = null,
)

// === Stop Departures ===

@JsonClass(generateAdapter = true)
data class TransitlandDeparturesResponse(
    val stops: List<TransitlandDepartureStop> = emptyList(),
    val meta: TransitlandMeta? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandDepartureStop(
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "stop_name") val stopName: String? = null,
    val departures: List<TransitlandDeparture> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandDeparture(
    // Transitland nests route info under `trip.route`, not as a sibling of `trip`.
    val trip: TransitlandTrip = TransitlandTrip(),
    val arrival: TransitlandTimeInfo? = null,
    val departure: TransitlandTimeInfo? = null,
    @Json(name = "arrival_time") val arrivalTime: String? = null,
    @Json(name = "departure_time") val departureTime: String? = null,
    @Json(name = "stop_headsign") val stopHeadsign: String? = null,
    val interpolated: Int? = null,
    // Transitland returns this as a plain string (e.g. "STATIC"), not an array.
    @Json(name = "schedule_relationship") val scheduleRelationship: String? = null,
    // Board position of this stop within the trip's stop sequence.
    @Json(name = "stop_sequence") val stopSequence: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandTimeInfo(
    val scheduled: String? = null,
    @Json(name = "scheduled_utc") val scheduledUtc: String? = null,
    val estimated: String? = null,
    @Json(name = "estimated_utc") val estimatedUtc: String? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandTrip(
    @Json(name = "trip_id") val tripId: String? = null,
    @Json(name = "trip_headsign") val tripHeadsign: String? = null,
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "trip_short_name") val tripShortName: String? = null,
    val route: TransitlandRouteInfo? = null,
    // Transitland internal integer trip id — the /routes/{key}/trips/{id} path key.
    val id: Long? = null,
)

// === Trip detail (full stop_times + shape) ===

@JsonClass(generateAdapter = true)
data class TransitlandTripDetailResponse(
    val trips: List<TransitlandTripDetail> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandTripDetail(
    val id: Long? = null,
    @Json(name = "trip_id") val tripId: String? = null,
    @Json(name = "trip_headsign") val tripHeadsign: String? = null,
    @Json(name = "stop_times") val stopTimes: List<TransitlandTripStopTime>? = null,
    val shape: TransitlandTripShape? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandTripStopTime(
    @Json(name = "arrival_time") val arrivalTime: String? = null,
    @Json(name = "departure_time") val departureTime: String? = null,
    @Json(name = "stop_sequence") val stopSequence: Int? = null,
    val stop: TransitlandTripStop? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandTripStop(
    val id: Long? = null,
    @Json(name = "stop_id") val stopId: String? = null,
    @Json(name = "stop_name") val stopName: String? = null,
    val geometry: TransitlandGeometry? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandTripShape(
    // include_geometry=true: LineString whose points may carry a 3rd elevation element.
    val geometry: TransitlandLineGeometry? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandLineGeometry(
    val type: String = "",
    val coordinates: List<List<Double>> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandRouteInfo(
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "route_id") val routeId: String? = null,
    @Json(name = "route_short_name") val routeShortName: String? = null,
    @Json(name = "route_long_name") val routeLongName: String? = null,
    @Json(name = "route_type") val routeType: Int? = null,
    @Json(name = "route_color") val routeColor: String? = null,
    @Json(name = "route_text_color") val routeTextColor: String? = null,
    val agency: TransitlandAgencyInfo? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandAgencyInfo(
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "agency_name") val agencyName: String? = null,
    @Json(name = "agency_id") val agencyId: String? = null,
)
