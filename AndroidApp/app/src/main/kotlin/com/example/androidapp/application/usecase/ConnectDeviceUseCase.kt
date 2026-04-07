package com.example.androidapp.application.usecase

/**
 * Use case interface for managing the connection lifecycle of a heart-rate device.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
 */
interface ConnectDeviceUseCase {

    /**
     * Connect to the heart-rate device.
     * Suspends until the device is fully connected.
     *
     * @param deviceId Unique identifier of the target device.
     */
    suspend fun connect(deviceId: String)

    /**
     * Disconnect from the heart-rate device.
     *
     * @param deviceId Unique identifier of the target device.
     */
    fun disconnect(deviceId: String)
}

