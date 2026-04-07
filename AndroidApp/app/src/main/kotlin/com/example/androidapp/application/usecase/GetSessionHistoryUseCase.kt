package com.example.androidapp.application.usecase

import com.example.androidapp.domain.model.HrvSession

/**
 * Use case interface for retrieving the history of all recorded HRV sessions.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
 */
interface GetSessionHistoryUseCase {

    /**
     * Load all persisted HRV sessions, ordered by start time descending.
     *
     * @return List of all [HrvSession] objects with their snapshots.
     */
    suspend operator fun invoke(): List<HrvSession>
}

