package com.charles.crowdtransit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.charles.crowdtransit.app.data.local.dao.OfflineDao
import com.charles.crowdtransit.app.data.local.entities.CachedAgencyEntity
import com.charles.crowdtransit.app.data.local.entities.CachedStopEntity

@Database(
    entities = [CachedStopEntity::class, CachedAgencyEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao
}
