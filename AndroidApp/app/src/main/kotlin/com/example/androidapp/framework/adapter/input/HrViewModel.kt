package com.example.androidapp.framework.adapter.input

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.application.port.output.MonitoringServicePort
import com.example.androidapp.application.usecase.ConnectDeviceUseCase
import com.example.androidapp.application.usecase.GetSessionHistoryUseCase
import com.example.androidapp.application.usecase.GetHeartRateStreamUseCase
import com.example.androidapp.application.usecase.RecordHrvSnapshotUseCase
import com.example.androidapp.application.usecase.ScanForDevicesUseCase
import com.example.androidapp.application.usecase.StartHrvSessionUseCase
import com.example.androidapp.application.usecase.StopHrvSessionUseCase
import com.example.androidapp.domain.model.FoundDevice
import com.example.androidapp.domain.model.HrData
import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import com.example.androidapp.domain.service.HrvCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * Extends HR monitoring with **live HRV (RMSSD)** computation, session management,
 * and per-minute snapshot recording as described in TDR-001.
 *
 * @param connectUseCase Use case for managing the device connection lifecycle.
 * @param streamUseCase Use case for obtaining a live heart-rate data stream.
 * @param scanUseCase Use case for scanning for nearby heart-rate devices.
 * @param startSessionUseCase Use case for starting a new HRV recording session.
 * @param recordSnapshotUseCase Use case for persisting per-minute RMSSD snapshots.
 * @param stopSessionUseCase Use case for stopping an active HRV session.
 * @param getSessionHistoryUseCase Use case for loading persisted HRV session history.
 * @param monitoringServicePort Output port for controlling the foreground keep-alive service.
 * @param timeProvider Clock function for testability. Defaults to [System.currentTimeMillis].
 */
