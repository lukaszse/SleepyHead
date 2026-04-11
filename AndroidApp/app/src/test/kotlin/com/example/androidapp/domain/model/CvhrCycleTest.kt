package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CvhrCycleTest {

    @Test
    fun `creates CvhrCycle with valid values`() {
        val cycle = CvhrCycle(
            startTimeMs = 1000L,
            endTimeMs = 61000L,
            minHr = 55.0,
            maxHr = 78.0,
            deltaHr = 23.0,
            periodMs = 60000L
        )
        assertEquals(1000L, cycle.startTimeMs)
        assertEquals(61000L, cycle.endTimeMs)
        assertEquals(55.0, cycle.minHr, 0.001)
        assertEquals(78.0, cycle.maxHr, 0.001)
        assertEquals(23.0, cycle.deltaHr, 0.001)
        assertEquals(60000L, cycle.periodMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative deltaHr`() {
        CvhrCycle(
            startTimeMs = 1000L, endTimeMs = 61000L,
            minHr = 78.0, maxHr = 55.0, deltaHr = -23.0, periodMs = 60000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxHr less than minHr`() {
        CvhrCycle(
            startTimeMs = 1000L, endTimeMs = 61000L,
            minHr = 80.0, maxHr = 60.0, deltaHr = 0.0, periodMs = 60000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects endTimeMs before startTimeMs`() {
        CvhrCycle(
            startTimeMs = 61000L, endTimeMs = 1000L,
            minHr = 55.0, maxHr = 78.0, deltaHr = 23.0, periodMs = 60000L
        )
    }

    @Test
    fun `allows zero deltaHr when min equals max`() {
        val cycle = CvhrCycle(
            startTimeMs = 0L, endTimeMs = 30000L,
            minHr = 70.0, maxHr = 70.0, deltaHr = 0.0, periodMs = 30000L
        )
        assertEquals(0.0, cycle.deltaHr, 0.001)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = CvhrCycle(0L, 60000L, 55.0, 78.0, 23.0, 60000L)
        val b = CvhrCycle(0L, 60000L, 55.0, 78.0, 23.0, 60000L)
        assertEquals(a, b)
    }
}

