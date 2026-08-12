package com.charles.crowdtransit.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.charles.crowdtransit.app.MainActivity
import com.charles.crowdtransit.app.R
import com.charles.crowdtransit.app.ai.model.AssistantModelCatalog
import com.charles.crowdtransit.app.ai.model.AssistantModelDownloader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service wrapping the Hopper model download (2-2.6 GB). Modelled directly on
 * NavigationService: same channel/notification shape, START/STOP actions, START_NOT_STICKY.
 * dataSync is the correct foregroundServiceType for a large one-shot file transfer.
 */
@AndroidEntryPoint
class AssistantDownloadService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "assistant_download"
        private const val NOTIFICATION_ID = 4002
        private const val EXTRA_VARIANT = "variant"
        const val ACTION_START = "com.charles.crowdtransit.app.assistant.download.START"
        const val ACTION_STOP = "com.charles.crowdtransit.app.assistant.download.STOP"

        fun start(context: Context, variant: AssistantModelCatalog.Variant) {
            context.startForegroundService(
                Intent(context, AssistantDownloadService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_VARIANT, variant.name),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AssistantDownloadService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    @Inject lateinit var downloader: AssistantModelDownloader

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                downloader.cancel()
                stopSelf()
            }

            ACTION_START -> {
                val variant = intent.getStringExtra(EXTRA_VARIANT)
                    ?.let { runCatching { AssistantModelCatalog.Variant.valueOf(it) }.getOrNull() }
                if (variant != null) startDownload(variant) else stopSelf()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startDownload(variant: AssistantModelCatalog.Variant) {
        val info = AssistantModelCatalog.byVariant(variant)
        createChannel()
        val notification = buildNotification("Downloading Hopper…", "Starting…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        downloader.download(info)

        lifecycleScope.launch {
            downloader.state.collect { state ->
                when (state) {
                    is AssistantModelDownloader.State.Running -> {
                        val pct = if (info.sizeBytes > 0) {
                            (state.downloadedBytes * 100 / info.sizeBytes).toInt()
                        } else {
                            0
                        }
                        val mb = state.downloadedBytes / 1_000_000
                        val totalMb = info.sizeBytes / 1_000_000
                        updateNotification("Downloading Hopper… $pct%", "$mb MB of $totalMb MB")
                    }

                    is AssistantModelDownloader.State.Verifying ->
                        updateNotification("Verifying download…", null)

                    is AssistantModelDownloader.State.Done -> {
                        updateNotification("Hopper is ready", null)
                        stopSelf()
                    }

                    is AssistantModelDownloader.State.Failed -> {
                        updateNotification("Download failed", state.message)
                        stopSelf()
                    }

                    AssistantModelDownloader.State.Idle -> Unit
                }
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI assistant download", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Downloading the on-device Hopper AI model"
            },
        )
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantDownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transit)
            .setContentTitle(title)
            .apply { text?.let { setContentText(it) } }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String?) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(title, text))
    }
}
