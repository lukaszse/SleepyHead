package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpO2SampleTest {

    @Test
    fun `creates SpO2Sample with all fields`() {
        val sample = SpO2Sample(
            timestamp = 1000L,
            spo2Percent = 97,
            pulseRate = 72,
            perfusionIndex = 5.2
        )
        assertEquals(1000L, sample.timestamp)
        assertEquals(97, sample.spo2Percent)
        assertEquals(72, sample.pulseRate)
        assertEquals(5.2, sample.perfusionIndex!!, 0.001)
    }

    @Test
    fun `perfusionIndex defaults to null`() {
        val sample = SpO2Sample(timestamp = 1000L, spo2Percent = 95, pulseRate = 60)
        assertNull(sample.perfusionIndex)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = SpO2Sample(timestamp = 1L, spo2Percent = 98, pulseRate = 65)
        val b = SpO2Sample(timestamp = 1L, spo2Percent = 98, pulseRate = 65)
        assertEquals(a, b)
    }

    @Test
    fun `copy modifies selected fields`() {
        val original = SpO2Sample(timestamp = 1L, spo2Percent = 98, pulseRate = 65)
        val modified = original.copy(spo2Percent = 92)
        assertEquals(92, modified.spo2Percent)
        assertEquals(65, modified.pulseRate)
    }
}

