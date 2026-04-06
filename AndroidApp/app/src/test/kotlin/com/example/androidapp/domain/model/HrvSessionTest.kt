package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrvSessionTest {

    @Test
    fun `averageRmssd returns null when snapshots are empty`() {
        val session = HrvSession(
            id = "test-id",
            startTime = 1000L,
            endTime = null,
            snapshots = emptyList()
        )
        assertNull(session.averageRmssd)
    }

    @Test
    fun `averageRmssd returns single value when one snapshot`() {
        val session = HrvSession(
            id = "test-id",
            startTime = 1000L,
            snapshots = listOf(
                HrvSnapshot(timestamp = 2000L, rmssdMs = 42.0, rrIntervalCount = 300, windowDurationMs = 300_000L)
            )
        )
        assertEquals(42.0, session.averageRmssd!!, 0.001)
    }

    @Test
    fun `averageRmssd computes mean of multiple snapshots`() {
        val session = HrvSession(
            id = "test-id",
            startTime = 1000L,
            snapshots = listOf(
                HrvSnapshot(timestamp = 2000L, rmssdMs = 40.0, rrIntervalCount = 300, windowDurationMs = 300_000L),
                HrvSnapshot(timestamp = 3000L, rmssdMs = 50.0, rrIntervalCount = 300, windowDurationMs = 300_000L),
                HrvSnapshot(timestamp = 4000L, rmssdMs = 60.0, rrIntervalCount = 300, windowDurationMs = 300_000L)
            )
        )
        assertEquals(50.0, session.averageRmssd!!, 0.001)
    }

    @Test
    fun `endTime defaults to null for active session`() {
        val session = HrvSession(id = "id", startTime = 1000L)
        assertNull(session.endTime)
    }

    @Test
    fun `snapshots default to empty list`() {
        val session = HrvSession(id = "id", startTime = 1000L)
        assertEquals(emptyList<HrvSnapshot>(), session.snapshots)
    }
}