class HrViewModel(
    private val connectUseCase: ConnectDeviceUseCase,
    private val streamUseCase: GetHeartRateStreamUseCase,
    private val scanUseCase: ScanForDevicesUseCase,
    private val startSessionUseCase: StartHrvSessionUseCase,
    private val recordSnapshotUseCase: RecordHrvSnapshotUseCase,
    private val stopSessionUseCase: StopHrvSessionUseCase,
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val monitoringServicePort: MonitoringServicePort? = null,
    private val timeProvider: () -> Long = System::currentTimeMillis
) : ViewModel() {

    // --- Existing HR state (unchanged) ---

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

    // --- HRV state (new — TDR-001) ---

    private val _currentRmssd = MutableStateFlow<Double?>(null)
    /** Latest RMSSD value computed from the 5-minute sliding window. */
    val currentRmssd: StateFlow<Double?> = _currentRmssd.asStateFlow()

    private val _sessionAverage = MutableStateFlow<Double?>(null)
    /** Running average of all RMSSD snapshots in the active session. */
    val sessionAverage: StateFlow<Double?> = _sessionAverage.asStateFlow()

    private val _sessionSnapshots = MutableStateFlow<List<HrvSnapshot>>(emptyList())
    /** Ordered list of per-minute snapshots for the live chart. */
    val sessionSnapshots: StateFlow<List<HrvSnapshot>> = _sessionSnapshots.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    /** Whether an HRV recording session is currently active. */
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _sessionHistory = MutableStateFlow<List<HrvSession>>(emptyList())
    /** List of all past HRV sessions loaded from persistence. */
    val sessionHistory: StateFlow<List<HrvSession>> = _sessionHistory.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    /** Whether session history is currently being loaded. */
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    // --- Internal HRV ---

    /** Timestamped RR buffer — each entry is (receivedAt: Long, rrMs: Int). */
    private val rrBuffer = ArrayDeque<Pair<Long, Int>>()
    private var currentSessionId: String? = null
    private var lastReceivedTimestamp: Long = 0L
    private var currentDeviceId: String? = null
    private var monitoringJob: Job? = null

    // --- Scanning (unchanged) ---

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

    // --- Monitoring (extended with HRV) ---

    /**
     * Connect to the heart-rate device, start the HR data stream,
     * and begin an HRV recording session.
     *
     * Timer and watcher coroutines are launched as **structured children** of the
     * monitoring job so that cancelling [monitoringJob] tears down everything.
     *
     * @param deviceId Unique identifier of the target Polar device (e.g. "A1B2C3D4").
     */
    fun startMonitoring(deviceId: String) {
        monitoringJob?.cancel()
        stopScan()
        _isConnected.value = false
        _error.value = null
        currentDeviceId = deviceId

        monitoringJob = viewModelScope.launch {
            try {
                connectUseCase.connect(deviceId)
                _isConnected.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Connection failed"
                return@launch
            }

            // Start HRV session automatically on connect (TDR-001 §3.2)
            val session = startSessionUseCase()
            currentSessionId = session.id
            rrBuffer.clear()
            _sessionSnapshots.value = emptyList()
            _currentRmssd.value = null
            _sessionAverage.value = null
            _sessionActive.value = true
            lastReceivedTimestamp = timeProvider()

            // Start foreground service to keep process alive during sleep (TDR-001 Phase F)
            try {
                monitoringServicePort?.startForegroundMonitoring()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start foreground service: ${e.message}")
            }

            // Timer & watcher as CHILDREN of monitoringJob (structured concurrency)
            launch { snapshotTimerLoop() }
            launch { autoEndWatcherLoop() }

            streamUseCase(deviceId)
                .catch { throwable ->
                    _error.value = throwable.message ?: "Unknown error"
                    _isConnected.value = false
                }
                .collect { data ->
                    _hrData.value = data
                    lastReceivedTimestamp = timeProvider()
                    addRrIntervals(data)
                }
        }
    }

    /**
     * Disconnect from the heart-rate device, stop collecting data,
     * and finalise the active HRV session.
     *
     * @param deviceId Unique identifier of the target Polar device (e.g. "A1B2C3D4").
     */
    fun stopMonitoring(deviceId: String) {
        monitoringJob?.cancel()
        monitoringJob = null
        connectUseCase.disconnect(deviceId)
        _isConnected.value = false
        finaliseSession()

        // Stop foreground service (TDR-001 Phase F)
        try {
            monitoringServicePort?.stopForegroundMonitoring()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop foreground service: ${e.message}")
        }
    }

    // --- HRV Session Finalisation ---

    /**
     * Persist the session end-time and reset HRV state.
     * Called from [stopMonitoring] — timer/watcher are already cancelled
     * structurally via [monitoringJob] cancellation.
     */
    private fun finaliseSession() {
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                try {
                    stopSessionUseCase(sessionId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to finalise session: ${e.message}")
                }
            }
        }
        _sessionActive.value = false
        currentSessionId = null
    }

    // --- RR Buffer — 5-Minute Sliding Window (TDR-001 §6.1) ---

    internal fun addRrIntervals(hrData: HrData) {
        val now = timeProvider()
        hrData.rrIntervals.forEach { rrBuffer.addLast(now to it) }
        val cutoff = now - HrvCalculator.WINDOW_MS
        while (rrBuffer.isNotEmpty() && rrBuffer.first().first < cutoff) {
            rrBuffer.removeFirst()
        }
    }

    // --- Per-Minute Snapshot Timer (TDR-001 §6.2) ---

    private suspend fun snapshotTimerLoop() {
        while (_isConnected.value) {
            delay(SNAPSHOT_INTERVAL_MS)
            val intervals = rrBuffer.map { it.second }
            val rmssd = HrvCalculator.rmssd(intervals)
            if (rmssd != null) {
                val snapshot = HrvSnapshot(
                    timestamp = timeProvider(),
                    rmssdMs = rmssd,
                    rrIntervalCount = intervals.size,
                    windowDurationMs = HrvCalculator.WINDOW_MS
                )
                currentSessionId?.let { sessionId ->
                    try {
                        recordSnapshotUseCase(sessionId, snapshot)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to record snapshot: ${e.message}")
                    }
                }
                _sessionSnapshots.value = _sessionSnapshots.value + snapshot
                _sessionAverage.value = _sessionSnapshots.value.map { it.rmssdMs }.average()
                _currentRmssd.value = rmssd
            }
        }
    }

    // --- Auto-End Watcher (TDR-001 §6.3) ---

    private suspend fun autoEndWatcherLoop() {
        while (_isConnected.value) {
            delay(AUTO_END_CHECK_INTERVAL_MS)
            val gap = timeProvider() - lastReceivedTimestamp
            if (gap >= AUTO_END_GAP_MS) {
                currentDeviceId?.let { stopMonitoring(it) }
            }
        }
    }

    // --- Session History (TDR-001 §8.5) ---

    /**
     * Load all persisted HRV sessions from storage.
     * Results are emitted to [sessionHistory].
     */
    fun loadSessionHistory() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                _sessionHistory.value = getSessionHistoryUseCase()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load session history: ${e.message}")
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    companion object {
        private const val TAG = "HrViewModel"

        /** Snapshot recording interval: once per minute. */
        internal const val SNAPSHOT_INTERVAL_MS = 60_000L

        /** Auto-end session after 2-hour data gap. */
        internal const val AUTO_END_GAP_MS = 2 * 60 * 60 * 1000L

        /** How often to check for auto-end condition. */
        internal const val AUTO_END_CHECK_INTERVAL_MS = 60_000L
    }
}
