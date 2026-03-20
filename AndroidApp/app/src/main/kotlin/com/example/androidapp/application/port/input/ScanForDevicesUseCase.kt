package com.example.androidapp.application.port.input

import com.example.androidapp.domain.model.FoundDevice
import kotlinx.coroutines.flow.Flow

/**
 * Input port (use case) for scanning for nearby heart-rate devices.
 */
interface ScanForDevicesUseCase {

    /**
     * Start scanning for nearby heart-rate devices.
     *
     * @return [Flow] emitting [FoundDevice] objects as they are discovered.
     */
    operator fun invoke(): Flow<FoundDevice>
}

