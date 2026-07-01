package com.charles.crowdtransit.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface TransitlandApi {

    @GET("api/v2/rest/stops")
    suspend fun getStopsNearby(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radiusMeters: Int,
        @Query("limit") limit: Int = 20,
    ): TransitlandStopsResponse

    @GET("api/v2/rest/stops")
    suspend fun searchStops(
        @Query("search") search: String,
        @Query("limit") limit: Int = 20,
    ): TransitlandStopsResponse

    @GET("api/v2/rest/stops")
    suspend fun getStopByOnestopId(
        @Query("onestop_id") onestopId: String,
    ): TransitlandStopsResponse
}
