package com.example.androidapp.application.port.output

import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.flow.Flow

/**
 * Output port defining the contract for heart-rate monitor hardware communication.
 *
 * Framework adapters (e.g. Polar BLE SDK) implement this interface so that
 * the application layer remains decoupled from any specific technology.
 */
interface HeartRateMonitorPort {

    /**
     * Establish a connection with the heart-rate sensor.
     *
     * @param deviceId Unique identifier of the target device.
     */
    fun connect(deviceId: String)

    /**
     * Disconnect from the heart-rate sensor.
     *
     * @param deviceId Unique identifier of the target device.
     */
    fun disconnect(deviceId: String)

    /**
     * Return a reactive stream of heart-rate readings from the sensor.
     *
     * @param deviceId Unique identifier of the target device.
     * @return [Flow] emitting [HrData] objects as they arrive from the sensor.
     */
    fun getHeartRateStream(deviceId: String): Flow<HrData>
}


