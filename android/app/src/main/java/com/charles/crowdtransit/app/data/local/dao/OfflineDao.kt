package com.charles.crowdtransit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.charles.crowdtransit.app.data.local.entities.CachedAgencyEntity
import com.charles.crowdtransit.app.data.local.entities.CachedStopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {

    @Query("SELECT * FROM cached_agencies ORDER BY downloadedAt DESC")
    fun observeAgencies(): Flow<List<CachedAgencyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgency(agency: CachedAgencyEntity)

    @Query("DELETE FROM cached_agencies WHERE onestopId = :onestopId")
    suspend fun deleteAgency(onestopId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<CachedStopEntity>)

    @Query("DELETE FROM cached_stops WHERE agencyOnestopId = :agencyOnestopId")
    suspend fun deleteStopsForAgency(agencyOnestopId: String)

    @Query("SELECT * FROM cached_stops")
    suspend fun getAllStops(): List<CachedStopEntity>

    @Query("SELECT * FROM cached_stops WHERE name LIKE '%' || :query || '%' LIMIT 20")
    suspend fun searchStops(query: String): List<CachedStopEntity>

    @Query("SELECT * FROM cached_stops WHERE stopId = :stopId LIMIT 1")
    suspend fun getStop(stopId: String): CachedStopEntity?
}
