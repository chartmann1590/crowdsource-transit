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
import com.charles.crowdtransit.app.data.navigation.NavigationSessionRepository
import com.charles.crowdtransit.app.ui.screens.map.locationFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service driving live navigation: collects GPS fixes (only while the session
 * is active; started from the foreground, no background-location permission), feeds the
 * NavigationEngine via the session repository, and mirrors the current instruction into
 * an ongoing notification. Stops itself on arrival or END action.
 */
@AndroidEntryPoint
class NavigationService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_START = "com.charles.crowdtransit.app.navigation.START"
        const val ACTION_STOP = "com.charles.crowdtransit.app.navigation.STOP"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, NavigationService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NavigationService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    @Inject lateinit var session: NavigationSessionRepository

    private var locationJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                session.stop()
                stopSelf()
            }

            else -> if (session.isActive) startNavigation() else stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startNavigation() {
        createChannel()
        val notification = buildNotification("Starting navigation…", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (locationJob != null) return
        locationJob = lifecycleScope.launch {
            locationFlow(this@NavigationService).collect { fix ->
                session.onLocation(fix.lat, fix.lng)
                val state = session.state.value
                if (state == null || state.arrived) {
                    state?.let { updateNotification(it.instruction, null) }
                    stopSelf()
                    return@collect
                }
                updateNotification(
                    (if (state.offRoute) "Off route — " else "") + state.instruction,
                    state.nextInstruction?.let { "Then: $it" },
                )
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live trip navigation"
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
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NavigationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transit)
            .setContentTitle(title)
            .apply { text?.let { setContentText(it) } }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "End", stopIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String?) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        locationJob?.cancel()
        super.onDestroy()
    }
}
