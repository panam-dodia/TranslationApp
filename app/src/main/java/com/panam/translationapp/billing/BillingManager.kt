package com.panam.translationapp.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingManager(
    private val context: Context,
    private val subscriptionManager: SubscriptionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "BillingManager"

    // Product ID for your subscription (you'll need to create this in Google Play Console)
    companion object {
        const val SUBSCRIPTION_PRODUCT_ID = "translation_app_monthly"
    }

    private var billingClient: BillingClient? = null

    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private val _purchaseCompleted = MutableStateFlow(false)
    val purchaseCompleted: StateFlow<Boolean> = _purchaseCompleted.asStateFlow()

    sealed class SubscriptionState {
        object Loading : SubscriptionState()
        object Active : SubscriptionState()
        object Inactive : SubscriptionState()
        data class Error(val message: String) : SubscriptionState()
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    Log.d(TAG, "User canceled purchase")
                } else {
                    Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
                }
            }
            .enablePendingPurchases()
            .build()

        connectToBillingClient()
    }

    private fun connectToBillingClient() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    checkSubscriptionStatus()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _subscriptionState.value = SubscriptionState.Error("Billing setup failed")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing client disconnected")
                // Try to reconnect
                connectToBillingClient()
            }
        })
    }

    fun checkSubscriptionStatus() {
        billingClient?.let { client ->
            if (client.isReady) {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()

                client.queryPurchasesAsync(params) { billingResult, purchases ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val hasActiveSubscription = purchases.any { purchase ->
                            purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }

                        if (hasActiveSubscription) {
                            scope.launch {
                                subscriptionManager.activateSubscription()
                            }
                            _subscriptionState.value = SubscriptionState.Active
                        } else {
                            _subscriptionState.value = SubscriptionState.Inactive
                        }
                    } else {
                        Log.e(TAG, "Query purchases failed: ${billingResult.debugMessage}")
                        _subscriptionState.value = SubscriptionState.Inactive
                    }
                }
            }
        }
    }

    fun launchSubscriptionFlow(activity: Activity) {
        billingClient?.let { client ->
            if (client.isReady) {
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUBSCRIPTION_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )

                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                        val productDetails = productDetailsList[0]

                        // Get the offer token for the free trial offer
                        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                        if (offerToken != null) {
                            val productDetailsParamsList = listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(offerToken)
                                    .build()
                            )

                            val billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build()

                            client.launchBillingFlow(activity, billingFlowParams)
                        } else {
                            Log.e(TAG, "No offer token found")
                        }
                    } else {
                        Log.e(TAG, "Product details query failed: ${billingResult.debugMessage}")
                    }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge the purchase
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        scope.launch {
                            subscriptionManager.activateSubscription()
                        }
                        _subscriptionState.value = SubscriptionState.Active
                        _purchaseCompleted.value = true
                        Log.d(TAG, "Purchase acknowledged successfully")
                    }
                }
            } else {
                scope.launch {
                    subscriptionManager.activateSubscription()
                }
                _subscriptionState.value = SubscriptionState.Active
                _purchaseCompleted.value = true
            }
        }
    }

    fun endConnection() {
        billingClient?.endConnection()
    }
}
