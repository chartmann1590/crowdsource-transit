package com.charles.crowdtransit.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_stops")
data class CachedStopEntity(
    @PrimaryKey val stopId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val state: String,
    val country: String,
    val agencyOnestopId: String,
)
