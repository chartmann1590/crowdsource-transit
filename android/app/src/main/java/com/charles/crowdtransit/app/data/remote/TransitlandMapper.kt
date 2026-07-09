package com.charles.crowdtransit.app.data.remote

import com.charles.crowdtransit.model.RouteStopSummary
import com.charles.crowdtransit.model.RouteWithStops
import com.charles.crowdtransit.model.ServedDeparture
import com.charles.crowdtransit.model.ServedRoute
import com.charles.crowdtransit.model.Stop

fun gtfsRouteTypeToTransitType(routeType: Int?): String = when (routeType) {
    0 -> "tram"
    1 -> "subway"
    2 -> "train"
    3 -> "bus"
    4 -> "ferry"
    else -> "transit"
}

fun normalizeHexColor(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return if (raw.startsWith("#")) raw else "#$raw"
}

fun routeColorFor(transitType: String, rawColor: String?): String {
    val normalized = normalizeHexColor(rawColor)
    if (normalized.isNotEmpty()) return normalized
    return when (transitType) {
        "bus" -> "#00A862"
        "train" -> "#2563EB"
        "subway" -> "#8B2FC9"
        "ferry" -> "#0891B2"
        "tram" -> "#EA7317"
        else -> "#00A862"
    }
}

fun TransitlandStop.toStop(ratingSum: Long = 0L, ratingCount: Long = 0L, commentCount: Long = 0L): Stop {
    val coordinates = geometry?.coordinates.orEmpty()
    val lng = coordinates.getOrNull(0) ?: 0.0
    val lat = coordinates.getOrNull(1) ?: 0.0

    val routeIdsMap = mutableMapOf<String, Boolean>()
    val transitTypesSet = mutableSetOf<String>()
    val agencyNamesSet = linkedSetOf<String>()
    routeStops?.forEach { rs ->
        rs.route?.let { r ->
            r.onestopId?.let { id ->
                routeIdsMap[id] = true
            }
            val type = gtfsRouteTypeToTransitType(r.routeType)
            if (type != "transit") {
                transitTypesSet.add(type)
            }
            r.agency?.agencyName?.takeIf { it.isNotBlank() }?.let { agencyNamesSet.add(it) }
        }
    }

    return Stop(
        stopId = onestopId ?: "",
        name = stopName ?: onestopId ?: "",
        desc = stopDesc ?: "",
        lat = lat,
        lng = lng,
        code = stopId ?: "",
        country = place?.countryName ?: "",
        state = place?.stateName ?: "",
        city = "",
        transitTypes = transitTypesSet.toList(),
        routeIds = routeIdsMap,
        agencyNames = agencyNamesSet.toList(),
        ratingSum = ratingSum,
        ratingCount = ratingCount,
        commentCount = commentCount,
        crowdsourced = false,
        verified = true,
        active = true,
    )
}

fun TransitlandStop.toStaticRoutes(): List<ServedRoute> {
    val routes = mutableListOf<ServedRoute>()
    routeStops?.forEach { rs ->
        rs.route?.let { r ->
            r.onestopId?.let { onestopId ->
                val transitType = gtfsRouteTypeToTransitType(r.routeType)
                val color = routeColorFor(transitType, r.routeColor)
                val textColor = normalizeHexColor(r.routeTextColor).ifBlank { "#FFFFFF" }
                val agencyName = r.agency?.agencyName ?: ""
                routes.add(
                    ServedRoute(
                        onestopId = onestopId,
                        routeId = r.routeId ?: "",
                        shortName = r.routeShortName ?: "",
                        longName = r.routeLongName ?: "",
                        routeType = r.routeType,
                        transitType = transitType,
                        color = color,
                        textColor = textColor,
                        agencyName = agencyName,
                        nextDepartureTime = null,
                    )
                )
            }
        }
    }
    return routes.distinctBy { it.onestopId }.sortedBy { it.shortName.ifBlank { it.longName } }
}

fun TransitlandFullRoute.toRouteWithStops(): RouteWithStops {
    val transitType = gtfsRouteTypeToTransitType(routeType)
    val color = routeColorFor(transitType, routeColor)
    val textColor = normalizeHexColor(routeTextColor).ifBlank { "#FFFFFF" }
    val stops = routeStops.orEmpty().mapNotNull { entry ->
        val stop = entry.stop ?: return@mapNotNull null
        val coords = stop.geometry?.coordinates.orEmpty()
        RouteStopSummary(
            gtfsStopId = stop.stopId ?: "",
            name = stop.stopName ?: "",
            lat = coords.getOrNull(1) ?: 0.0,
            lng = coords.getOrNull(0) ?: 0.0,
        )
    }
    return RouteWithStops(
        onestopId = onestopId ?: "",
        routeId = routeId ?: "",
        shortName = routeShortName ?: "",
        longName = routeLongName ?: "",
        transitType = transitType,
        color = color,
        textColor = textColor,
        agencyName = agency?.agencyName ?: "",
        stops = stops,
    )
}

// === Stop Departures mapping ===

