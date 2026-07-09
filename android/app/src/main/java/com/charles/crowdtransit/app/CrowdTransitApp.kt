package com.charles.crowdtransit.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

@HiltAndroidApp
class CrowdTransitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
        MobileAds.initialize(this)
    }
}
