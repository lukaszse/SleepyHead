package com.example.androidapp.framework.bootstrap

import android.content.Context
import com.example.androidapp.application.port.input.ConnectDeviceInputPort
import com.example.androidapp.application.port.input.GetHeartRateStreamInputPort
import com.example.androidapp.application.port.input.GetSessionHistoryInputPort
import com.example.androidapp.application.port.input.RecordHrvSnapshotInputPort
import com.example.androidapp.application.port.input.ScanForDevicesInputPort
import com.example.androidapp.application.port.input.StartHrvSessionInputPort
import com.example.androidapp.application.port.input.StopHrvSessionInputPort
import com.example.androidapp.framework.adapter.input.HrViewModel
import com.example.androidapp.framework.adapter.output.file.HrvSessionFileAdapter
import com.example.androidapp.framework.adapter.output.polar.PolarBleAdapter
import com.example.androidapp.framework.adapter.output.service.HrvServiceController

/**
 * Composition root (bootstrap) — manual dependency injection wiring.
 *
 * In Davi Vieira's hexagonal architecture this class lives in the **bootstrap**
 * area of the Framework hexagon.  Its sole responsibility is to instantiate
 * adapters, input ports and the ViewModel, then expose them to infrastructure
 * components (e.g. [MainActivity][com.example.androidapp.framework.infra.MainActivity]).
 *
 * @param context Android application [Context] required by framework adapters.
 */
class AppDependencies(context: Context) {

    // --- Output adapters (driven) ---
    private val polarAdapter = PolarBleAdapter(context)
    private val hrvFileAdapter = HrvSessionFileAdapter(context.filesDir)
    private val serviceController = HrvServiceController(context)

    // --- Input ports (HR) ---
    private val connectInputPort = ConnectDeviceInputPort(polarAdapter)
    private val streamInputPort = GetHeartRateStreamInputPort(polarAdapter)
    private val scanInputPort = ScanForDevicesInputPort(polarAdapter)

    // --- Input ports (HRV) ---
    private val startHrvSessionInputPort = StartHrvSessionInputPort(hrvFileAdapter)
    private val recordHrvSnapshotInputPort = RecordHrvSnapshotInputPort(hrvFileAdapter)
    private val stopHrvSessionInputPort = StopHrvSessionInputPort(hrvFileAdapter)
    private val getSessionHistoryInputPort = GetSessionHistoryInputPort(hrvFileAdapter)

    /**
     * Fully wired [HrViewModel] ready to be consumed by the UI layer.
     */
    val viewModel = HrViewModel(
        connectInputPort, streamInputPort, scanInputPort,
        startHrvSessionInputPort, recordHrvSnapshotInputPort, stopHrvSessionInputPort,
        getSessionHistoryInputPort,
        serviceController
    )
}
