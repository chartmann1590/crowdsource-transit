package com.charles.crowdtransit.app.data.feedback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.feedbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "feedback_bug_reports"
)

@Singleton
class BugReportRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val listType = Types.newParameterizedType(List::class.java, BugReport::class.java)
    private val adapter = moshi.adapter<List<BugReport>>(listType)

    private val key = stringPreferencesKey("bug_reports_list")

    val bugReports: Flow<List<BugReport>> = context.feedbackDataStore.data.map { prefs ->
        val json = prefs[key] ?: return@map emptyList<BugReport>()
        try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveBugReport(report: BugReport) {
        context.feedbackDataStore.edit { prefs ->
            val json = prefs[key] ?: "[]"
            val list = try {
                adapter.fromJson(json)?.toMutableList() ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            val existing = list.indexOfFirst { it.number == report.number }
            if (existing >= 0) {
                list[existing] = report
            } else {
                list.add(0, report)
            }
            prefs[key] = adapter.toJson(list)
        }
    }

    suspend fun updateBugReports(reports: List<BugReport>) {
        context.feedbackDataStore.edit { prefs ->
            prefs[key] = adapter.toJson(reports)
        }
    }

    suspend fun getBugReportsList(): List<BugReport> {
        val prefs = context.feedbackDataStore.data
        var result = emptyList<BugReport>()
        prefs.collect { data ->
            val json = data[key] ?: ""
            result = try {
                adapter.fromJson(json) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            return@collect
        }
        return result
    }
}
