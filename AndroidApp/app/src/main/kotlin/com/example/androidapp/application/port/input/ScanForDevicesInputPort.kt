package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.application.usecase.ScanForDevicesUseCase
import com.example.androidapp.domain.model.FoundDevice
import kotlinx.coroutines.flow.Flow

/**
 * Input port that implements [ScanForDevicesUseCase] by delegating
 * to the [HeartRateMonitorPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param monitorPort Output port used to communicate with the heart-rate hardware.
 */
class ScanForDevicesInputPort(
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

