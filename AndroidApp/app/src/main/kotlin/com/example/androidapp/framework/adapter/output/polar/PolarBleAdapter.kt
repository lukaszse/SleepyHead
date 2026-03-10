package com.example.androidapp.framework.adapter.output.polar

import android.content.Context
import android.util.Log
import com.example.androidapp.application.port.output.HeartRateMonitorPort
import com.example.androidapp.domain.model.HrData
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import kotlinx.coroutines.flow.Flow
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
     * Establish a BLE connection with the Polar device.
     *
     * @param deviceId Polar device identifier (e.g. "A1B2C3D4").
     */
    override fun connect(deviceId: String) {
        api.connectToDevice(deviceId)
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

