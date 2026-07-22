package com.charles.crowdtransit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.charles.crowdtransit.app.data.trip.TripPlanCodec
import com.charles.crowdtransit.app.data.trip.TripSessionHolder
import com.charles.crowdtransit.app.navigation.CrowdTransitNavGraph
import com.charles.crowdtransit.app.ui.theme.CrowdTransitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tripSession: TripSessionHolder

    @Inject lateinit var tripPlanCodec: TripPlanCodec

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        setContent {
            CrowdTransitTheme {
                CrowdTransitNavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Shared-trip deep links: crowdtransit://trip?d=<blob> and
     * https://…/crowdsource-transit/trip#d=<blob> (payload in query or fragment).
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri: Uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        val blob = uri.getQueryParameter("d")
            ?: uri.fragment?.substringAfter("d=", "")?.takeIf { it.isNotEmpty() }
            ?: return
        val plan = runCatching { tripPlanCodec.decode(blob) }.getOrNull() ?: return
        tripSession.openSharedPlan(plan)
    }
}
