package com.charles.crowdtransit.app.util

import kotlin.math.roundToInt

/**
 * Shared distance formatter honouring the user's units preference
 * ([com.charles.crowdtransit.app.data.preferences.UserPreferencesStore.useImperialUnits]).
 * Imperial: feet under a mile, else miles. Metric: metres under a kilometre, else km.
 */
object DistanceFormat {
    fun format(meters: Double, useImperial: Boolean): String {
        if (useImperial) {
            val feet = meters * 3.28084
            return if (feet < 5280.0) {
                "${feet.roundToInt()} ft"
            } else {
                String.format("%.1f mi", feet / 5280.0)
            }
        }
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format("%.1f km", meters / 1000.0)
        }
    }

    fun format(meters: Int, useImperial: Boolean): String = format(meters.toDouble(), useImperial)
}
