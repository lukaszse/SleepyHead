package com.example.androidapp.framework.adapter.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.application.usecase.ConnectDeviceUseCase
import com.example.androidapp.application.usecase.GetHeartRateStreamUseCase
import com.example.androidapp.application.usecase.ScanForDevicesUseCase
import com.example.androidapp.domain.model.FoundDevice
import com.example.androidapp.domain.model.HrData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Driving (input) adapter that bridges the UI infrastructure with the application use cases.
 *
 * In Davi Vieira's hexagonal architecture this is a **framework input adapter** —
 * it translates UI intents into use-case calls and exposes observable state
 * consumed by the infrastructure UI layer (HrScreen).
 *
 * It communicates exclusively with use case interfaces — never with framework adapters directly.
 *
 * @param connectUseCase Use case for managing the device connection lifecycle.
 * @param streamUseCase Use case for obtaining a live heart-rate data stream.
 * @param scanUseCase Use case for scanning for nearby heart-rate devices.
 */
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase,
    private val scanUseCase: ScanForDevicesUseCase
) : ViewModel() {

    private val _hrData = MutableStateFlow<HrData?>(null)
    val hrData: StateFlow<HrData?> = _hrData.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _foundDevices = MutableStateFlow<List<FoundDevice>>(emptyList())
    /**
     * Observable state holding the list of discovered devices during scanning.
     */
    val foundDevices: StateFlow<List<FoundDevice>> = _foundDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    /**
     * Observable state indicating whether a BLE scan is currently in progress.
     */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    /**
     * Start scanning for nearby heart-rate devices.
     * Discovered devices are added to [foundDevices]. Duplicates are filtered by deviceId.
     */
    fun startScan() {
        if (_isScanning.value) return

        _foundDevices.value = emptyList()
        _error.value = null
        _isScanning.value = true

        scanJob = viewModelScope.launch {
            scanUseCase()
                .catch { throwable ->
                    _error.value = throwable.message ?: "Scan failed"
                    _isScanning.value = false
                }
                .collect { device ->
                    val current = _foundDevices.value
                    if (current.none { it.deviceId == device.deviceId }) {
                        _foundDevices.value = current + device
                    }
                }
        }
    }

    /**
     * Stop the ongoing BLE scan.
     */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    /**
     * Connect to the heart-rate device and start collecting the HR data stream.
     *
     * @param deviceId Unique identifier of the target Polar device (e.g. "A1B2C3D4").
     */
    fun startMonitoring(deviceId: String) {
        stopScan()
        _isConnected.value = false
        _error.value = null

        viewModelScope.launch {
            try {
                connectUseCase.connect(deviceId)
                _isConnected.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Connection failed"
                return@launch
            }

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

