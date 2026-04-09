package com.example.androidapp.framework.adapter.output.file

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Driven (output) adapter that persists HRV sessions as JSONL files.
 *
 * Each session is stored as a single `.jsonl` file where:
 * - **Line 1** is the session header (id, startTime, endTime).
 * - **Lines 2+** are per-minute RMSSD snapshots.
 *
 * This adapter implements [HrvSessionRepositoryPort] so that the application
 * and domain layers remain decoupled from the file system.
 *
 * @param baseDir Root directory where session files are stored.
 */
class HrvSessionFileAdapter(
    private val baseDir: File
) : HrvSessionRepositoryPort {

    private val json = Json { ignoreUnknownKeys = true }

    private val dir: File
        get() = baseDir.resolve("hrv_sessions").also { it.mkdirs() }

    override suspend fun createSession(session: HrvSession): Unit = withContext(Dispatchers.IO) {
        val header = HeaderLine(
            type = "header",
            id = session.id,
            startTime = session.startTime,
            endTime = session.endTime
        )
        findFileBySessionId(session.id).writeText(json.encodeToString(header) + "\n")
    }

    override suspend fun appendSnapshot(sessionId: String, snapshot: HrvSnapshot): Unit =
        withContext(Dispatchers.IO) {
            val line = SnapshotLine(
                type = "snapshot",
                timestamp = snapshot.timestamp,
                rmssdMs = snapshot.rmssdMs,
                rrIntervalCount = snapshot.rrIntervalCount,
                windowDurationMs = snapshot.windowDurationMs
            )
            findFileBySessionId(sessionId).appendText(json.encodeToString(line) + "\n")
        }

    override suspend fun finaliseSession(session: HrvSession): Unit = withContext(Dispatchers.IO) {
        val file = findFileBySessionId(session.id)
        if (!file.exists()) return@withContext

        val lines = file.readLines().toMutableList()
        if (lines.isEmpty()) return@withContext

        // Replace the header line (first line) with the updated endTime
        val updatedHeader = HeaderLine(
            type = "header",
            id = session.id,
            startTime = session.startTime,
            endTime = session.endTime
        )
        lines[0] = json.encodeToString(updatedHeader)
        file.writeText(lines.joinToString("\n") + "\n")
    }

    override suspend fun findById(sessionId: String): HrvSession? = withContext(Dispatchers.IO) {
        val file = findFileBySessionId(sessionId)
        if (!file.exists()) return@withContext null
        parseFile(file)
    }

    override suspend fun loadAll(): List<HrvSession> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "jsonl" }
            ?.mapNotNull { parseFile(it) }
            ?.sortedByDescending { it.startTime }
            ?: emptyList()
    }

    // ── Internal ────────────────────────────────────────────────────

    private fun findFileBySessionId(sessionId: String): File {
        val existing = dir.listFiles { f -> f.name.contains(sessionId) }?.firstOrNull()
        return existing ?: dir.resolve("hrv_${sessionId}.jsonl")
    }

    private fun parseFile(file: File): HrvSession? {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val header = json.decodeFromString<HeaderLine>(lines.first())
        val snapshots = lines.drop(1).map { line ->
            val snapshotLine = json.decodeFromString<SnapshotLine>(line)
            HrvSnapshot(
                timestamp = snapshotLine.timestamp,
                rmssdMs = snapshotLine.rmssdMs,
                rrIntervalCount = snapshotLine.rrIntervalCount,
                windowDurationMs = snapshotLine.windowDurationMs
            )
        }

        return HrvSession(
            id = header.id,
            startTime = header.startTime,
            endTime = header.endTime,
            snapshots = snapshots
        )
    }

    // ── JSONL line DTOs (framework-internal, never leak to domain) ──

    @Serializable
    private data class HeaderLine(
        val type: String,
        val id: String,
        val startTime: Long,
        val endTime: Long? = null
    )

    @Serializable
    private data class SnapshotLine(
        val type: String,
        val timestamp: Long,
        val rmssdMs: Double,
        val rrIntervalCount: Int,
        val windowDurationMs: Long
    )
}

