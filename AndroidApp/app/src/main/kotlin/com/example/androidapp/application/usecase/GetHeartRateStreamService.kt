package com.example.androidapp.application.usecase

import com.example.androidapp.application.port.input.GetHeartRateStreamUseCase
import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.flow.Flow

/**
 * Application service that implements [GetHeartRateStreamUseCase] by delegating
 * to the [HeartRateMonitorPort] output port.
 *
 * @param monitorPort Output port used to communicate with the heart-rate hardware.
 */
class GetHeartRateStreamService(
    private val monitorPort: HeartRateMonitorPort
) : GetHeartRateStreamUseCase {

    /**
     * Return a live stream of heart-rate readings from the given device.
     *
     * @param deviceId Unique identifier of the target device.
     * @return [Flow] emitting [HrData] objects as they arrive from the sensor.
     */
    override fun invoke(deviceId: String): Flow<HrData> =
        monitorPort.getHeartRateStream(deviceId)
}


