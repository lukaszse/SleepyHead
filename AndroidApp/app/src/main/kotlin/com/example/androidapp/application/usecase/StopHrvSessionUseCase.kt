package com.example.androidapp.application.usecase

import com.example.androidapp.domain.model.HrvSession

/**
 * Use case interface for stopping an active HRV recording session.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
 */
interface StopHrvSessionUseCase {

    /**
     * Stop the given session by setting its end time and finalising persistence.
     *
     * @param sessionId Unique identifier of the session to stop.
     * @return The finalised [HrvSession] with `endTime` set.
     */
    suspend operator fun invoke(sessionId: String): HrvSession
}

