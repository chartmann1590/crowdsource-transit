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
import com.charles.crowdtransit.app.MainActivity
import com.charles.crowdtransit.app.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that keeps the app process at foreground priority for the
 * duration of one Hopper reply. On-device CPU inference over an image can take well
 * over a minute; confirmed on a real device that without this, the process gets killed
 * by the system the moment the app is even briefly backgrounded during that window —
 * most commonly when the system camera app takes focus during "scan a stop sign", or
 * when the user checks something else while waiting for a text reply. The generation
 * itself is unaffected either way (it's not run inside this service); this only raises
 * the process's importance so Android's low-memory killer doesn't treat it as an easy
 * background target while a reply is in flight. Modelled on AssistantDownloadService —
 * same channel/notification shape, START/STOP actions, START_NOT_STICKY, dataSync type
 * (already granted for that service; a generated reply is comparably "processing app
 * data" as a one-shot background task).
 */
@AndroidEntryPoint
class AssistantInferenceService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "assistant_inference"
        private const val NOTIFICATION_ID = 4003
        private const val ACTION_START = "com.charles.crowdtransit.app.assistant.inference.START"
        private const val ACTION_STOP = "com.charles.crowdtransit.app.assistant.inference.STOP"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AssistantInferenceService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AssistantInferenceService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> {
                createChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI assistant replies", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Hopper is generating a reply on-device"
            },
        )
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transit)
            .setContentTitle("Hopper is thinking…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
