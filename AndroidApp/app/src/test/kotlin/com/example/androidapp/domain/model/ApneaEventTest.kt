package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApneaEventTest {

    @Test
    fun `creates ApneaEvent with all fields`() {
        val event = ApneaEvent(
            startTimeMs = 1000L,
            endTimeMs = 25000L,
            type = ApneaType.OBSTRUCTIVE,
            confidence = Confidence.HIGH,
            spo2Nadir = 85
        )
        assertEquals(1000L, event.startTimeMs)
        assertEquals(25000L, event.endTimeMs)
        assertEquals(ApneaType.OBSTRUCTIVE, event.type)
        assertEquals(Confidence.HIGH, event.confidence)
        assertEquals(85, event.spo2Nadir)
    }

    @Test
    fun `spo2Nadir defaults to null`() {
        val event = ApneaEvent(
            startTimeMs = 0L, endTimeMs = 15000L,
            type = ApneaType.CENTRAL, confidence = Confidence.MEDIUM
        )
        assertNull(event.spo2Nadir)
    }

    @Test
    fun `durationMs returns correct value`() {
        val event = ApneaEvent(
            startTimeMs = 10000L, endTimeMs = 30000L,
            type = ApneaType.MIXED, confidence = Confidence.LOW
        )
        assertEquals(20000L, event.durationMs)
    }

    @Test
    fun `durationMs is zero when start equals end`() {
        val event = ApneaEvent(
            startTimeMs = 5000L, endTimeMs = 5000L,
            type = ApneaType.OBSTRUCTIVE, confidence = Confidence.LOW
        )
        assertEquals(0L, event.durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects endTimeMs before startTimeMs`() {
        ApneaEvent(
            startTimeMs = 30000L, endTimeMs = 10000L,
            type = ApneaType.OBSTRUCTIVE, confidence = Confidence.HIGH
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects spo2Nadir above 100`() {
        ApneaEvent(
            startTimeMs = 0L, endTimeMs = 15000L,
            type = ApneaType.OBSTRUCTIVE, confidence = Confidence.HIGH,
            spo2Nadir = 101
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects spo2Nadir below 0`() {
        ApneaEvent(
            startTimeMs = 0L, endTimeMs = 15000L,
            type = ApneaType.OBSTRUCTIVE, confidence = Confidence.HIGH,
            spo2Nadir = -1
        )
    }

    @Test
    fun `allows spo2Nadir at boundary values`() {
        val low = ApneaEvent(0L, 15000L, ApneaType.OBSTRUCTIVE, Confidence.HIGH, spo2Nadir = 0)
        assertEquals(0, low.spo2Nadir)

        val high = ApneaEvent(0L, 15000L, ApneaType.OBSTRUCTIVE, Confidence.HIGH, spo2Nadir = 100)
        assertEquals(100, high.spo2Nadir)
    }
}

