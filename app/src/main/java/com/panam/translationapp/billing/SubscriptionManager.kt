package com.panam.translationapp.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.subscriptionDataStore: DataStore<Preferences> by preferencesDataStore(name = "subscription_prefs")

class SubscriptionManager(private val context: Context) {

    companion object {
        private val FIRST_INSTALL_TIME = longPreferencesKey("first_install_time")
        private val HAS_ACTIVE_SUBSCRIPTION = booleanPreferencesKey("has_active_subscription")
        private const val TRIAL_PERIOD_DAYS = 7L
    }

    private val dataStore = context.subscriptionDataStore

    // Initialize first install time if not set
    suspend fun initializeFirstInstall() {
        val firstInstallTime = dataStore.data.map { preferences ->
            preferences[FIRST_INSTALL_TIME]
        }.first()

        if (firstInstallTime == null) {
            dataStore.edit { preferences ->
                preferences[FIRST_INSTALL_TIME] = System.currentTimeMillis()
            }
        }
    }

    // Check if user is in trial period
    suspend fun isInTrialPeriod(): Boolean {
        val firstInstallTime = dataStore.data.map { preferences ->
            preferences[FIRST_INSTALL_TIME] ?: System.currentTimeMillis()
        }.first()

        val currentTime = System.currentTimeMillis()
        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(currentTime - firstInstallTime)

        return daysSinceInstall < TRIAL_PERIOD_DAYS
    }

    // Check if user has active subscription
    suspend fun hasActiveSubscription(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[HAS_ACTIVE_SUBSCRIPTION] ?: false
        }.first()
    }

    // Get days remaining in trial
    suspend fun getDaysRemainingInTrial(): Long {
        val firstInstallTime = dataStore.data.map { preferences ->
            preferences[FIRST_INSTALL_TIME] ?: System.currentTimeMillis()
        }.first()

        val currentTime = System.currentTimeMillis()
        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(currentTime - firstInstallTime)
        val daysRemaining = TRIAL_PERIOD_DAYS - daysSinceInstall

        return maxOf(0, daysRemaining)
    }

    // Check if user has access (trial or subscription)
    suspend fun hasAccess(): Boolean {
        return isInTrialPeriod() || hasActiveSubscription()
    }

    // Activate subscription
    suspend fun activateSubscription() {
        dataStore.edit { preferences ->
            preferences[HAS_ACTIVE_SUBSCRIPTION] = true
        }
    }

    // Deactivate subscription (for testing or if subscription expires)
    suspend fun deactivateSubscription() {
        dataStore.edit { preferences ->
            preferences[HAS_ACTIVE_SUBSCRIPTION] = false
        }
    }

    // Get subscription status flow for reactive UI updates
    fun getSubscriptionStatusFlow(): Flow<SubscriptionStatus> {
        return dataStore.data.map { preferences ->
            val firstInstallTime = preferences[FIRST_INSTALL_TIME] ?: System.currentTimeMillis()
            val hasSubscription = preferences[HAS_ACTIVE_SUBSCRIPTION] ?: false

            val currentTime = System.currentTimeMillis()
            val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(currentTime - firstInstallTime)
            val inTrial = daysSinceInstall < TRIAL_PERIOD_DAYS
            val daysRemaining = maxOf(0, TRIAL_PERIOD_DAYS - daysSinceInstall)

            when {
                hasSubscription -> SubscriptionStatus.Subscribed
                inTrial -> SubscriptionStatus.Trial(daysRemaining)
                else -> SubscriptionStatus.Expired
            }
        }
    }

    // Reset trial (for testing purposes only - remove in production)
    suspend fun resetTrial() {
        dataStore.edit { preferences ->
            preferences.clear()
            preferences[FIRST_INSTALL_TIME] = System.currentTimeMillis()
            preferences[HAS_ACTIVE_SUBSCRIPTION] = false
        }
    }
}

sealed class SubscriptionStatus {
    object Subscribed : SubscriptionStatus()
    data class Trial(val daysRemaining: Long) : SubscriptionStatus()
    object Expired : SubscriptionStatus()
}
