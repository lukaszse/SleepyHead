package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.application.usecase.GetSessionHistoryUseCase
import com.example.androidapp.domain.model.HrvSession

/**
 * Input port that implements [GetSessionHistoryUseCase] by delegating
 * to the [HrvSessionRepositoryPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param repository Output port used to persist HRV session data.
 */
class GetSessionHistoryInputPort(
    private val repository: HrvSessionRepositoryPort
) : GetSessionHistoryUseCase {

    /**
     * Load all persisted HRV sessions, ordered by start time descending.
     *
     * @return List of all [HrvSession] objects with their snapshots.
     */
    override suspend fun invoke(): List<HrvSession> =
        repository.loadAll()
}

