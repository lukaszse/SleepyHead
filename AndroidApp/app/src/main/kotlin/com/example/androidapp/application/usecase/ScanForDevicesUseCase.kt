package com.example.androidapp.application.usecase

import com.example.androidapp.domain.model.FoundDevice
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for scanning for nearby heart-rate devices.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
 */
interface ScanForDevicesUseCase {

    /**
     * Start scanning for nearby heart-rate devices.
     *
     * @return [Flow] emitting [FoundDevice] objects as they are discovered.
     */
    operator fun invoke(): Flow<FoundDevice>
}

