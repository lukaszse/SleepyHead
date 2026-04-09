package com.example.androidapp.application.port.output

import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot

/**
 * Output port defining the persistence contract for HRV sessions.
 *
 * Framework adapters (e.g. JSONL file storage, Room) implement this interface
 * so that the application layer remains decoupled from any specific storage
 * technology.
 *
 * This port operates exclusively on domain objects — no framework types leak in.
 */
interface HrvSessionRepositoryPort {

    /**
     * Create a new session record in the underlying storage.
     *
     * @param session The session to persist (usually with `endTime = null`).
     */
    suspend fun createSession(session: HrvSession)

    /**
     * Append a single RMSSD snapshot to an existing session.
     *
     * @param sessionId Unique identifier of the target session.
     * @param snapshot The snapshot to append.
     */
    suspend fun appendSnapshot(sessionId: String, snapshot: HrvSnapshot)

    /**
     * Finalise a session by updating its header (e.g. setting `endTime`).
     *
     * @param session The session with updated fields (typically `endTime` set).
     */
    suspend fun finaliseSession(session: HrvSession)

    /**
     * Find a single session by its unique identifier.
     *
     * @param sessionId Unique identifier of the target session.
     * @return The matching [HrvSession] with its snapshots, or `null` if not found.
     */
    suspend fun findById(sessionId: String): HrvSession?

    /**
     * Load all persisted sessions, ordered by start time descending.
     *
     * @return List of all sessions with their snapshots.
     */
    suspend fun loadAll(): List<HrvSession>
}

