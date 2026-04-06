package com.example.androidapp.application.usecase

import com.example.androidapp.domain.model.HrvSession

/**
 * Use case interface for starting a new HRV recording session.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
 */
interface StartHrvSessionUseCase {

    /**
     * Start a new HRV session and persist its initial record.
     *
     * @return The newly created [HrvSession] (with `endTime = null`).
     */
    suspend operator fun invoke(): HrvSession
}

