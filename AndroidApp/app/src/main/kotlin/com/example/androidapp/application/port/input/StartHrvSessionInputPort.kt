package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.application.usecase.StartHrvSessionUseCase
import com.example.androidapp.domain.model.HrvSession
import java.util.UUID

/**
 * Input port that implements [StartHrvSessionUseCase] by delegating
 * to the [HrvSessionRepositoryPort] output port.
 *
 * In hexagonal architecture (Davi Vieira style) the input port is the concrete
 * class that orchestrates the use case logic.
 *
 * @param hrvSessionRepositoryPort Output port used to persist HRV session data.
 */
class StartHrvSessionInputPort(
    private val hrvSessionRepositoryPort: HrvSessionRepositoryPort
) : StartHrvSessionUseCase {

    /**
     * Create a new HRV session with a generated UUID and persist it.
     *
     * @return The newly created [HrvSession] (with `endTime = null`).
     */
    override suspend fun invoke(): HrvSession {
        val session = HrvSession(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis()
        )
        hrvSessionRepositoryPort.createSession(session)
        return session
    }
}

