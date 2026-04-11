package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccSampleTest {

    @Test
    fun `creates AccSample with valid values`() {
        val sample = AccSample(timestamp = 1000L, xMg = 10, yMg = -20, zMg = 980)
        assertEquals(1000L, sample.timestamp)
        assertEquals(10, sample.xMg)
        assertEquals(-20, sample.yMg)
        assertEquals(980, sample.zMg)
    }

    @Test
    fun `supports negative acceleration values`() {
        val sample = AccSample(timestamp = 0L, xMg = -500, yMg = -1000, zMg = -100)
        assertEquals(-500, sample.xMg)
        assertEquals(-1000, sample.yMg)
        assertEquals(-100, sample.zMg)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = AccSample(timestamp = 1L, xMg = 0, yMg = 0, zMg = 1000)
        val b = AccSample(timestamp = 1L, xMg = 0, yMg = 0, zMg = 1000)
        assertEquals(a, b)
    }

    @Test
    fun `copy modifies selected fields`() {
        val original = AccSample(timestamp = 1L, xMg = 0, yMg = 0, zMg = 1000)
        val modified = original.copy(zMg = -1000)
        assertEquals(0, modified.xMg)
        assertEquals(-1000, modified.zMg)
    }
}

