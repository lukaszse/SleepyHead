package com.example.androidapp.framework.adapter.output.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.androidapp.application.port.output.MonitoringServicePort

/**
 * Framework adapter that implements [MonitoringServicePort] using Android
 * [Context] and [Intent] to start/stop the [HrvMonitoringService].
 *
 * This is a thin adapter with no business logic — it only translates
 * the port's methods into Android service lifecycle calls.
 *
 * @param context Application [Context] used to start/stop the service.
 */
class HrvServiceController(
    private val context: Context
) : MonitoringServicePort {

    override fun startForegroundMonitoring() {
        val intent = Intent(context, HrvMonitoringService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopForegroundMonitoring() {
        val intent = Intent(context, HrvMonitoringService::class.java)
        context.stopService(intent)
    }
}
