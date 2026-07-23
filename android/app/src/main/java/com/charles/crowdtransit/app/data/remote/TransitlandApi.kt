package com.charles.crowdtransit.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TransitlandApi {

    @GET("api/v2/rest/stops")
    suspend fun getStopsNearby(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radiusMeters: Int,
        @Query("limit") limit: Int = 100,
    ): TransitlandStopsResponse

    @GET("api/v2/rest/stops")
    suspend fun searchStops(
        @Query("search") search: String,
        @Query("limit") limit: Int = 20,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("radius") radiusMeters: Int? = null,
    ): TransitlandStopsResponse

    @GET("api/v2/rest/stops")
    suspend fun getStopByOnestopId(
        @Query("onestop_id") onestopId: String,
    ): TransitlandStopsResponse

    @GET("api/v2/rest/routes")
    suspend fun getRoutesNear(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radiusMeters: Int = 80,
        @Query("limit") limit: Int = 10,
    ): TransitlandRoutesResponse

    @GET("api/v2/rest/operators")
    suspend fun searchOperators(
        @Query("search") search: String,
        @Query("limit") limit: Int = 10,
    ): TransitlandOperatorsResponse

    // Operator detail incl. its source feeds (for offline GTFS download resolution).
    @GET("api/v2/rest/operators/{onestop_id}")
    suspend fun getOperator(
        @Path("onestop_id") onestopId: String,
    ): TransitlandOperatorsResponse

    @GET("api/v2/rest/stops")
    suspend fun getStopsByAgency(
        @Query("served_by_onestop_ids") agencyOnestopId: String,
        @Query("limit") limit: Int = 100,
        @Query("after") after: Long? = null,
    ): TransitlandStopsResponse

    @GET("api/v2/rest/routes/{route_key}")
    suspend fun getRouteWithStops(
        @Path("route_key") routeKey: String,
    ): TransitlandFullRoutesResponse

    @GET("api/v2/rest/stops/{stop_key}/departures")
    suspend fun getStopDepartures(
        @Path("stop_key") stopKey: String,
        @Query("relative_date") relativeDate: String = "TODAY",
        @Query("next") nextSeconds: Int = 86400,
        @Query("limit") limit: Int = 200,
        @Query("include_alerts") includeAlerts: String = "false",
    ): TransitlandDeparturesResponse

    // Trip detail: full ordered stop_times + shape. The path key is Transitland's
    // internal INTEGER trip id (trip.id from departures), not the GTFS trip_id.
    @GET("api/v2/rest/routes/{route_key}/trips/{trip_id}")
    suspend fun getTrip(
        @Path("route_key") routeKey: String,
        @Path("trip_id") tripIntId: Long,
        @Query("include_geometry") includeGeometry: Boolean = true,
    ): TransitlandTripDetailResponse
}
