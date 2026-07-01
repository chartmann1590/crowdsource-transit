package com.charles.crowdtransit.app.data.remote

import com.charles.crowdtransit.model.Stop

fun gtfsRouteTypeToTransitType(routeType: Int?): String = when (routeType) {
    0 -> "tram"
    1 -> "subway"
    2 -> "train"
    3 -> "bus"
    4 -> "ferry"
    else -> "transit"
}

fun TransitlandStop.toStop(ratingSum: Long = 0L, ratingCount: Long = 0L, commentCount: Long = 0L): Stop {
    val coordinates = geometry?.coordinates.orEmpty()
    val lng = coordinates.getOrNull(0) ?: 0.0
    val lat = coordinates.getOrNull(1) ?: 0.0
    return Stop(
        stopId = onestopId ?: "",
        name = stopName ?: onestopId ?: "",
        desc = stopDesc ?: "",
        lat = lat,
        lng = lng,
        code = stopId ?: "",
        country = place?.countryName ?: "",
        state = place?.stateName ?: "",
        city = "",
        transitTypes = emptyList(),
        ratingSum = ratingSum,
        ratingCount = ratingCount,
        commentCount = commentCount,
        crowdsourced = false,
        verified = true,
        active = true,
    )
}
