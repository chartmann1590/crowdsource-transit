package com.charles.crowdtransit.app.data.remote

import com.charles.crowdtransit.model.Stop

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
