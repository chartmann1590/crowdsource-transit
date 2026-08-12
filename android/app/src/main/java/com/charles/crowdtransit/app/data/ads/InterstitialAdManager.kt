package com.charles.crowdtransit.app.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.charles.crowdtransit.app.BuildConfig
import com.charles.crowdtransit.app.data.billing.BillingRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and shows interstitial ads, frequency-capped so they don't show on every
 * navigation. Call [maybeShow] at a natural transition point (e.g. opening a stop);
 * it shows an ad every [SHOW_EVERY_N] calls and silently no-ops otherwise, if the ad
 * hasn't finished loading yet, or if the rider has an active "Remove Ads" subscription
 * (see [BillingRepository]).
 */
@Singleton
class InterstitialAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingRepository: BillingRepository,
) {
    private companion object {
        const val SHOW_EVERY_N = 3
        const val TAG = "InterstitialAdManager"
    }

    private var interstitialAd: InterstitialAd? = null
    private val callCount = AtomicInteger(0)

    init {
        loadAd()
    }

    private fun loadAd() {
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                }
            },
        )
    }

    fun maybeShow(activity: Activity) {
        if (billingRepository.isSubscribed.value) return
        if (callCount.incrementAndGet() % SHOW_EVERY_N != 0) return
        val ad = interstitialAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadAd()
            }
        }
        ad.show(activity)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InterstitialAdManagerEntryPoint {
    fun interstitialAdManager(): InterstitialAdManager
}

fun Context.interstitialAdManager(): InterstitialAdManager =
    EntryPointAccessors.fromApplication(applicationContext, InterstitialAdManagerEntryPoint::class.java)
        .interstitialAdManager()
