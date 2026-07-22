package com.charles.crowdtransit.app.data.routing

import com.charles.crowdtransit.app.data.local.dao.GtfsDao
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarEntity
import com.charles.crowdtransit.app.data.remote.gtfsRouteTypeToTransitType
import com.charles.crowdtransit.app.domain.routing.DepartureOption
import com.charles.crowdtransit.app.domain.routing.GtfsDataSource
import com.charles.crowdtransit.app.domain.routing.RouteMeta
import com.charles.crowdtransit.app.domain.routing.StopCandidate
import com.charles.crowdtransit.app.domain.routing.TripDetails
import com.charles.crowdtransit.app.domain.routing.TripStopTime
import com.charles.crowdtransit.app.util.PolylineCodec
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline [GtfsDataSource] over the downloaded GTFS Room tables (Phase F). Times use
 * the device timezone (v1 assumption: users plan trips where they are). Handles
 * >24:00:00 stop_times by also considering the previous service day. Trip references
 * are synthetic Longs mapped to local trip ids (the interface's tripIntId is
 * Transitland-shaped).
 */
@Singleton
class RoomGtfsDataSource @Inject constructor(
    private val dao: GtfsDao,
) : GtfsDataSource {

    private val tripIdBySynthetic = ConcurrentHashMap<Long, String>()
    private val syntheticByTripId = ConcurrentHashMap<String, Long>()
    private val nextSynthetic = AtomicLong(1)
    private val dateFormat = DateTimeFormatter.BASIC_ISO_DATE

    private fun syntheticId(tripId: String): Long =
        syntheticByTripId.computeIfAbsent(tripId) { id ->
            nextSynthetic.getAndIncrement().also { tripIdBySynthetic[it] = id }
        }

    /** True when every candidate stop key resolves to a downloaded GTFS stop. */
    suspend fun covers(stopKeys: List<String>): Boolean {
        if (stopKeys.isEmpty()) return false
        return dao.stopsByIds(stopKeys).size == stopKeys.size
    }

    override suspend fun stopsNear(lat: Double, lng: Double, radiusM: Int, limit: Int): List<StopCandidate> {
        val latDelta = radiusM / 111_000.0
        val lngDelta = radiusM / (111_000.0 * Math.cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        return dao.stopsInBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta, limit * 3)
            .map { StopCandidate(key = it.stopId, name = it.name, lat = it.lat, lng = it.lng) }
            .take(limit * 3)
    }

    private fun activeServiceIds(
        calendars: List<GtfsCalendarEntity>,
        added: Set<String>,
        removed: Set<String>,
        date: LocalDate,
    ): Set<String> {
        val dateInt = date.format(dateFormat).toInt()
        val dayBit = 1 shl (date.dayOfWeek.value - 1) // Monday=bit0 … Sunday=bit6
        val regular = calendars
            .filter { (it.daysMask and dayBit) != 0 && dateInt >= it.startDate && dateInt <= it.endDate }
            .map { it.serviceId }
        return (regular + added).toSet() - removed
    }

    override suspend fun departures(stopKey: String, notBeforeUtcMs: Long, windowSec: Int): List<DepartureOption> {
        val zone = ZoneId.systemDefault()
        val agencies = dao.agenciesWithSchedules()
        if (agencies.isEmpty()) return emptyList()
        val calendars = dao.calendars(agencies)

        val result = mutableListOf<DepartureOption>()
        // Service day of notBefore, plus the previous day for >24:00:00 stop_times.
        val notBeforeDate = java.time.Instant.ofEpochMilli(notBeforeUtcMs).atZone(zone).toLocalDate()
        for (dayOffset in 0 downTo -1) {
            val serviceDate = notBeforeDate.plusDays(dayOffset.toLong())
            val midnightMs = serviceDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val minDepSec = ((notBeforeUtcMs - midnightMs) / 1000).toInt()
            val maxDepSec = minDepSec + windowSec
            if (maxDepSec < 0) continue

            val dateInt = serviceDate.format(dateFormat).toInt()
            val exceptions = dao.calendarDates(agencies, dateInt)
            val active = activeServiceIds(
                calendars,
                added = exceptions.filter { it.exceptionType == 1 }.map { it.serviceId }.toSet(),
                removed = exceptions.filter { it.exceptionType == 2 }.map { it.serviceId }.toSet(),
                date = serviceDate,
            )
            if (active.isEmpty()) continue

            for (row in dao.departuresAtStop(stopKey, minDepSec.coerceAtLeast(0), maxDepSec)) {
                if (row.serviceId !in active) continue
                val transitType = gtfsRouteTypeToTransitType(row.routeType)
                result.add(
                    DepartureOption(
                        route = RouteMeta(
                            onestopId = row.routeId,
                            shortName = row.shortName,
                            longName = row.longName,
                            color = normalizeColor(row.color, transitType),
                            agency = row.routeId.substringBefore('|'),
                            mode = transitType,
                        ),
                        headsign = row.headsign,
                        tripIntId = syntheticId(row.tripId),
                        boardStopSequence = row.seq,
                        depUtcMs = midnightMs + row.depSec * 1000L,
                    ),
                )
            }
        }
        return result.sortedBy { it.depUtcMs }
    }

    override suspend fun tripDetails(routeOnestopId: String, tripIntId: Long): TripDetails? {
        val tripId = tripIdBySynthetic[tripIntId] ?: return null
        val trip = dao.trip(tripId) ?: return null
        val stopTimes = dao.stopTimesForTrip(tripId)
        if (stopTimes.size < 2) return null
        val stops = dao.stopsByIds(stopTimes.map { it.stopId }.distinct()).associateBy { it.stopId }
        val mapped = stopTimes.mapNotNull { st ->
            val stop = stops[st.stopId] ?: return@mapNotNull null
            TripStopTime(
                seq = st.seq,
                gtfsStopId = stop.gtfsStopId,
                stopKey = stop.stopId,
                name = stop.name,
                lat = stop.lat,
                lng = stop.lng,
                arrivalSec = st.arrSec,
                departureSec = st.depSec,
            )
        }
        if (mapped.size < 2) return null
        val shape = trip.shapeId.takeIf { it.isNotEmpty() }
            ?.let { dao.shape(it) }
            ?.encodedPolyline
            ?.let { PolylineCodec.decode(it) }
        return TripDetails(
            gtfsTripId = tripId.substringAfter('|'),
            headsign = trip.headsign,
            stopTimes = mapped,
            shape = shape,
        )
    }

    private fun normalizeColor(raw: String, transitType: String): String {
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty()) return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return when (transitType) {
            "bus" -> "#00A862"
            "train" -> "#2563EB"
            "subway" -> "#8B2FC9"
            "ferry" -> "#0891B2"
            "tram" -> "#EA7317"
            else -> "#00A862"
        }
    }
}
