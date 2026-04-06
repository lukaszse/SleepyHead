package com.example.androidapp.application.port.input

import com.example.androidapp.application.port.output.HrvSessionRepositoryPort
import com.example.androidapp.domain.model.HrvSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StartHrvSessionInputPortTest {

    private val repository = mockk<HrvSessionRepositoryPort>(relaxed = true)
    private val inputPort = StartHrvSessionInputPort(repository)

    @Test
    fun `invoke creates session with UUID id`() = runTest {
        val session = inputPort()

        assertNotNull(session.id)
        assert(session.id.isNotBlank())
    }

    @Test
    fun `invoke creates session with current time as startTime`() = runTest {
        val before = System.currentTimeMillis()
        val session = inputPort()
        val after = System.currentTimeMillis()

        assert(session.startTime in before..after)
    }

    @Test
    fun `invoke creates session with null endTime`() = runTest {
        val session = inputPort()

        assertNull(session.endTime)
    }

    @Test
    fun `invoke creates session with empty snapshots`() = runTest {
        val session = inputPort()

        assertEquals(emptyList<Any>(), session.snapshots)
    }

    @Test
    fun `invoke delegates to repository createSession`() = runTest {
        val sessionSlot = slot<HrvSession>()
        coEvery { repository.createSession(capture(sessionSlot)) } returns Unit

        val returned = inputPort()

        coVerify(exactly = 1) { repository.createSession(any()) }
        assertEquals(returned.id, sessionSlot.captured.id)
        assertEquals(returned.startTime, sessionSlot.captured.startTime)
    }

    @Test
    fun `invoke generates unique IDs on consecutive calls`() = runTest {
        val session1 = inputPort()
        val session2 = inputPort()

        assert(session1.id != session2.id)
    }
}

