package com.charles.crowdtransit.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val useImperialUnits: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_IMPERIAL] ?: false
    }

    suspend fun setUseImperialUnits(use: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_IMPERIAL] = use
        }
    }

    companion object {
        private val KEY_USE_IMPERIAL = booleanPreferencesKey("use_imperial_units")
    }
}
