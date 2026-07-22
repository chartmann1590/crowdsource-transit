package com.charles.crowdtransit.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies MIGRATION_1_2 (Phase F offline-GTFS tables) applies cleanly to a v1 database
 * that already has real rows, and that the resulting schema matches what Room expects
 * (validateMigration checks column/index definitions against the current entities).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesExistingDataAndAddsGtfsTables() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO cached_stops (stopId, name, lat, lng, state, country, agencyOnestopId) " +
                    "VALUES ('s-1', 'Test Stop', 40.7, -74.0, 'NY', 'US', 'o-test')",
            )
            execSQL(
                "INSERT INTO cached_agencies (onestopId, name, stopCount, downloadedAt) " +
                    "VALUES ('o-test', 'Test Agency', 1, 1690000000000)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        val stopCursor = migrated.query("SELECT COUNT(*) FROM cached_stops")
        stopCursor.moveToFirst()
        assert(stopCursor.getInt(0) == 1) { "existing cached_stops row must survive the migration" }
        stopCursor.close()

        for (table in listOf(
            "gtfs_stops", "gtfs_routes", "gtfs_trips", "gtfs_stop_times",
            "gtfs_calendars", "gtfs_calendar_dates", "gtfs_shapes",
        )) {
            val cursor = migrated.query("SELECT COUNT(*) FROM $table")
            cursor.moveToFirst()
            assert(cursor.getInt(0) == 0) { "$table should exist and be empty after migration" }
            cursor.close()
        }
    }
}
