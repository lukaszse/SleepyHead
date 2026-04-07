package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.application.usecase.RecordHrvSnapshotUseCase
import com.example.androidapp.domain.model.HrvSnapshot

/**
 * Input port that implements [RecordHrvSnapshotUseCase] by delegating
 * to the [HrvSessionRepositoryPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param repository Output port used to persist HRV session data.
 */
class RecordHrvSnapshotInputPort(
    private val repository: HrvSessionRepositoryPort
) : RecordHrvSnapshotUseCase {

    /**
     * Append a single RMSSD snapshot to the given session.
     *
     * @param sessionId Unique identifier of the active session.
     * @param snapshot The [HrvSnapshot] to persist.
     */
    override suspend fun invoke(sessionId: String, snapshot: HrvSnapshot) {
        repository.appendSnapshot(sessionId, snapshot)
    }
}

