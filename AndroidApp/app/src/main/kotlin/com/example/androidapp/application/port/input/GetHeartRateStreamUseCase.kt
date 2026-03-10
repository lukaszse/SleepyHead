package com.example.androidapp.application.port.input

import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.flow.Flow

/**
 * Input port (use case) for obtaining a live stream of heart-rate data.
 */
interface GetHeartRateStreamUseCase {

    /**
     * Return a live stream of heart-rate readings from the given device.
     *
     * @param deviceId Unique identifier of the target device.
     * @return [Flow] emitting [HrData] objects as they arrive from the sensor.
     */
    operator fun invoke(deviceId: String): Flow<HrData>
}

