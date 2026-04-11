package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DesaturationEventTest {

    @Test
    fun `creates DesaturationEvent with valid values`() {
        val event = DesaturationEvent(
            startTimeMs = 1000L,
            nadirTimeMs = 5000L,
            endTimeMs = 10000L,
            baselineSpO2 = 97,
            nadirSpO2 = 91,
            dropPercent = 6
        )
        assertEquals(1000L, event.startTimeMs)
        assertEquals(5000L, event.nadirTimeMs)
        assertEquals(10000L, event.endTimeMs)
        assertEquals(97, event.baselineSpO2)
        assertEquals(91, event.nadirSpO2)
        assertEquals(6, event.dropPercent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative dropPercent`() {
        DesaturationEvent(
            startTimeMs = 0L, nadirTimeMs = 5000L, endTimeMs = 10000L,
            baselineSpO2 = 90, nadirSpO2 = 95, dropPercent = -5
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects nadirSpO2 greater than baselineSpO2`() {
        DesaturationEvent(
            startTimeMs = 0L, nadirTimeMs = 5000L, endTimeMs = 10000L,
            baselineSpO2 = 90, nadirSpO2 = 95, dropPercent = 5
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects nadirTimeMs outside event window`() {
        DesaturationEvent(
            startTimeMs = 5000L, nadirTimeMs = 1000L, endTimeMs = 10000L,
            baselineSpO2 = 97, nadirSpO2 = 91, dropPercent = 6
        )
    }

    @Test
    fun `allows nadir at start boundary`() {
        val event = DesaturationEvent(
            startTimeMs = 1000L, nadirTimeMs = 1000L, endTimeMs = 5000L,
            baselineSpO2 = 96, nadirSpO2 = 93, dropPercent = 3
        )
        assertEquals(1000L, event.nadirTimeMs)
    }

    @Test
    fun `allows nadir at end boundary`() {
        val event = DesaturationEvent(
            startTimeMs = 1000L, nadirTimeMs = 5000L, endTimeMs = 5000L,
            baselineSpO2 = 96, nadirSpO2 = 93, dropPercent = 3
        )
        assertEquals(5000L, event.nadirTimeMs)
    }
}

