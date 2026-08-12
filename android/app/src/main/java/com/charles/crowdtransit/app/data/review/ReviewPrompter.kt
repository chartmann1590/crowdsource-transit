package com.charles.crowdtransit.app.data.review

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "review_prompt_prefs")

/** Stop ratings submitted before we ever ask for a review. Early asks convert worse. */
private const val RATINGS_BEFORE_FIRST_ASK = 2

/**
 * Prompts the official Play In-App Review dialog after a rider has actually contributed a stop
 * rating — real community participation, not just app-open. Google's own quota caps how often
 * the dialog can appear regardless of what we request, so this only needs to avoid asking on the
 * very first rating and never ask twice.
 */
@Singleton
class ReviewPrompter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val RATING_COUNT = intPreferencesKey("review_prompt_rating_count")
        val REQUESTED = booleanPreferencesKey("review_prompt_requested")
    }

    suspend fun maybeRequestReview(activity: Activity) {
        var shouldRequest = false
        context.dataStore.edit { prefs ->
            val alreadyRequested = prefs[Keys.REQUESTED] ?: false
            val count = (prefs[Keys.RATING_COUNT] ?: 0) + 1
            prefs[Keys.RATING_COUNT] = count
            if (!alreadyRequested && count >= RATINGS_BEFORE_FIRST_ASK) {
                prefs[Keys.REQUESTED] = true
                shouldRequest = true
            }
        }
        if (!shouldRequest) return

        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, reviewInfo).await()
        }
    }
}
