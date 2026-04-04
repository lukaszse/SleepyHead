package com.example.androidapp.framework.bootstrap

import android.content.Context
import com.example.androidapp.application.port.input.ConnectDeviceInputPort
import com.example.androidapp.application.port.input.GetHeartRateStreamInputPort
import com.example.androidapp.application.port.input.ScanForDevicesInputPort
import com.example.androidapp.framework.adapter.input.HrViewModel
import com.example.androidapp.framework.adapter.output.polar.PolarBleAdapter

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

    private val polarAdapter = PolarBleAdapter(context)

    private val connectInputPort = ConnectDeviceInputPort(polarAdapter)
    private val streamInputPort = GetHeartRateStreamInputPort(polarAdapter)
    private val scanInputPort = ScanForDevicesInputPort(polarAdapter)

    /**
     * Fully wired [HrViewModel] ready to be consumed by the UI layer.
     */
    val viewModel = HrViewModel(connectInputPort, streamInputPort, scanInputPort)
}

