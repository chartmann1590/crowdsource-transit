package com.charles.crowdtransit.app.ai.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.charles.crowdtransit.app.ai.device.DeviceCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads a Hopper model file with byte-level progress, HTTP Range resume, and SHA-256
 * verification before it's considered installed. Follows GtfsDownloadManager's proven
 * shape (singleton, sealed State, StateFlow, app-scoped SupervisorJob scope, dedicated
 * long-timeout client) — but a 2+ GB model file needs the things that download doesn't:
 * a real destination file, resumability, and a checksum, since a corrupted 2.6 GB file
 * that half-works is the worst failure mode here.
 */
@Singleton
class AssistantModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AssistantModelStore,
) {
    sealed class State {
        data object Idle : State()
        data class Running(val info: AssistantModelCatalog.ModelInfo, val downloadedBytes: Long) : State()
        data class Verifying(val info: AssistantModelCatalog.ModelInfo) : State()
        data class Failed(val info: AssistantModelCatalog.ModelInfo, val message: String) : State()
        data class Done(val info: AssistantModelCatalog.ModelInfo) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Long read timeout: a multi-GB download over a slow connection needs minutes, not
    // the shared app client's 15 s. connectTimeout stays short — a dead host should fail
    // fast rather than hang.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Starts (or resumes) the download. Caller is responsible for the cellular-network
     * confirmation dialog — this function does not gate on [isOnUnmeteredNetwork] itself
     * so it can also be invoked after the user explicitly confirms a metered download.
     */
    fun download(info: AssistantModelCatalog.ModelInfo) {
        if (_state.value is State.Running || _state.value is State.Verifying) return
        scope.launch {
            try {
                downloadInternal(info)
            } catch (e: Exception) {
                _state.value = State.Failed(info, e.message ?: "download failed")
            }
        }
    }

    fun cancel() {
        // Cooperative: the loop below checks scope.isActive between chunks. Cancelling
        // the scope's children stops the in-flight request; the .part file is left in
        // place so a subsequent download() call resumes from where it stopped.
        scope.coroutineContext.cancelChildren()
        if (_state.value is State.Running) _state.value = State.Idle
    }

    private suspend fun downloadInternal(info: AssistantModelCatalog.ModelInfo) {
        if (store.isInstalled(info)) {
            _state.value = State.Done(info)
            return
        }
        if (!DeviceCapability.hasStorageFor(info.sizeBytes, store.freeStorageBytes())) {
            _state.value = State.Failed(info, "not enough free storage for this download")
            return
        }

        val partial = store.partialFile(info)
        val resumeFrom = if (partial.exists()) partial.length() else 0L
        if (resumeFrom >= info.sizeBytes) {
            // Already fully downloaded, just unverified — fall through to verification.
            verifyAndFinalize(info, partial)
            return
        }

        _state.value = State.Running(info, resumeFrom)

        val request = Request.Builder()
            .url(info.downloadUrl)
            .apply { if (resumeFrom > 0) addHeader("Range", "bytes=$resumeFrom-") }
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("download failed (HTTP ${response.code})")
            }
            // A 200 for a Range request means the server ignored it — restart clean
            // rather than silently corrupt the file by appending past the actual start.
            val serverResumed = response.code == 206
            val startOffset = if (serverResumed) resumeFrom else 0L
            val body = response.body ?: error("empty response body")

            var cancelled = false
            RandomAccessFile(partial, "rw").use { raf ->
                if (!serverResumed) raf.setLength(0)
                raf.seek(startOffset)
                var written = startOffset
                body.byteStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    // This coroutine's own job, not the shared scope's — cancel() only
                    // cancels children, so checking the scope's job would never trip.
                    while (coroutineContext[Job]?.isActive == true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        written += read
                        _state.value = State.Running(info, written)
                    }
                    cancelled = coroutineContext[Job]?.isActive != true
                }
            }
            // User-cancelled mid-stream: leave the .part file exactly as written so the
            // next download() call resumes from here. Do NOT verify/delete it — an
            // incomplete file would otherwise fail verification and be deleted, destroying
            // the resumable progress cancel() promises to keep.
            if (cancelled) return
        }

        verifyAndFinalize(info, partial)
    }

    private fun verifyAndFinalize(info: AssistantModelCatalog.ModelInfo, partial: File) {
        _state.value = State.Verifying(info)
        if (partial.length() != info.sizeBytes) {
            partial.delete()
            _state.value = State.Failed(info, "download incomplete — please retry")
            return
        }
        val digest = store.sha256(partial)
        if (!digest.equals(info.sha256, ignoreCase = true)) {
            partial.delete()
            _state.value = State.Failed(info, "downloaded file failed verification — please retry")
            return
        }
        val finalFile = store.finalFile(info)
        finalFile.delete()
        if (!partial.renameTo(finalFile)) {
            _state.value = State.Failed(info, "could not finalize the downloaded file")
            return
        }
        _state.value = State.Done(info)
    }
}
