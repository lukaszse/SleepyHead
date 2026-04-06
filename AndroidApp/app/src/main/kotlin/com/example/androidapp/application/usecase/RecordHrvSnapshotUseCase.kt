package com.example.androidapp.application.usecase

import com.example.androidapp.domain.model.HrvSnapshot

/**
 * Use case interface for recording a single HRV snapshot within an active session.
 *
 * Implementations (Input Ports) orchestrate domain logic and delegate to Output Ports.
 */
interface RecordHrvSnapshotUseCase {

    /**
     * Persist a single RMSSD snapshot to the given session.
     *
     * @param sessionId Unique identifier of the active session.
     * @param snapshot The [HrvSnapshot] to record.
     */
    suspend operator fun invoke(sessionId: String, snapshot: HrvSnapshot)
}

