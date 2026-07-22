package com.charles.crowdtransit.app.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Walking directions via the CrowdTransit ORS proxy Worker (workers/ors-proxy).
 * The Worker holds the OpenRouteService key; its URL is public and safe to hard-code
 * (see di/NetworkModule). Request/response mirror ORS foot-walking GeoJSON directions.
 */
interface OrsApi {
    @POST("walk")
    suspend fun walk(@Body body: OrsWalkRequest): OrsGeoJsonResponse
}

@JsonClass(generateAdapter = true)
data class OrsWalkRequest(
    /** [lng, lat] pairs in travel order (2–5 waypoints). */
    val coordinates: List<List<Double>>,
)

@JsonClass(generateAdapter = true)
data class OrsGeoJsonResponse(
    val features: List<OrsFeature> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OrsFeature(
    val geometry: OrsLineGeometry? = null,
    val properties: OrsProperties? = null,
)

@JsonClass(generateAdapter = true)
data class OrsLineGeometry(
    val type: String = "",
    val coordinates: List<List<Double>> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OrsProperties(
    val summary: OrsSummary? = null,
    val segments: List<OrsSegment> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OrsSummary(
    val distance: Double? = null,
    val duration: Double? = null,
)

@JsonClass(generateAdapter = true)
data class OrsSegment(
    val steps: List<OrsStep> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OrsStep(
    val instruction: String? = null,
    val distance: Double? = null,
)
