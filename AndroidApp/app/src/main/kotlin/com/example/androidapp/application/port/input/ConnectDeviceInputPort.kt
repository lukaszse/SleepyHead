package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.application.usecase.ConnectDeviceUseCase

/**
 * Input port that implements [ConnectDeviceUseCase] by delegating
 * to the [HeartRateMonitorPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param monitorPort Output port used to communicate with the heart-rate hardware.
 */
class ConnectDeviceInputPort(
    private val monitorPort: HeartRateMonitorPort
) : ConnectDeviceUseCase {

    /**
     * Connect to the heart-rate device.
     * Suspends until the device is fully connected.
     *
     * @param deviceId Unique identifier of the target device.
     */
    override suspend fun connect(deviceId: String) {
        monitorPort.connect(deviceId)
    }

    /**
     * Disconnect from the heart-rate device.
     *
     * @param deviceId Unique identifier of the target device.
     */
    override fun disconnect(deviceId: String) {
        monitorPort.disconnect(deviceId)
    }
}

