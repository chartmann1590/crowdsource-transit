package com.charles.crowdtransit.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Default to imperial (miles/feet) — most of the user base is US transit riders. */
    val useImperialUnits: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_IMPERIAL] ?: true
    }

    suspend fun setUseImperialUnits(use: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_IMPERIAL] = use
        }
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAS_ONBOARDED] ?: false
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_ONBOARDED] = completed
        }
    }

    /** Max metres the trip planner will walk to reach a boarding/alighting stop. */
    val maxWalkToStopM: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAX_WALK_TO_STOP_M] ?: DEFAULT_MAX_WALK_TO_STOP_M
    }

    suspend fun setMaxWalkToStopM(meters: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAX_WALK_TO_STOP_M] = meters
        }
    }

    // --- Hopper AI assistant ---

    /** User has opted in to the assistant (does not imply the model is downloaded). */
    val assistantEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASSISTANT_ENABLED] ?: false
    }

    suspend fun setAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ASSISTANT_ENABLED] = enabled }
    }

    /** Which model variant is installed/selected: "text_only" or "multimodal", or null. */
    val assistantVariant: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASSISTANT_VARIANT]
    }

    suspend fun setAssistantVariant(variant: String?) {
        context.dataStore.edit { prefs ->
            if (variant == null) prefs.remove(KEY_ASSISTANT_VARIANT) else prefs[KEY_ASSISTANT_VARIANT] = variant
        }
    }

    /** Inference backend preference: "cpu" or "gpu". */
    val assistantBackend: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASSISTANT_BACKEND] ?: "cpu"
    }

    suspend fun setAssistantBackend(backend: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ASSISTANT_BACKEND] = backend }
    }

    val assistantOnboardingSeen: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASSISTANT_ONBOARDING_SEEN] ?: false
    }

    suspend fun setAssistantOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ASSISTANT_ONBOARDING_SEEN] = seen }
    }

    val assistantTermsAccepted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASSISTANT_TERMS_ACCEPTED] ?: false
    }

    suspend fun setAssistantTermsAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ASSISTANT_TERMS_ACCEPTED] = accepted }
    }

    companion object {
        const val DEFAULT_MAX_WALK_TO_STOP_M = 1600
        private val KEY_USE_IMPERIAL = booleanPreferencesKey("use_imperial_units")
        private val KEY_HAS_ONBOARDED = booleanPreferencesKey("has_completed_onboarding")
        private val KEY_MAX_WALK_TO_STOP_M = intPreferencesKey("max_walk_to_stop_m")
        private val KEY_ASSISTANT_ENABLED = booleanPreferencesKey("assistant_enabled")
        private val KEY_ASSISTANT_VARIANT = stringPreferencesKey("assistant_variant")
        private val KEY_ASSISTANT_BACKEND = stringPreferencesKey("assistant_backend")
        private val KEY_ASSISTANT_ONBOARDING_SEEN = booleanPreferencesKey("assistant_onboarding_seen")
        private val KEY_ASSISTANT_TERMS_ACCEPTED = booleanPreferencesKey("assistant_terms_accepted")
    }
}
