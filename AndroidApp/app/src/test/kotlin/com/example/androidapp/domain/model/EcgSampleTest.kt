package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EcgSampleTest {

    @Test
    fun `creates EcgSample with valid values`() {
        val sample = EcgSample(timestamp = 1000L, voltageUv = 1500)
        assertEquals(1000L, sample.timestamp)
        assertEquals(1500, sample.voltageUv)
    }

    @Test
    fun `supports negative voltage values`() {
        val sample = EcgSample(timestamp = 2000L, voltageUv = -200)
        assertEquals(-200, sample.voltageUv)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = EcgSample(timestamp = 100L, voltageUv = 500)
        val b = EcgSample(timestamp = 100L, voltageUv = 500)
        assertEquals(a, b)
    }

    @Test
    fun `copy modifies selected fields`() {
        val original = EcgSample(timestamp = 100L, voltageUv = 500)
        val modified = original.copy(voltageUv = 1000)
        assertEquals(100L, modified.timestamp)
        assertEquals(1000, modified.voltageUv)
    }
}

