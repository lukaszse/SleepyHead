package com.example.androidapp.framework.adapter.output.polar

import android.content.Context
import android.util.Log
import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.domain.model.FoundDevice
import com.example.androidapp.domain.model.HrData
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Driven adapter that implements [HeartRateMonitorPort] using the Polar BLE SDK.
 *
 * This is the only class in the project that depends on the Polar SDK.
 * It converts Polar-specific types ([PolarHrData]) into domain types ([HrData])
 * and bridges RxJava streams to Kotlin [Flow].
 *
 * @param context Android [Context] required to initialise the Polar BLE API.
 */
class PolarBleAdapter(context: Context) : HeartRateMonitorPort {

    companion object {
        private const val TAG = "PolarBleAdapter"
    }

    /**
     * SharedFlow that emits the deviceId when a device becomes fully connected.
     * Used by [connect] to suspend until the BLE connection is established.
     */
    private val deviceConnectedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE
        )
    )

    init {
        api.setApiCallback(object : PolarBleApiCallback() {

            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "Bluetooth power state changed: powered=$powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected: ${polarDeviceInfo.deviceId}")
                deviceConnectedFlow.tryEmit(polarDeviceInfo.deviceId)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connecting: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device disconnected: ${polarDeviceInfo.deviceId}")
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS info — identifier=$identifier uuid=$uuid value=$value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery level — identifier=$identifier level=$level%")
            }
        })
    }

    /**
     * Scan for nearby Polar devices using the Polar SDK search API.
     * Converts the RxJava Flowable to a Kotlin Flow and maps to domain [FoundDevice].
     *
     * @return [Flow] emitting [FoundDevice] objects as they are discovered.
     */
    override fun scanForDevices(): Flow<FoundDevice> {
        return api.searchForDevice()
            .asFlow()
            .map { deviceInfo ->
                FoundDevice(
                    deviceId = deviceInfo.deviceId,
                    name = deviceInfo.name.ifBlank { null }
                )
            }
    }

    /**
     * Establish a BLE connection with the Polar device.
     * Suspends until the [deviceConnected] callback fires for this [deviceId].
     *
     * @param deviceId Polar device identifier (e.g. "A1B2C3D4").
     */
    override suspend fun connect(deviceId: String) {
        api.connectToDevice(deviceId)
        // Suspend until the callback confirms this device is connected
        deviceConnectedFlow.first { it == deviceId }
    }

    /**
     * Disconnect from the Polar device.
     *
     * @param deviceId Polar device identifier (e.g. "A1B2C3D4").
     */
    override fun disconnect(deviceId: String) {
        api.disconnectFromDevice(deviceId)
    }

    /**
     * Start streaming heart-rate data from the Polar device and return it as a [Flow].
     *
     * Internally converts the Polar SDK RxJava [io.reactivex.rxjava3.core.Flowable] to
     * a Kotlin [Flow] and maps [PolarHrData] samples to domain [HrData] objects.
     *
     * @param deviceId Polar device identifier (e.g. "A1B2C3D4").
     * @return [Flow] emitting [HrData] objects as they arrive from the sensor.
     */
    override fun getHeartRateStream(deviceId: String): Flow<HrData> {
        return api.startHrStreaming(deviceId)
            .asFlow()
            .map { polarHrData: PolarHrData ->
                val sample = polarHrData.samples.first()
                HrData(
                    bpm = sample.hr,
                    rrIntervals = sample.rrsMs
                )
            }
    }
}
