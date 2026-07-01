package com.charles.crowdtransit.app.data.firebase

import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GeoFireHelper @Inject constructor(
    private val db: FirebaseDatabase,
) {
    private val geoFire by lazy {
        GeoFire(db.reference.child("geofire").child("stops"))
    }

    suspend fun queryRadius(lat: Double, lng: Double, radiusKm: Double): List<String> =
        suspendCancellableCoroutine { continuation ->
            val results = mutableListOf<String>()
            val query = geoFire.queryAtLocation(GeoLocation(lat, lng), radiusKm)

            val listener = object : GeoQueryEventListener {
                override fun onKeyEntered(key: String, location: GeoLocation) {
                    results.add(key)
                }

                override fun onKeyExited(key: String) {}

                override fun onKeyMoved(key: String, location: GeoLocation) {}

                override fun onGeoQueryReady() {
                    continuation.resume(results.toList())
                }

                override fun onGeoQueryError(error: DatabaseError) {
                    if (!continuation.isCompleted) {
                        continuation.resume(emptyList())
                    }
                }
            }

            query.addGeoQueryEventListener(listener)
            continuation.invokeOnCancellation { query.removeAllListeners() }
        }
}
