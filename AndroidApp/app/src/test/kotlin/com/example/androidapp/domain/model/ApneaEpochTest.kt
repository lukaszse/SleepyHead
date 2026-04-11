package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ApneaEpochTest {

    @Test
    fun `creates epoch with event detected`() {
        val event = ApneaEvent(
            startTimeMs = 1000L, endTimeMs = 15000L,
            type = ApneaType.OBSTRUCTIVE, confidence = Confidence.HIGH
        )
        val epoch = ApneaEpoch(
            epochStartMs = 0L,
            features = mapOf("cvhr_flag" to 1.0, "edr_cessation" to 1.0),
            eventDetected = true,
            apneaEvent = event,
            bodyPosition = BodyPosition.SUPINE
        )
        assertEquals(0L, epoch.epochStartMs)
        assertTrue(epoch.eventDetected)
        assertEquals(event, epoch.apneaEvent)
        assertEquals(BodyPosition.SUPINE, epoch.bodyPosition)
        assertEquals(2, epoch.features.size)
    }

    @Test
    fun `creates epoch without event`() {
        val epoch = ApneaEpoch(
            epochStartMs = 60000L,
            features = mapOf("cvhr_flag" to 0.0),
            eventDetected = false
        )
        assertFalse(epoch.eventDetected)
        assertNull(epoch.apneaEvent)
        assertEquals(BodyPosition.UNKNOWN, epoch.bodyPosition)
    }

    @Test
    fun `EPOCH_DURATION_MS is 60 seconds`() {
        assertEquals(60_000L, ApneaEpoch.EPOCH_DURATION_MS)
    }

    @Test
    fun `features map supports various keys`() {
        val features = mapOf(
            "cvhr_flag" to 1.0,
            "cvhr_delta_hr" to 18.5,
            "edr_cessation" to 0.0,
            "edr_amplitude" to 0.3,
            "acc_effort" to 1.0
        )
        val epoch = ApneaEpoch(
            epochStartMs = 0L,
            features = features,
            eventDetected = false
        )
        assertEquals(5, epoch.features.size)
        assertEquals(18.5, epoch.features["cvhr_delta_hr"]!!, 0.001)
    }
}

