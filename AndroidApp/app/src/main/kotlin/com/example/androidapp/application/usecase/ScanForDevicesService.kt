package com.example.androidapp.application.usecase

import com.example.androidapp.application.port.input.ScanForDevicesUseCase
import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.domain.model.FoundDevice
import kotlinx.coroutines.flow.Flow

/**
 * Application service that implements [ScanForDevicesUseCase] by delegating
 * to the [HeartRateMonitorPort] output port.
 *
 * @param monitorPort Output port used to communicate with the heart-rate hardware.
 */
class ScanForDevicesService(
    private val monitorPort: HeartRateMonitorPort
) : ScanForDevicesUseCase {

    /**
     * Start scanning for nearby heart-rate devices.
     *
     * @return [Flow] emitting [FoundDevice] objects as they are discovered.
     */
    override fun invoke(): Flow<FoundDevice> =
        monitorPort.scanForDevices()
}

