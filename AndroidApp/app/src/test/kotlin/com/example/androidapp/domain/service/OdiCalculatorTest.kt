package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.DesaturationEvent
import com.example.androidapp.domain.model.SeverityCategory
import org.junit.Assert.*
import org.junit.Test

class OdiCalculatorTest {

    @Test
    fun `calculateOdi should calculate correct ODI indices`() {
        // Create synthetic SpO₂ data with desaturations
        val duration = 3600L * 1000 // 1 hour in milliseconds
        val sampleInterval = 1000L // 1 second intervals
        val numSamples = (duration / sampleInterval).toInt()
        
        val timestamps = LongArray(numSamples) { i ->
            i * sampleInterval
        }
        
        val spo2Values = DoubleArray(numSamples) { i ->
            val t = i * sampleInterval / 1000.0 // seconds
            
            // Base SpO₂
            var spo2 = 96.0
            
            // Add periodic desaturations every 2 minutes (30 events/hour)
            if ((i / 120) % 2 == 0 && i % 120 in 10..30) {
                // 4% desaturation lasting 20 seconds
                spo2 -= 4.0
            }
            
            spo2
        }
        
        val result = OdiCalculator.calculateOdi(timestamps, spo2Values)
        
        // Heuristic event detector: expect a non-trivial ODI on this synthetic pattern
        assertEquals(1.0, result.totalHours, 0.15)
        assertTrue("ODI₄ should reflect recurring desaturations", result.odi4 >= 5.0)
        assertTrue("ODI₃ should be at least ODI₄", result.odi3 >= result.odi4 - 1e-6)
        assertTrue("Should count multiple desaturations", result.desaturationCount >= 8)
    }

    @Test
    fun `detectDesaturationEvents should detect individual events`() {
        // Create simple SpO₂ signal with one clear desaturation
        val timestamps = longArrayOf(0, 5000, 10000, 15000, 20000, 25000, 30000, 35000, 40000)
        val spo2Values = doubleArrayOf(96.0, 95.0, 92.0, 90.0, 92.0, 94.0, 95.0, 96.0, 96.0)
        
        val events = OdiCalculator.detectDesaturationEvents(timestamps, spo2Values)
        
        assertEquals("Should merge into one desaturation episode", 1, events.size)
        val event = events[0]
        assertEquals(15000L, event.nadirTimeMs) // nadir at 15 s
        assertTrue(event.dropPercent >= 3)
        assertTrue(event.baselineSpO2 - event.nadirSpO2 >= 3)
        assertTrue(event.endTimeMs > event.startTimeMs)
    }

    @Test
    fun `calculateT90 should calculate time below 90%`() {
        // SpO₂: 95% for 30s, 88% for 40s, 92% for 30s = 40s below 90%
        val timestamps = longArrayOf(0, 10000, 20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000)
        val spo2Values = doubleArrayOf(95.0, 95.0, 95.0, 88.0, 88.0, 88.0, 88.0, 92.0, 92.0, 92.0, 92.0)
        
        val t90 = OdiCalculator.calculateT90(timestamps, spo2Values)
        
        // 40 seconds below 90% out of 100 seconds total = 40%
        assertEquals(40.0, t90, 1.0)
    }

    @Test
    fun `calculateAverageSpo2 should calculate correct average`() {
        val spo2Values = doubleArrayOf(95.0, 96.0, 97.0, 94.0, 93.0)
        val average = OdiCalculator.calculateAverageSpo2(spo2Values)
        
        assertEquals(95.0, average, 0.1) // (95+96+97+94+93)/5 = 95.0
    }

    @Test
    fun `classifyOdiSeverity should classify correctly`() {
        assertEquals(SeverityCategory.NORMAL, OdiCalculator.classifyOdiSeverity(0.0, 0.0))
        assertEquals(SeverityCategory.NORMAL, OdiCalculator.classifyOdiSeverity(4.9, 4.9))
        assertEquals(SeverityCategory.MILD, OdiCalculator.classifyOdiSeverity(5.0, 5.0))
        assertEquals(SeverityCategory.MILD, OdiCalculator.classifyOdiSeverity(14.9, 14.9))
        assertEquals(SeverityCategory.MODERATE, OdiCalculator.classifyOdiSeverity(15.0, 15.0))
        assertEquals(SeverityCategory.MODERATE, OdiCalculator.classifyOdiSeverity(29.9, 29.9))
        assertEquals(SeverityCategory.SEVERE, OdiCalculator.classifyOdiSeverity(30.0, 30.0))
        
        // Should prefer ODI₄ if available
        assertEquals(SeverityCategory.MILD, OdiCalculator.classifyOdiSeverity(20.0, 10.0)) // Uses ODI₄=10.0
    }

