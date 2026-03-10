package com.example.androidapp.framework.adapter.input.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.application.port.input.ConnectDeviceUseCase
import com.example.androidapp.application.port.input.GetHeartRateStreamUseCase
import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel that bridges the UI layer with the application use cases.
 *
 * It communicates exclusively with [ConnectDeviceUseCase] and
 * [GetHeartRateStreamUseCase] — never with framework adapters directly.
 *
 * @param connectUseCase Use case for managing the device connection lifecycle.
 * @param streamUseCase Use case for obtaining a live heart-rate data stream.
 */
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase
) : ViewModel() {

    private val _hrData = MutableStateFlow<HrData?>(null)

    /**
     * Observable state holding the latest [HrData] received from the sensor,
     * or `null` if no data has been received yet.
     */
    val hrData: StateFlow<HrData?> = _hrData.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /**
     * Observable state holding the latest error message, or `null` if there is no error.
     */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow(false)

    /**
     * Observable state indicating whether the device is currently streaming data.
     */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Connect to the heart-rate device and start collecting the HR data stream.
     *
     * @param deviceId Unique identifier of the target Polar device (e.g. "A1B2C3D4").
     */
    fun startMonitoring(deviceId: String) {
        connectUseCase.connect(deviceId)
        _isConnected.value = true
        _error.value = null

        viewModelScope.launch {
            streamUseCase(deviceId)
                .catch { throwable ->
                    _error.value = throwable.message ?: "Unknown error"
                    _isConnected.value = false
                }
                .collect { data ->
                    _hrData.value = data
                }
        }
    }

    /**
     * Disconnect from the heart-rate device and stop collecting data.
     *
     * @param deviceId Unique identifier of the target Polar device (e.g. "A1B2C3D4").
     */
    fun stopMonitoring(deviceId: String) {
        connectUseCase.disconnect(deviceId)
        _isConnected.value = false
    }
}