private fun parseGtfsTimeSeconds(raw: String?): Int? {
    if (raw == null) return null
    val parts = raw.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return parts[0] * 3600 + parts[1] * 60 + parts[2]
}

private fun secondsToHHMMSS(total: Int): String {
    val wrapped = ((total % 86400) + 86400) % 86400
    val hh = (wrapped / 3600).toString().padStart(2, '0')
    val mm = ((wrapped % 3600) / 60).toString().padStart(2, '0')
    val ss = (wrapped % 60).toString().padStart(2, '0')
    return "$hh:$mm:$ss"
}

// Transitland provides an absolute UTC instant for each scheduled/estimated time
// (`scheduled_utc`/`estimated_utc`), computed server-side from the stop's own
// timezone. Comparing against this (rather than the device's local wall clock)
// keeps "next departure"/"upcoming" correct for stops outside the device's timezone.
private fun departureUtcMillis(t: TransitlandTimeInfo?): Long? {
    val iso = t?.estimatedUtc ?: t?.scheduledUtc ?: return null
    return try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        null
    }
}

data class RouteAndSchedule(
    val routes: List<ServedRoute> = emptyList(),
    val upcoming: List<ServedDeparture> = emptyList(),
)

/**
 * Convert a Transitland departures response into deduplicated routes serving this stop
 * and a sorted list of upcoming departures (filtered to after "now").
 */
fun TransitlandDeparturesResponse.toRoutesAndSchedule(
    upcomingWindowSeconds: Int = 86400,
    maxUpcoming: Int = 25,
): RouteAndSchedule {
    val routesMap = LinkedHashMap<String, ServedRoute>()
    val nextDepartureUtcMillis = mutableMapOf<String, Long>()
    val upcoming = mutableListOf<Pair<Long, ServedDeparture>>()
    val nowMillis = System.currentTimeMillis()
    val cutoffMillis = nowMillis + upcomingWindowSeconds * 1000L

    for (stop in this.stops) {
        for (dep in stop.departures) {
            val route = dep.trip.route ?: continue
            val onestopId = route.onestopId ?: continue
            val transitType = gtfsRouteTypeToTransitType(route.routeType)
            val color = routeColorFor(transitType, route.routeColor)
            val textColor = normalizeHexColor(route.routeTextColor).ifBlank { "#FFFFFF" }
            val agencyName = route.agency?.agencyName ?: ""

            val routeEntry = routesMap.getOrPut(onestopId) {
                ServedRoute(
                    onestopId = onestopId,
                    routeId = route.routeId ?: "",
                    shortName = route.routeShortName ?: "",
                    longName = route.routeLongName ?: "",
                    routeType = route.routeType,
                    transitType = transitType,
                    color = color,
                    textColor = textColor,
                    agencyName = agencyName,
                    nextDepartureTime = null,
                )
            }

            // depUtcMillis is the authoritative instant (from the stop's own timezone) used
            // for filtering/ordering; depSec (local wall-clock HH:MM:SS) is display-only.
            val depUtcMillis = departureUtcMillis(dep.departure) ?: departureUtcMillis(dep.arrival)
            val depSec = parseGtfsTimeSeconds(dep.departureTime ?: dep.arrivalTime)
            if (depUtcMillis != null && depSec != null) {
                if (depUtcMillis >= nowMillis) {
                    val currentNextMillis = nextDepartureUtcMillis[onestopId]
                    if (currentNextMillis == null || depUtcMillis < currentNextMillis) {
                        nextDepartureUtcMillis[onestopId] = depUtcMillis
                        routesMap[onestopId] = routeEntry.copy(nextDepartureTime = secondsToHHMMSS(depSec))
                    }
                }
                if (depUtcMillis >= nowMillis && depUtcMillis <= cutoffMillis) {
                    val isRealtime = dep.scheduleRelationship != null && dep.scheduleRelationship != "STATIC"
                    upcoming.add(
                        depUtcMillis to ServedDeparture(
                            routeOnestopId = onestopId,
                            routeId = route.routeId ?: "",
                            routeShortName = route.routeShortName ?: "",
                            routeLongName = route.routeLongName ?: "",
                            routeType = route.routeType,
                            transitType = transitType,
                            routeColor = color,
                            headsign = dep.trip?.tripHeadsign ?: dep.stopHeadsign ?: "",
                            departureTime = secondsToHHMMSS(depSec),
                            arrivalTime = dep.arrivalTime?.let { secondsToHHMMSS(parseGtfsTimeSeconds(it)!!) },
                            interpolated = dep.interpolated == 1,
                            isRealtime = isRealtime,
                        ),
                    )
                }
            }
        }
    }

    val sortedRoutes = routesMap.values.sortedWith(compareBy(
        { nextDepartureUtcMillis[it.onestopId] ?: Long.MAX_VALUE },
        { it.shortName.ifBlank { it.longName } },
    ))
    val sortedUpcoming = upcoming.sortedBy { it.first }.take(maxUpcoming).map { it.second }
    return RouteAndSchedule(sortedRoutes, sortedUpcoming)
}
