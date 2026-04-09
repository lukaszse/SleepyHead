package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.application.usecase.GetHeartRateStreamUseCase
import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.flow.Flow

/**
 * Input port that implements [GetHeartRateStreamUseCase] by delegating
 * to the [HeartRateMonitorPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param heartRateMonitorPort Output port used to communicate with the heart-rate hardware.
 */
class GetHeartRateStreamInputPort(
    private val heartRateMonitorPort: HeartRateMonitorPort
) : GetHeartRateStreamUseCase {

    /**
     * Return a live stream of heart-rate readings from the given device.
     *
     * @param deviceId Unique identifier of the target device.
     * @return [Flow] emitting [HrData] objects as they arrive from the sensor.
     */
    override fun invoke(deviceId: String): Flow<HrData> =
        heartRateMonitorPort.getHeartRateStream(deviceId)
}

