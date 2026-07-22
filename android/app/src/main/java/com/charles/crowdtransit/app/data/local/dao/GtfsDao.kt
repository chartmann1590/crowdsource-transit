package com.charles.crowdtransit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarDateEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsRouteEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsShapeEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsStopEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsStopTimeEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsTripEntity

@Dao
interface GtfsDao {

    // === Import ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(rows: List<GtfsStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(rows: List<GtfsRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(rows: List<GtfsTripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(rows: List<GtfsStopTimeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(rows: List<GtfsCalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarDates(rows: List<GtfsCalendarDateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShapes(rows: List<GtfsShapeEntity>)

    @Query("UPDATE gtfs_trips SET lastSeq = (SELECT MAX(seq) FROM gtfs_stop_times WHERE gtfs_stop_times.tripId = gtfs_trips.tripId) WHERE agencyOnestopId = :agency")
    suspend fun computeTripLastSeqs(agency: String)

    // === Delete (per agency) ===

    @Query("DELETE FROM gtfs_stops WHERE agencyOnestopId = :agency")
    suspend fun deleteStops(agency: String)

    @Query("DELETE FROM gtfs_routes WHERE agencyOnestopId = :agency")
    suspend fun deleteRoutes(agency: String)

    @Query("DELETE FROM gtfs_stop_times WHERE tripId IN (SELECT tripId FROM gtfs_trips WHERE agencyOnestopId = :agency)")
    suspend fun deleteStopTimes(agency: String)

    @Query("DELETE FROM gtfs_trips WHERE agencyOnestopId = :agency")
    suspend fun deleteTrips(agency: String)

    @Query("DELETE FROM gtfs_calendars WHERE agencyOnestopId = :agency")
    suspend fun deleteCalendars(agency: String)

    @Query("DELETE FROM gtfs_calendar_dates WHERE agencyOnestopId = :agency")
    suspend fun deleteCalendarDates(agency: String)

    @Query("DELETE FROM gtfs_shapes WHERE agencyOnestopId = :agency")
    suspend fun deleteShapes(agency: String)

    // === Routing queries (RoomGtfsDataSource) ===

    @Query(
        "SELECT * FROM gtfs_stops WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng LIMIT :limit",
    )
    suspend fun stopsInBox(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double, limit: Int): List<GtfsStopEntity>

    data class DepartureRow(
        val tripId: String,
        val seq: Int,
        val depSec: Int,
        val serviceId: String,
        val headsign: String,
        val routeId: String,
        val shortName: String,
        val longName: String,
        val routeType: Int,
        val color: String,
    )

    @Query(
        """
        SELECT st.tripId AS tripId, st.seq AS seq, st.depSec AS depSec,
               t.serviceId AS serviceId, t.headsign AS headsign,
               r.routeId AS routeId, r.shortName AS shortName, r.longName AS longName,
               r.routeType AS routeType, r.color AS color
        FROM gtfs_stop_times st
        JOIN gtfs_trips t ON t.tripId = st.tripId
        JOIN gtfs_routes r ON r.routeId = t.routeId
        WHERE st.stopId = :stopId AND st.depSec BETWEEN :minDepSec AND :maxDepSec AND st.seq < t.lastSeq
        ORDER BY st.depSec
        LIMIT 300
        """,
    )
    suspend fun departuresAtStop(stopId: String, minDepSec: Int, maxDepSec: Int): List<DepartureRow>

    @Query("SELECT * FROM gtfs_stop_times WHERE tripId = :tripId ORDER BY seq")
    suspend fun stopTimesForTrip(tripId: String): List<GtfsStopTimeEntity>

    @Query("SELECT * FROM gtfs_trips WHERE tripId = :tripId LIMIT 1")
    suspend fun trip(tripId: String): GtfsTripEntity?

    @Query("SELECT * FROM gtfs_stops WHERE stopId IN (:stopIds)")
    suspend fun stopsByIds(stopIds: List<String>): List<GtfsStopEntity>

    @Query("SELECT * FROM gtfs_shapes WHERE shapeId = :shapeId LIMIT 1")
    suspend fun shape(shapeId: String): GtfsShapeEntity?

    @Query("SELECT * FROM gtfs_calendars WHERE agencyOnestopId IN (:agencies)")
    suspend fun calendars(agencies: List<String>): List<GtfsCalendarEntity>

    @Query("SELECT * FROM gtfs_calendar_dates WHERE agencyOnestopId IN (:agencies) AND date = :date")
    suspend fun calendarDates(agencies: List<String>, date: Int): List<GtfsCalendarDateEntity>

    @Query("SELECT COUNT(*) FROM gtfs_stop_times WHERE tripId IN (SELECT tripId FROM gtfs_trips WHERE agencyOnestopId = :agency)")
    suspend fun stopTimeCount(agency: String): Long

    @Query("SELECT DISTINCT agencyOnestopId FROM gtfs_trips")
    suspend fun agenciesWithSchedules(): List<String>
}
