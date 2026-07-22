package com.charles.crowdtransit.app.data.gtfs

import com.charles.crowdtransit.app.BuildConfig
import com.charles.crowdtransit.app.data.local.dao.GtfsDao
import com.charles.crowdtransit.app.data.remote.TransitlandApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an agency's full GTFS (schedules + shapes) into Room for offline routing.
 * Runs in an app-scoped coroutine so it survives navigation (not process death — the
 * existing Downloads feature has the same tradeoff; WorkManager is future work).
 * Feed zip comes from Transitland's download_latest_feed_version (one API call).
 */
@Singleton
class GtfsDownloadManager @Inject constructor(
    private val api: TransitlandApi,
    private val importer: GtfsImporter,
    private val dao: GtfsDao,
) {
    sealed class State {
        object Idle : State()
        data class Running(val agencyOnestopId: String, val table: String, val rows: Long) : State()
        data class Failed(val agencyOnestopId: String, val message: String) : State()
        data class Done(val agencyOnestopId: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _agenciesWithSchedules = MutableStateFlow<Set<String>>(emptySet())
    val agenciesWithSchedules: StateFlow<Set<String>> = _agenciesWithSchedules.asStateFlow()

    // Plain long-timeout client for the feed zip; the shared app client's 15 s read
    // timeout is far too short for multi-hundred-MB feeds.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    fun refreshDownloadedAgencies() {
        scope.launch {
            runCatching { _agenciesWithSchedules.value = dao.agenciesWithSchedules().toSet() }
        }
    }

    fun downloadSchedules(agencyOnestopId: String) {
        if (_state.value is State.Running) return
        _state.value = State.Running(agencyOnestopId, "resolving feed", 0)
        scope.launch {
            try {
                val operator = api.getOperator(agencyOnestopId).operators.firstOrNull()
                    ?: error("agency not found")
                val feedKey = operator.feeds
                    ?.firstOrNull { it.spec.equals("gtfs", ignoreCase = true) }
                    ?.onestopId
                    ?: error("no GTFS feed listed for this agency")

                val url = "https://api.transit.land/api/v2/rest/feeds/$feedKey/download_latest_feed_version" +
                    "?apikey=${BuildConfig.TRANSITLAND_API_KEY}"
                val response = downloadClient.newCall(Request.Builder().url(url).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        error(
                            if (resp.code == 404) "this feed can't be redistributed by Transitland"
                            else "feed download failed (HTTP ${resp.code})",
                        )
                    }
                    val body = resp.body ?: error("empty feed download")
                    importer.import(agencyOnestopId, body.byteStream()) { progress ->
                        _state.value = State.Running(agencyOnestopId, progress.table, progress.rows)
                    }
                }
                refreshDownloadedAgencies()
                _state.value = State.Done(agencyOnestopId)
            } catch (e: Exception) {
                runCatching { importer.clearAgency(agencyOnestopId) }
                _state.value = State.Failed(agencyOnestopId, e.message ?: "download failed")
            }
        }
    }

    fun removeSchedules(agencyOnestopId: String) {
        scope.launch {
            runCatching { importer.clearAgency(agencyOnestopId) }
            refreshDownloadedAgencies()
        }
    }
}
