package com.charles.crowdtransit.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Remove Ads" subscription via Play Billing — the sole SKU, deliberately: this app has
 * no other paid feature, so a single ad-free tier is the whole billing surface. Gates
 * [com.charles.crowdtransit.app.data.ads.InterstitialAdManager] and
 * [com.charles.crowdtransit.app.ui.components.BannerAdView].
 *
 * REQUIRES Play Console setup this repo can't do for you: create a subscription product
 * with product ID [REMOVE_ADS_PRODUCT_ID] under Monetize > Products > Subscriptions,
 * with at least one base plan + offer, before this can show real pricing or complete a
 * real purchase. Until that exists, [productDetails] stays null and the "Remove Ads"
 * settings row shows "Unavailable" rather than a broken buy button.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PurchasesUpdatedListener {

    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads_monthly"
        private const val TAG = "BillingRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True once a real, acknowledged, active subscription purchase is on record. */
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    /** Null until Play Console has the product configured and the query succeeds. */
    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        loadProductDetails()
                        refreshPurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                }
            }

            // No auto-reconnect loop: the next explicit user action (opening the
            // subscription screen, launching the purchase flow) retries via connect().
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private suspend fun loadProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
        _productDetails.value = result.productDetailsList?.firstOrNull()
    }

    /** Re-checks entitlement — call on app start and whenever the subscription screen opens. */
    suspend fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val result = billingClient.queryPurchasesAsync(params)
        val purchases = result.purchasesList
        purchases.forEach { acknowledgeIfNeeded(it) }
        _isSubscribed.value = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    /** No-ops with a log warning if [productDetails] hasn't loaded (Play Console not configured yet). */
    fun launchPurchaseFlow(activity: Activity) {
        val details = _productDetails.value
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (details == null || offerToken == null) {
            Log.w(TAG, "launchPurchaseFlow called with no product/offer loaded yet")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                Log.w(TAG, "onPurchasesUpdated: ${result.responseCode} ${result.debugMessage}")
            }
            return
        }
        val list = purchases ?: return
        scope.launch {
            list.forEach { acknowledgeIfNeeded(it) }
            _isSubscribed.value = list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        }
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.acknowledgePurchase(params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledgePurchase failed: ${result.debugMessage}")
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillingRepositoryEntryPoint {
    fun billingRepository(): BillingRepository
}

fun Context.billingRepository(): BillingRepository =
    EntryPointAccessors.fromApplication(applicationContext, BillingRepositoryEntryPoint::class.java)
        .billingRepository()
