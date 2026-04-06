package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.domain.model.HrvSnapshot
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordHrvSnapshotInputPortTest {

    private val repository = mockk<HrvSessionRepositoryPort>(relaxed = true)
    private val inputPort = RecordHrvSnapshotInputPort(repository)

    private val snapshot = HrvSnapshot(
        timestamp = 1_300_000L,
        rmssdMs = 42.3,
        rrIntervalCount = 312,
        windowDurationMs = 300_000L
    )

    @Test
    fun `invoke delegates to repository appendSnapshot`() = runTest {
        inputPort("session-123", snapshot)

        coVerify(exactly = 1) { repository.appendSnapshot("session-123", snapshot) }
    }

    @Test
    fun `invoke passes exact sessionId to repository`() = runTest {
        val customId = "custom-session-id"

        inputPort(customId, snapshot)

        coVerify { repository.appendSnapshot(customId, snapshot) }
    }

    @Test
    fun `invoke passes exact snapshot to repository`() = runTest {
        val customSnapshot = HrvSnapshot(
            timestamp = 9_999_999L,
            rmssdMs = 55.5,
            rrIntervalCount = 280,
            windowDurationMs = 300_000L
        )

        inputPort("session-123", customSnapshot)

        coVerify { repository.appendSnapshot("session-123", customSnapshot) }
    }
}

