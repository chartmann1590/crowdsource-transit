package com.charles.crowdtransit.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_agencies")
data class CachedAgencyEntity(
    @PrimaryKey val onestopId: String,
    val name: String,
    val stopCount: Int,
    val downloadedAt: Long,
)
