package com.charles.crowdtransit.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * On-device GTFS for offline trip planning (Phase F). All ids are feed-scoped as
 * "{agencyOnestopId}|{gtfsId}" so multiple downloaded agencies coexist. Rows are
 * written by GtfsImporter and read by RoomGtfsDataSource. No Room foreign keys —
 * the importer streams tables in whatever order the zip provides.
 */

@Entity(
    tableName = "gtfs_stops",
    primaryKeys = ["stopId"],
    indices = [Index("agencyOnestopId"), Index("lat"), Index("lng")],
)
data class GtfsStopEntity(
    val stopId: String,
    val agencyOnestopId: String,
    val gtfsStopId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

@Entity(
    tableName = "gtfs_routes",
    primaryKeys = ["routeId"],
    indices = [Index("agencyOnestopId")],
)
data class GtfsRouteEntity(
    val routeId: String,
    val agencyOnestopId: String,
    val gtfsRouteId: String,
    val shortName: String,
    val longName: String,
    val routeType: Int,
    val color: String,
)

@Entity(
    tableName = "gtfs_trips",
    primaryKeys = ["tripId"],
    indices = [Index("routeId"), Index("agencyOnestopId")],
)
data class GtfsTripEntity(
    val tripId: String,
    val agencyOnestopId: String,
    val routeId: String,
    val serviceId: String,
    val headsign: String,
    val shapeId: String,
    /** Highest stop_sequence of this trip (precomputed so departure queries can skip last stops). */
    val lastSeq: Int,
)

@Entity(
    tableName = "gtfs_stop_times",
    primaryKeys = ["tripId", "seq"],
    indices = [Index("stopId", "depSec")],
)
data class GtfsStopTimeEntity(
    val tripId: String,
    val seq: Int,
    val stopId: String,
    /** Seconds since service-day midnight; may exceed 86400. */
    val arrSec: Int,
    val depSec: Int,
)

@Entity(
    tableName = "gtfs_calendars",
    primaryKeys = ["serviceId"],
    indices = [Index("agencyOnestopId")],
)
data class GtfsCalendarEntity(
    val serviceId: String,
    val agencyOnestopId: String,
    /** Bit 0 = Monday … bit 6 = Sunday (java.time DayOfWeek ordinal). */
    val daysMask: Int,
    /** yyyyMMdd ints. */
    val startDate: Int,
    val endDate: Int,
)

@Entity(
    tableName = "gtfs_calendar_dates",
    primaryKeys = ["serviceId", "date"],
    indices = [Index("agencyOnestopId")],
)
data class GtfsCalendarDateEntity(
    val serviceId: String,
    val agencyOnestopId: String,
    /** yyyyMMdd. */
    val date: Int,
    /** 1 = service added, 2 = service removed. */
    val exceptionType: Int,
)

@Entity(
    tableName = "gtfs_shapes",
    primaryKeys = ["shapeId"],
    indices = [Index("agencyOnestopId")],
)
data class GtfsShapeEntity(
    val shapeId: String,
    val agencyOnestopId: String,
    /** Encoded polyline precision 5 — one row per shape, not per point. */
    val encodedPolyline: String,
)
