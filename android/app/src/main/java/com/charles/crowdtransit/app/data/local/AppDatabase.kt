package com.charles.crowdtransit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.charles.crowdtransit.app.data.local.dao.GtfsDao
import com.charles.crowdtransit.app.data.local.dao.OfflineDao
import com.charles.crowdtransit.app.data.local.entities.CachedAgencyEntity
import com.charles.crowdtransit.app.data.local.entities.CachedStopEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarDateEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsCalendarEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsRouteEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsShapeEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsStopEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsStopTimeEntity
import com.charles.crowdtransit.app.data.local.entities.GtfsTripEntity

@Database(
    entities = [
        CachedStopEntity::class,
        CachedAgencyEntity::class,
        GtfsStopEntity::class,
        GtfsRouteEntity::class,
        GtfsTripEntity::class,
        GtfsStopTimeEntity::class,
        GtfsCalendarEntity::class,
        GtfsCalendarDateEntity::class,
        GtfsShapeEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    abstract fun gtfsDao(): GtfsDao

    companion object {
        /** v1→v2: purely additive — the new offline-GTFS tables (Phase F). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_stops` (`stopId` TEXT NOT NULL, `agencyOnestopId` TEXT NOT NULL, `gtfsStopId` TEXT NOT NULL, `name` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, PRIMARY KEY(`stopId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_stops_agencyOnestopId` ON `gtfs_stops` (`agencyOnestopId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_stops_lat` ON `gtfs_stops` (`lat`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_stops_lng` ON `gtfs_stops` (`lng`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_routes` (`routeId` TEXT NOT NULL, `agencyOnestopId` TEXT NOT NULL, `gtfsRouteId` TEXT NOT NULL, `shortName` TEXT NOT NULL, `longName` TEXT NOT NULL, `routeType` INTEGER NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`routeId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_routes_agencyOnestopId` ON `gtfs_routes` (`agencyOnestopId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_trips` (`tripId` TEXT NOT NULL, `agencyOnestopId` TEXT NOT NULL, `routeId` TEXT NOT NULL, `serviceId` TEXT NOT NULL, `headsign` TEXT NOT NULL, `shapeId` TEXT NOT NULL, `lastSeq` INTEGER NOT NULL, PRIMARY KEY(`tripId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_trips_routeId` ON `gtfs_trips` (`routeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_trips_agencyOnestopId` ON `gtfs_trips` (`agencyOnestopId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_stop_times` (`tripId` TEXT NOT NULL, `seq` INTEGER NOT NULL, `stopId` TEXT NOT NULL, `arrSec` INTEGER NOT NULL, `depSec` INTEGER NOT NULL, PRIMARY KEY(`tripId`, `seq`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_stop_times_stopId_depSec` ON `gtfs_stop_times` (`stopId`, `depSec`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_calendars` (`serviceId` TEXT NOT NULL, `agencyOnestopId` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, PRIMARY KEY(`serviceId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_calendars_agencyOnestopId` ON `gtfs_calendars` (`agencyOnestopId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_calendar_dates` (`serviceId` TEXT NOT NULL, `agencyOnestopId` TEXT NOT NULL, `date` INTEGER NOT NULL, `exceptionType` INTEGER NOT NULL, PRIMARY KEY(`serviceId`, `date`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_calendar_dates_agencyOnestopId` ON `gtfs_calendar_dates` (`agencyOnestopId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gtfs_shapes` (`shapeId` TEXT NOT NULL, `agencyOnestopId` TEXT NOT NULL, `encodedPolyline` TEXT NOT NULL, PRIMARY KEY(`shapeId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gtfs_shapes_agencyOnestopId` ON `gtfs_shapes` (`agencyOnestopId`)")
            }
        }
    }
}
