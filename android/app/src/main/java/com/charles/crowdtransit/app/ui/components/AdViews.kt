package com.charles.crowdtransit.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.crowdtransit.app.BuildConfig
import com.charles.crowdtransit.app.data.billing.billingRepository
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Adaptive banner ad, shown inline in scrollable/persistent UI (e.g. the nearby-stops
 * sheet). Renders nothing for riders with an active "Remove Ads" subscription — see
 * [com.charles.crowdtransit.app.data.billing.BillingRepository].
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isSubscribed by context.billingRepository().isSubscribed.collectAsState()
    if (isSubscribed) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
