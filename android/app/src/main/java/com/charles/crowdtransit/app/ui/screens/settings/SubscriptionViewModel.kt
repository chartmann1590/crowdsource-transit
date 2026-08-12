package com.charles.crowdtransit.app.ui.screens.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val isSubscribed: Boolean = false,
    /** Null until Play Console has the product configured — see BillingRepository. */
    val price: String? = null,
    val period: String? = null,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
) : ViewModel() {

    val uiState: StateFlow<SubscriptionUiState> = combine(
        billingRepository.isSubscribed,
        billingRepository.productDetails,
    ) { subscribed, details ->
        val offer = details?.subscriptionOfferDetails?.firstOrNull()
        val phase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
        SubscriptionUiState(
            isSubscribed = subscribed,
            price = phase?.formattedPrice,
            period = phase?.billingPeriod?.let { describeBillingPeriod(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionUiState())

    init {
        viewModelScope.launch { billingRepository.refreshPurchases() }
    }

    fun subscribe(activity: Activity) {
        billingRepository.launchPurchaseFlow(activity)
    }

    fun restorePurchases() {
        viewModelScope.launch { billingRepository.refreshPurchases() }
    }
}

/** ISO 8601 durations Play uses for billing periods — only the ones any subscription plan would realistically use. */
private fun describeBillingPeriod(iso: String): String = when (iso) {
    "P1W" -> "week"
    "P1M" -> "month"
    "P3M" -> "3 months"
    "P6M" -> "6 months"
    "P1Y" -> "year"
    else -> iso
}
