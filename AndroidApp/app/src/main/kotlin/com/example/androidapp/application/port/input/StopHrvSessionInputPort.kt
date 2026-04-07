package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.application.usecase.StopHrvSessionUseCase
import com.example.androidapp.domain.model.HrvSession

/**
 * Input port that implements [StopHrvSessionUseCase] by delegating
 * to the [HrvSessionRepositoryPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param repository Output port used to persist HRV session data.
 */
class StopHrvSessionInputPort(
    private val repository: HrvSessionRepositoryPort
) : StopHrvSessionUseCase {

    /**
     * Stop the given session by setting its end time and finalising persistence.
     *
     * @param sessionId Unique identifier of the session to stop.
     * @return The finalised [HrvSession] with `endTime` set.
     */
    override suspend fun invoke(sessionId: String): HrvSession {
        val allSessions = repository.loadAll()
        val session = allSessions.first { it.id == sessionId }
        val finalised = session.copy(endTime = System.currentTimeMillis())
        repository.finaliseSession(finalised)
        return finalised
    }
}

