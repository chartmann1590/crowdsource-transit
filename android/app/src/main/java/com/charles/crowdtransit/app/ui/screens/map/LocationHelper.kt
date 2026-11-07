package com.charles.crowdtransit.app.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class UserLocation(val lat: Double, val lng: Double)

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

// Amazon Appstore (Fire) build: Fire tablets have no Google Play services, so the fused
// location provider is unavailable there; use the platform LocationManager (Wi-Fi/network,
// GPS when present) instead. The Play build keeps the fused provider unchanged.
fun locationFlow(context: Context): Flow<UserLocation> =
    if (com.charles.crowdtransit.app.BuildConfig.STORE == "amazon") platformLocationFlow(context) else fusedLocationFlow(context)

@SuppressLint("MissingPermission")
private fun platformLocationFlow(context: Context): Flow<UserLocation> = callbackFlow {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        .filter { it != android.location.LocationManager.PASSIVE_PROVIDER }
    providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { trySend(UserLocation(it.latitude, it.longitude)) }
    val listener = android.location.LocationListener { location ->
        trySend(UserLocation(location.latitude, location.longitude))
    }
    providers.forEach { p -> runCatching { lm.requestLocationUpdates(p, 5_000L, 0f, listener, context.mainLooper) } }
    awaitClose { runCatching { lm.removeUpdates(listener) } }
}

@SuppressLint("MissingPermission")
private fun fusedLocationFlow(context: Context): Flow<UserLocation> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)

    client.lastLocation.addOnSuccessListener { location ->
        location?.let { trySend(UserLocation(it.latitude, it.longitude)) }
    }

    val request = LocationRequest.Builder(5_000L)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMinUpdateIntervalMillis(2_000L)
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                trySend(UserLocation(location.latitude, location.longitude))
            }
        }
    }

    client.requestLocationUpdates(request, callback, context.mainLooper)
    awaitClose { client.removeLocationUpdates(callback) }
}
