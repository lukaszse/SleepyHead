package com.example.androidapp.framework.adapter.output.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the Android process alive during nocturnal
 * HRV monitoring sessions.
 *
 * This service acts as a **keep-alive shell** — it does not own the monitoring
 * logic (which remains in [HrViewModel][com.example.androidapp.framework.adapter.input.HrViewModel]).
 * Its responsibilities are:
 *
 * 1. Display a persistent "Sleep session active" notification.
 * 2. Acquire a [PARTIAL_WAKE_LOCK][PowerManager.PARTIAL_WAKE_LOCK] to prevent
 *    the CPU from sleeping during data collection.
 * 3. Keep the process in foreground state so Android does not kill it.
 *
 * Controlled via [HrvServiceController] which implements
 * [MonitoringServicePort][com.example.androidapp.application.port.output.MonitoringServicePort].
 */
class HrvMonitoringService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        Log.i(TAG, "HRV monitoring service started (foreground)")
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        Log.i(TAG, "HRV monitoring service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when HRV sleep monitoring is active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep session active")
            .setContentText("Recording HRV data from Polar H10")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    // --- WakeLock ---

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(MAX_WAKE_LOCK_DURATION_MS)
            }
            Log.d(TAG, "WakeLock acquired (timeout: ${MAX_WAKE_LOCK_DURATION_MS / 3_600_000}h)")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    companion object {
        internal const val TAG = "HrvMonitoringService"
        internal const val NOTIFICATION_ID = 1001
        internal const val CHANNEL_ID = "hrv_monitoring"
        internal const val CHANNEL_NAME = "HRV Monitoring"
        internal const val WAKE_LOCK_TAG = "SleepyHead:HrvMonitoring"

        /** 10 hours — covers the longest reasonable sleep session. */
        internal const val MAX_WAKE_LOCK_DURATION_MS = 10 * 60 * 60 * 1000L
    }
}

