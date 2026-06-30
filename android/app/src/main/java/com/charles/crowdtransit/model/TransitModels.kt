package com.charles.crowdtransit.model

data class Stop(
    val stopId: String = "",
    val agencyId: String = "",
    val name: String = "",
    val desc: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val code: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val transitTypes: List<String> = emptyList(),
    val routeIds: Map<String, Boolean> = emptyMap(),
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val commentCount: Long = 0L,
    val features: StopFeatures = StopFeatures(),
    val crowdsourced: Boolean = false,
    val verified: Boolean = false,
    val addedBy: String? = null,
    val addedAt: Long = 0L,
    val lastUpdated: Long = 0L,
    val active: Boolean = true
) {
    val averageRating: Float get() = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else 0f
}

data class StopFeatures(
    val shelter: Boolean = false,
    val bench: Boolean = false,
    val lighting: Boolean = false,
    val elevator: Boolean = false,
    val escalator: Boolean = false,
    val ticketMachine: Boolean = false,
    val bikeParking: Boolean = false,
    val parking: Boolean = false
)

data class Route(
    val routeId: String = "",
    val agencyId: String = "",
    val shortName: String = "",
    val longName: String = "",
    val type: String = "bus",
    val color: String = "#4CAF50",
    val textColor: String = "#FFFFFF",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val active: Boolean = true
) {
    val averageRating: Float get() = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else 0f
}

data class Agency(
    val agencyId: String = "",
    val name: String = "",
    val shortName: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val transitTypes: List<String> = emptyList(),
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val verified: Boolean = false,
    val active: Boolean = true
)

data class Comment(
    val commentId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val avatarInitials: String = "",
    val targetType: String = "stop",
    val targetId: String = "",
    val text: String = "",
    val transitType: String = "",
    val routeId: String = "",
    val rating: Int = 0,
    val helpfulCount: Long = 0L,
    val flagged: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class Rating(
    val userId: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val targetType: String = "stop",
    val targetId: String = "",
    val overall: Int = 0,
    val subcategories: Map<String, Int> = emptyMap(),
    val transitType: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val avatarInitials: String = "",
    val joinedAt: Long = 0L,
    val stats: UserStats = UserStats(),
    val badges: Map<String, Boolean> = emptyMap(),
    val lastActiveAt: Long = 0L
)

data class UserStats(
    val reviewCount: Long = 0L,
    val stopsAdded: Long = 0L,
    val helpfulVotes: Long = 0L,
    val reportCount: Long = 0L
)

data class Report(
    val reportId: String = "",
    val userId: String = "",
    val isAnonymous: Boolean = false,
    val stopId: String = "",
    val routeId: String = "",
    val agencyId: String = "",
    val type: String = "delay",
    val severity: String = "moderate",
    val title: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val confirmCount: Long = 0L,
    val resolved: Boolean = false,
    val active: Boolean = true
)
