package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OdiResultTest {

    @Test
    fun `creates OdiResult with valid values`() {
        val result = OdiResult(odi3 = 15.5, odi4 = 10.2, desaturationCount = 62, totalHours = 4.0)
        assertEquals(15.5, result.odi3, 0.001)
        assertEquals(10.2, result.odi4, 0.001)
        assertEquals(62, result.desaturationCount)
        assertEquals(4.0, result.totalHours, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative odi3`() {
        OdiResult(odi3 = -1.0, odi4 = 0.0, desaturationCount = 0, totalHours = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative odi4`() {
        OdiResult(odi3 = 0.0, odi4 = -1.0, desaturationCount = 0, totalHours = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative desaturationCount`() {
        OdiResult(odi3 = 0.0, odi4 = 0.0, desaturationCount = -1, totalHours = 1.0)
    }

    @Test
    fun `allows zero totalHours for empty recording`() {
        val result = OdiResult(odi3 = 0.0, odi4 = 0.0, desaturationCount = 0, totalHours = 0.0)
        assertEquals(0.0, result.totalHours, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative totalHours`() {
        OdiResult(odi3 = 0.0, odi4 = 0.0, desaturationCount = 0, totalHours = -1.0)
    }

    @Test
    fun `allows zero ODI values`() {
        val result = OdiResult(odi3 = 0.0, odi4 = 0.0, desaturationCount = 0, totalHours = 8.0)
        assertEquals(0.0, result.odi3, 0.001)
    }
}

