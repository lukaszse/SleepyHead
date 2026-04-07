package com.example.androidapp.application.port.output

/**
 * Output port for controlling the foreground monitoring service lifecycle.
 *
 * In Davi Vieira's hexagonal architecture this is a **driven port** —
 * the application layer declares the contract, the framework layer provides
 * the Android-specific implementation ([HrvServiceController]).
 *
 * This abstraction ensures the ViewModel does not depend on Android Context,
 * Intent, or any framework class — making it fully unit-testable with a mock.
 */
interface MonitoringServicePort {

    /**
     * Start the foreground service with a persistent notification and WakeLock.
     * Called when BLE monitoring begins (device connected, HRV session started).
     */
    fun startForegroundMonitoring()

    /**
     * Stop the foreground service, release WakeLock, and remove the notification.
     * Called when BLE monitoring ends (user disconnect or auto-end).
     */
    fun stopForegroundMonitoring()
}

