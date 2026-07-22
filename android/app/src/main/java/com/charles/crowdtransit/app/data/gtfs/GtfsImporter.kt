package com.charles.crowdtransit.app.data.gtfs

import com.charles.crowdtransit.app.data.local.dao.GtfsDao
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarDateEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsRouteEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsShapeEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsStopEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsStopTimeEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsTripEntity
import com.charles.crowdtransit.app.domain.routing.LngLat
import com.charles.crowdtransit.app.util.PolylineCodec
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams a GTFS zip into the offline Room tables (Phase F). Batched inserts of
 * [BATCH_SIZE]; shape points are aggregated per shape and stored as one encoded
 * polyline row. transfers.txt is intentionally skipped for v1 — the router does
 * same-stop + nearby-stop transfers on its own.
 */
@Singleton
class GtfsImporter @Inject constructor(
    private val dao: GtfsDao,
) {
    companion object {
        private const val BATCH_SIZE = 5000
    }

    data class Progress(val table: String, val rows: Long)

    suspend fun import(agencyOnestopId: String, zipStream: InputStream, onProgress: (Progress) -> Unit = {}) {
        clearAgency(agencyOnestopId)

        val zip = ZipInputStream(zipStream.buffered())
        var totalRows = 0L
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.substringAfterLast('/')
            val reader = zip.bufferedReader()
            when (name) {
                "stops.txt" -> totalRows += importStops(agencyOnestopId, reader, onProgress)
                "routes.txt" -> totalRows += importRoutes(agencyOnestopId, reader, onProgress)
                "trips.txt" -> totalRows += importTrips(agencyOnestopId, reader, onProgress)
                "stop_times.txt" -> totalRows += importStopTimes(agencyOnestopId, reader, onProgress)
                "calendar.txt" -> totalRows += importCalendar(agencyOnestopId, reader, onProgress)
                "calendar_dates.txt" -> totalRows += importCalendarDates(agencyOnestopId, reader, onProgress)
                "shapes.txt" -> totalRows += importShapes(agencyOnestopId, reader, onProgress)
                else -> Unit // skip agency.txt, transfers.txt, fare files, …
            }
        }
        dao.computeTripLastSeqs(agencyOnestopId)
    }

    suspend fun clearAgency(agency: String) {
        dao.deleteStopTimes(agency)
        dao.deleteTrips(agency)
        dao.deleteRoutes(agency)
        dao.deleteStops(agency)
        dao.deleteCalendars(agency)
        dao.deleteCalendarDates(agency)
        dao.deleteShapes(agency)
    }

    private fun scoped(agency: String, id: String) = "$agency|$id"

    fun parseGtfsTimeSec(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(":")
        if (parts.size != 3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val s = parts[2].toIntOrNull() ?: return null
        return h * 3600 + m * 60 + s
    }

    private suspend fun <T> batched(
        agency: String,
        table: String,
        reader: java.io.BufferedReader,
        onProgress: (Progress) -> Unit,
        toEntity: (Map<String, String>) -> T?,
        insert: suspend (List<T>) -> Unit,
    ): Long {
        val batch = ArrayList<T>(BATCH_SIZE)
        var rows = 0L
        // Pull-based parse: memory stays bounded at one batch even for huge stop_times.
        for (record in GtfsCsvParser.records(reader)) {
            toEntity(record)?.let { batch.add(it) }
            if (batch.size >= BATCH_SIZE) {
                insert(batch.toList())
                rows += batch.size
                batch.clear()
                onProgress(Progress(table, rows))
            }
        }
        if (batch.isNotEmpty()) {
            insert(batch.toList())
            rows += batch.size
            onProgress(Progress(table, rows))
        }
        return rows
    }

    private suspend fun importStops(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long =
        batched(agency, "stops", reader, onProgress, { r ->
            val id = r["stop_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            val lat = r["stop_lat"]?.toDoubleOrNull() ?: return@batched null
            val lng = r["stop_lon"]?.toDoubleOrNull() ?: return@batched null
            GtfsStopEntity(
                stopId = scoped(agency, id),
                agencyOnestopId = agency,
                gtfsStopId = id,
                name = r["stop_name"].orEmpty(),
                lat = lat,
                lng = lng,
            )
        }, dao::insertStops)

    private suspend fun importRoutes(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long =
        batched(agency, "routes", reader, onProgress, { r ->
            val id = r["route_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            GtfsRouteEntity(
                routeId = scoped(agency, id),
                agencyOnestopId = agency,
                gtfsRouteId = id,
                shortName = r["route_short_name"].orEmpty(),
                longName = r["route_long_name"].orEmpty(),
                routeType = r["route_type"]?.toIntOrNull() ?: 3,
                color = r["route_color"].orEmpty(),
            )
        }, dao::insertRoutes)

    private suspend fun importTrips(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long =
        batched(agency, "trips", reader, onProgress, { r ->
            val id = r["trip_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            val routeId = r["route_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            GtfsTripEntity(
                tripId = scoped(agency, id),
                agencyOnestopId = agency,
                routeId = scoped(agency, routeId),
                serviceId = scoped(agency, r["service_id"].orEmpty()),
                headsign = r["trip_headsign"].orEmpty(),
                shapeId = r["shape_id"]?.takeIf { it.isNotBlank() }?.let { scoped(agency, it) }.orEmpty(),
                lastSeq = 0, // filled by computeTripLastSeqs after stop_times import
            )
        }, dao::insertTrips)

    private suspend fun importStopTimes(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long =
        batched(agency, "stop_times", reader, onProgress, { r ->
            val tripId = r["trip_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            val stopId = r["stop_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            val seq = r["stop_sequence"]?.toIntOrNull() ?: return@batched null
            val arr = parseGtfsTimeSec(r["arrival_time"])
            val dep = parseGtfsTimeSec(r["departure_time"]) ?: arr
            // Untimed (interpolated) stop_times rows are kept with -1 markers? No — skip:
            // the router needs real times, and timepoint rows bracket the trip.
            if (arr == null || dep == null) return@batched null
            GtfsStopTimeEntity(
                tripId = scoped(agency, tripId),
                seq = seq,
                stopId = scoped(agency, stopId),
                arrSec = arr,
                depSec = dep,
            )
        }, dao::insertStopTimes)

    private suspend fun importCalendar(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long =
        batched(agency, "calendar", reader, onProgress, { r ->
            val id = r["service_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            var mask = 0
            listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                .forEachIndexed { i, day -> if (r[day] == "1") mask = mask or (1 shl i) }
            GtfsCalendarEntity(
                serviceId = scoped(agency, id),
                agencyOnestopId = agency,
                daysMask = mask,
                startDate = r["start_date"]?.toIntOrNull() ?: 0,
                endDate = r["end_date"]?.toIntOrNull() ?: 99999999,
            )
        }, dao::insertCalendars)

    private suspend fun importCalendarDates(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long =
        batched(agency, "calendar_dates", reader, onProgress, { r ->
            val id = r["service_id"]?.takeIf { it.isNotBlank() } ?: return@batched null
            val date = r["date"]?.toIntOrNull() ?: return@batched null
            GtfsCalendarDateEntity(
                serviceId = scoped(agency, id),
                agencyOnestopId = agency,
                date = date,
                exceptionType = r["exception_type"]?.toIntOrNull() ?: 1,
            )
        }, dao::insertCalendarDates)

    /**
     * Shapes aggregate per shape_id into one encoded polyline row. Memory is bounded to
     * one shape at a time by assuming shapes.txt groups rows by shape_id (standard);
     * an interleaved feed would just REPLACE with the last group seen.
     */
    private suspend fun importShapes(agency: String, reader: java.io.BufferedReader, onProgress: (Progress) -> Unit): Long {
        data class Pt(val seq: Int, val lat: Double, val lng: Double)

        var currentId: String? = null
        val points = mutableListOf<Pt>()
        val batch = mutableListOf<GtfsShapeEntity>()
        var rows = 0L

        suspend fun flushShape() {
            val id = currentId ?: return
            if (points.isNotEmpty()) {
                batch.add(
                    GtfsShapeEntity(
                        shapeId = scoped(agency, id),
                        agencyOnestopId = agency,
                        encodedPolyline = PolylineCodec.encode(
                            points.sortedBy { it.seq }.map { LngLat(it.lng, it.lat) },
                        ),
                    ),
                )
            }
            points.clear()
            if (batch.size >= 200) {
                dao.insertShapes(batch.toList())
                rows += batch.size
                batch.clear()
                onProgress(Progress("shapes", rows))
            }
        }

        for (r in GtfsCsvParser.records(reader)) {
            val id = r["shape_id"]?.takeIf { it.isNotBlank() } ?: continue
            val lat = r["shape_pt_lat"]?.toDoubleOrNull() ?: continue
            val lng = r["shape_pt_lon"]?.toDoubleOrNull() ?: continue
            val seq = r["shape_pt_sequence"]?.toIntOrNull() ?: continue
            if (id != currentId) {
                flushShape()
                currentId = id
            }
            points.add(Pt(seq, lat, lng))
        }
        flushShape()
        if (batch.isNotEmpty()) {
            dao.insertShapes(batch.toList())
            rows += batch.size
            onProgress(Progress("shapes", rows))
        }
        return rows
    }
}