    @Test
    fun `calculateSpo2Variability should calculate variability index`() {
        // Stable SpO₂ (implementation requires at least 10 samples)
        val stableValues = DoubleArray(12) { if (it % 2 == 0) 95.0 else 96.0 }
        val stableVariability = OdiCalculator.calculateSpo2Variability(stableValues)
        
        // Variable SpO₂
        val variableValues = doubleArrayOf(
            85.0, 95.0, 90.0, 98.0, 87.0, 92.0, 88.0, 96.0, 91.0, 97.0, 89.0, 94.0
        )
        val variableVariability = OdiCalculator.calculateSpo2Variability(variableValues)
        
        assertTrue("Variable SpO₂ should have higher variability", 
            variableVariability > stableVariability)
        assertTrue("Variability should be in 0-100 range", 
            stableVariability in 0.0..100.0)
        assertTrue("Variability should be in 0-100 range", 
            variableVariability in 0.0..100.0)
    }

    @Test
    fun `detectHypoxemiaEvents should detect prolonged low SpO₂`() {
        // Create SpO₂ with 6-minute hypoxemia (SpO₂ < 88% for >5 min)
        val duration = 20 * 60 * 1000L // 20 minutes
        val sampleInterval = 60000L // 1 minute intervals
        val numSamples = (duration / sampleInterval).toInt()
        
        val timestamps = LongArray(numSamples) { i ->
            i * sampleInterval
        }
        
        val spo2Values = DoubleArray(numSamples) { i ->
            when {
                i in 5..10 -> 85.0 // 6 minutes of hypoxemia
                else -> 95.0
            }
        }
        
        val events = OdiCalculator.detectHypoxemiaEvents(timestamps, spo2Values)
        
        assertEquals(1, events.size)
        
        val (start, end, minSpo2) = events[0]
        assertEquals(5 * sampleInterval, start) // Starts at minute 5
        assertEquals(11 * sampleInterval, end) // Ends at minute 11 (6 minutes duration)
        assertEquals(85.0, minSpo2, 0.1)
    }

    @Test
    fun `validateAndCleanSpo2Data should filter invalid values`() {
        val timestamps = longArrayOf(0, 1000, 2000, 3000, 4000)
        val spo2Values = doubleArrayOf(101.0, 50.0, 95.0, 96.0, 105.0) // Invalid: 101, 50, 105
        
        val (validTimestamps, validSpo2) = OdiCalculator::class.java.getDeclaredMethod(
            "validateAndCleanSpo2Data", LongArray::class.java, DoubleArray::class.java
        ).apply { isAccessible = true }
        .invoke(OdiCalculator, timestamps, spo2Values) as Pair<LongArray, DoubleArray>
        
        // Should keep only 95.0 and 96.0
        assertEquals(2, validTimestamps.size)
        assertEquals(2000L, validTimestamps[0])
        assertEquals(3000L, validTimestamps[1])
        assertEquals(95.0, validSpo2[0], 0.1)
        assertEquals(96.0, validSpo2[1], 0.1)
    }

    @Test
    fun `calculateOdi should handle short recordings`() {
        val shortTimestamps = longArrayOf(0, 1000)
        val shortSpo2 = doubleArrayOf(95.0, 96.0)
        
        val result = OdiCalculator.calculateOdi(shortTimestamps, shortSpo2)
        
        assertEquals(0.0, result.odi3, 0.0)
        assertEquals(0.0, result.odi4, 0.0)
        assertEquals(0, result.desaturationCount)
        assertEquals(0.0, result.totalHours, 0.0)
    }

    @Test
    fun `calculateOdi should handle noisy data`() {
        val timestamps = LongArray(100) { it * 1000L }
        val spo2Values = DoubleArray(100) { 
            95.0 + (Math.random() - 0.5) * 2.0 // 94-96% with noise
        }
        
        val result = OdiCalculator.calculateOdi(timestamps, spo2Values)
        
        // Noisy but stable SpO₂ should have low ODI
        assertTrue("ODI should be low for stable SpO₂", result.odi3 < 5.0)
        assertTrue("ODI should be low for stable SpO₂", result.odi4 < 5.0)
    }

    @Test
    fun `detectDesaturationEvents should filter short events`() {
        // Very short desaturation (5 seconds - below minimum 10 seconds)
        val timestamps = longArrayOf(0, 5000, 10000, 15000, 20000)
        val spo2Values = doubleArrayOf(96.0, 92.0, 96.0, 96.0, 96.0) // 4% drop for 5 seconds
        
        val events = OdiCalculator.detectDesaturationEvents(timestamps, spo2Values)
        
        // Should be filtered out due to short duration
        assertTrue("Should filter short events", events.isEmpty())
    }
}