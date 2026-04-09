package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.domain.model.HrvSession
import com.example.androidapp.domain.model.HrvSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StopHrvSessionInputPortTest {

    private val repository = mockk<HrvSessionRepositoryPort>(relaxed = true)
    private val inputPort = StopHrvSessionInputPort(repository)

    private val activeSession = HrvSession(
        id = "session-123",
        startTime = 1_000_000L,
        endTime = null,
        snapshots = listOf(
            HrvSnapshot(timestamp = 1_300_000L, rmssdMs = 42.0, rrIntervalCount = 300, windowDurationMs = 300_000L)
        )
    )

    @Test
    fun `invoke sets endTime on the session`() = runTest {
        coEvery { repository.findById("session-123") } returns activeSession

        val result = inputPort("session-123")

        assertNotNull(result.endTime)
    }

    @Test
    fun `invoke preserves session id and startTime`() = runTest {
        coEvery { repository.findById("session-123") } returns activeSession

        val result = inputPort("session-123")

        assertEquals("session-123", result.id)
        assertEquals(1_000_000L, result.startTime)
    }

    @Test
    fun `invoke preserves existing snapshots`() = runTest {
        coEvery { repository.findById("session-123") } returns activeSession

        val result = inputPort("session-123")

        assertEquals(1, result.snapshots.size)
        assertEquals(42.0, result.snapshots.first().rmssdMs, 0.001)
    }

    @Test
    fun `invoke delegates to repository finaliseSession`() = runTest {
        coEvery { repository.findById("session-123") } returns activeSession
        val sessionSlot = slot<HrvSession>()
        coEvery { repository.finaliseSession(capture(sessionSlot)) } returns Unit

        inputPort("session-123")

        coVerify(exactly = 1) { repository.finaliseSession(any()) }
        assertNotNull(sessionSlot.captured.endTime)
        assertEquals("session-123", sessionSlot.captured.id)
    }

    @Test
    fun `invoke sets endTime close to current time`() = runTest {
        coEvery { repository.findById("session-123") } returns activeSession

        val before = System.currentTimeMillis()
        val result = inputPort("session-123")
        val after = System.currentTimeMillis()

        assert(result.endTime!! in before..after)
    }

    @Test
    fun `invoke throws NoSuchElementException when session not found`() = runTest {
        coEvery { repository.findById("missing") } returns null

        try {
            inputPort("missing")
            assert(false) { "Expected NoSuchElementException" }
        } catch (e: NoSuchElementException) {
            assertEquals("Session not found: missing", e.message)
        }
    }
}

