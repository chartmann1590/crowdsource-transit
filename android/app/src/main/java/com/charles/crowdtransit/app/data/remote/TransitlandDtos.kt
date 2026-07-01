package com.charles.crowdtransit.app.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransitlandStopsResponse(
    val stops: List<TransitlandStop> = emptyList(),
    val meta: TransitlandMeta? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandMeta(
    val after: Long? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandStop(
    @Json(name = "onestop_id") val onestopId: String? = null,
    @Json(name = "stop_id") val stopId: String? = null,
    @Json(name = "stop_name") val stopName: String? = null,
    @Json(name = "stop_desc") val stopDesc: String? = null,
    val geometry: TransitlandGeometry? = null,
    val place: TransitlandPlace? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandGeometry(
    val type: String = "",
    val coordinates: List<Double> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandPlace(
    @Json(name = "adm0_name") val countryName: String? = null,
    @Json(name = "adm1_name") val stateName: String? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandRoutesResponse(
    val routes: List<TransitlandRoute> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandRoute(
    @Json(name = "route_type") val routeType: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TransitlandOperatorsResponse(
    val operators: List<TransitlandOperator> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransitlandOperator(
    @Json(name = "onestop_id") val onestopId: String = "",
    val name: String? = null,
    @Json(name = "short_name") val shortName: String? = null,
)
