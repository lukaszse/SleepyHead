package com.example.androidapp.application.port.input

/**
 * Input port (use case) for managing the connection lifecycle of a heart-rate device.
 */
interface ConnectDeviceUseCase {

    /**
     * Connect to the heart-rate device.
     *
     * @param deviceId Unique identifier of the target device.
     */
    fun connect(deviceId: String)

    /**
     * Disconnect from the heart-rate device.
     *
     * @param deviceId Unique identifier of the target device.
     */
    fun disconnect(deviceId: String)
}

