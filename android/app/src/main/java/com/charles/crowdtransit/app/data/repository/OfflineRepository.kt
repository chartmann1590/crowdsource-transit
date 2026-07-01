package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.local.dao.OfflineDao
import com.charles.crowdtransit.app.data.local.entities.CachedAgencyEntity
import com.charles.crowdtransit.app.data.local.entities.CachedStopEntity
import com.charles.crowdtransit.app.data.remote.TransitlandApi
import com.charles.crowdtransit.app.data.remote.TransitlandOperator
import com.charles.crowdtransit.model.Stop
import kotlinx.coroutines.flow.Flow
import java.lang.Math.toRadians
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val MAX_DOWNLOAD_PAGES = 50

@Singleton
class OfflineRepository @Inject constructor(
    private val transitlandApi: TransitlandApi,
    private val offlineDao: OfflineDao,
) {

    fun observeDownloadedAgencies(): Flow<List<CachedAgencyEntity>> = offlineDao.observeAgencies()

    suspend fun searchOperators(query: String): List<TransitlandOperator> =
        transitlandApi.searchOperators(query).operators

    suspend fun downloadAgency(onestopId: String, name: String, onProgress: (Int) -> Unit = {}) {
        offlineDao.deleteStopsForAgency(onestopId)
        var after: Long? = null
        var total = 0
        var pages = 0
        do {
            val response = transitlandApi.getStopsByAgency(onestopId, limit = 100, after = after)
            val entities = response.stops.mapNotNull { remote ->
                val stopId = remote.onestopId ?: return@mapNotNull null
                val coords = remote.geometry?.coordinates.orEmpty()
                CachedStopEntity(
                    stopId = stopId,
                    name = remote.stopName ?: stopId,
                    lat = coords.getOrNull(1) ?: 0.0,
                    lng = coords.getOrNull(0) ?: 0.0,
                    state = remote.place?.stateName ?: "",
                    country = remote.place?.countryName ?: "",
                    agencyOnestopId = onestopId,
                )
            }
            if (entities.isNotEmpty()) offlineDao.insertStops(entities)
            total += entities.size
            onProgress(total)
            pages++
            after = response.meta?.after.takeIf { response.stops.isNotEmpty() && pages < MAX_DOWNLOAD_PAGES }
        } while (after != null)

        offlineDao.insertAgency(
            CachedAgencyEntity(
                onestopId = onestopId,
                name = name,
                stopCount = total,
                downloadedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeAgency(onestopId: String) {
        offlineDao.deleteStopsForAgency(onestopId)
        offlineDao.deleteAgency(onestopId)
    }

    suspend fun getCachedStopsNearby(lat: Double, lng: Double, radiusKm: Double): List<Stop> {
        val all = offlineDao.getAllStops()
        return all
            .filter { haversineKm(lat, lng, it.lat, it.lng) <= radiusKm }
            .map { it.toStop() }
    }

    suspend fun searchCachedStops(query: String): List<Stop> =
        offlineDao.searchStops(query).map { it.toStop() }

    suspend fun getCachedStop(stopId: String): Stop? = offlineDao.getStop(stopId)?.toStop()

    private fun CachedStopEntity.toStop(): Stop = Stop(
        stopId = stopId,
        name = name,
        lat = lat,
        lng = lng,
        state = state,
        country = country,
    )

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = toRadians(lat2 - lat1)
        val dLng = toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
