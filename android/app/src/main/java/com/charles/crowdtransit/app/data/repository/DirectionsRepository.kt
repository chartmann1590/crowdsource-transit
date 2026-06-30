package com.charles.crowdtransit.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectionsRepository @Inject constructor() {

    fun openDirectionsToStop(context: Context, lat: Double, lng: Double, name: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=${Uri.encode(name)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    fun getWalkingDirectionsUri(destLat: Double, destLng: Double): Uri {
        return Uri.parse("google.navigation:q=$destLat,$destLng&mode=w")
    }
}
