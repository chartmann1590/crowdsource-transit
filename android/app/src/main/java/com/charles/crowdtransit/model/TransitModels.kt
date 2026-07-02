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
    val reportCount: Long = 0L,
    // Gamification (see /users/{uid}/stats)
    val points: Long = 0L,
    val photoCount: Long = 0L,
    val checkinCount: Long = 0L,
    val streakCount: Long = 0L,
    val lastContributionDate: String = "", // yyyy-MM-dd, local time
)

/** Points a contribution type is worth. Awarded client-side right after a successful write. */
enum class PointAction(val points: Int) {
    NEW_STOP(10),
    REVIEW(5),
    PHOTO(3),
    CHECK_IN(2),
}

enum class Level(val minPoints: Long, val displayName: String) {
    PEDESTRIAN(0, "Pedestrian"),
    COMMUTER(50, "Commuter"),
    REGULAR(150, "Regular"),
    CONDUCTOR(400, "Conductor"),
    TRANSIT_LEGEND(1000, "Transit Legend");

    companion object {
        fun forPoints(points: Long): Level = entries.lastOrNull { points >= it.minPoints } ?: PEDESTRIAN
    }
}

object Badges {
    const val FIRST_REVIEW = "first_review"
    const val FIRST_STOP = "first_stop"
    const val TEN_STOPS = "ten_stops"
    const val TWENTY_FIVE_REVIEWS = "twenty_five_reviews"
    const val FIRST_PHOTO = "first_photo"
    const val SEVEN_DAY_STREAK = "seven_day_streak"

    val labels: Map<String, String> = mapOf(
        FIRST_REVIEW to "First Review",
        FIRST_STOP to "First Stop",
        TEN_STOPS to "10 Stops Added",
        TWENTY_FIVE_REVIEWS to "25 Reviews",
        FIRST_PHOTO to "First Photo",
        SEVEN_DAY_STREAK to "7-Day Streak",
    )
}

/** /leaderboard/{uid} */
data class LeaderboardEntry(
    val displayName: String = "",
    val points: Long = 0L,
)

/** /activity/{stopId}/{pushId}. Live for 90 minutes, filtered client-side by timestamp. */
data class ActivityEvent(
    val activityId: String = "",
    val uid: String = "",
    val type: String = "checkin", // checkin|on_time|late|crowded|empty|not_running
    val timestamp: Long = 0L,
)

object ActivityType {
    const val CHECKIN = "checkin"
    const val ON_TIME = "on_time"
    const val LATE = "late"
    const val CROWDED = "crowded"
    const val EMPTY = "empty"
    const val NOT_RUNNING = "not_running"

    val quickReportTypes = listOf(ON_TIME, LATE, CROWDED, EMPTY, NOT_RUNNING)

    fun label(type: String): String = when (type) {
        CHECKIN -> "Checked in"
        ON_TIME -> "On time"
        LATE -> "Late"
        CROWDED -> "Crowded"
        EMPTY -> "Empty"
        NOT_RUNNING -> "Not running"
        else -> type
    }
}

/** /photos/{stopId}/{pushId} */
data class StopPhoto(
    val photoId: String = "",
    val uid: String = "",
    val data: String = "", // base64 JPEG, compressed to max 800px / ~100KB client-side
    val timestamp: Long = 0L,
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
