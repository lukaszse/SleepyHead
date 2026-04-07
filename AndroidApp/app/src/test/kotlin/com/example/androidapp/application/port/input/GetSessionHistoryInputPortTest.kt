package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSessionHistoryInputPortTest {

    private val repository = mockk<HrvSessionRepositoryPort>(relaxed = true)
    private val inputPort = GetSessionHistoryInputPort(repository)

    @Test
    fun `invoke delegates to repository loadAll`() = runTest {
        inputPort()

        coVerify(exactly = 1) { repository.loadAll() }
    }

    @Test
    fun `invoke returns empty list when no sessions exist`() = runTest {
        coEvery { repository.loadAll() } returns emptyList()

        val result = inputPort()

        assertEquals(emptyList<HrvSession>(), result)
    }

    @Test
    fun `invoke returns all sessions from repository`() = runTest {
        val sessions = listOf(
            HrvSession(id = "s1", startTime = 2000L, endTime = 3000L),
            HrvSession(
                id = "s2",
                startTime = 1000L,
                endTime = 2000L,
                snapshots = listOf(
                    HrvSnapshot(timestamp = 1_500_000L, rmssdMs = 38.7, rrIntervalCount = 301, windowDurationMs = 300_000L)
                )
            )
        )
        coEvery { repository.loadAll() } returns sessions

        val result = inputPort()

        assertEquals(2, result.size)
        assertEquals("s1", result[0].id)
        assertEquals("s2", result[1].id)
    }

    @Test
    fun `invoke preserves snapshot data from repository`() = runTest {
        val snapshot = HrvSnapshot(
            timestamp = 1_500_000L, rmssdMs = 44.1, rrIntervalCount = 308, windowDurationMs = 300_000L
        )
        val session = HrvSession(id = "s1", startTime = 1000L, snapshots = listOf(snapshot))
        coEvery { repository.loadAll() } returns listOf(session)

        val result = inputPort()

        assertEquals(1, result.first().snapshots.size)
        assertEquals(44.1, result.first().snapshots.first().rmssdMs, 0.001)
    }
}

