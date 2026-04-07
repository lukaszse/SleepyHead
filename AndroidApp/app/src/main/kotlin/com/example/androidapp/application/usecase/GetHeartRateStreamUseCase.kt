package com.example.androidapp.application.usecase

import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for obtaining a live stream of heart-rate data.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
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

