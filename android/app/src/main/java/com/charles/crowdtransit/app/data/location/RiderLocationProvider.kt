package com.charles.crowdtransit.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A one-shot "roughly where is the rider right now" lookup, independent of
 * NavigationSessionRepository — that repository's fix is only populated by
 * NavigationService while a trip is actively being turn-by-turn navigated, and is null
 * (explicitly cleared in stop()) the rest of the time, including exactly when Hopper is
 * asked to plan a *new* trip ("from here"). Backed by Play Services' cached last-known
 * location, so calling this doesn't start a location subscription or force a fresh GPS
 * fix — cheap enough to call on every tool invocation.
 */
@Singleton
class RiderLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission")
    suspend fun lastFix(): Pair<Double, Double>? {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null
        return try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                ?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            null
        }
    }
}
