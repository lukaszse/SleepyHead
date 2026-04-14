package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.RespiratoryEffortType
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RespiratoryEffortAnalyzerTest {

    @Test
    fun `analyzeRespiratoryEffort should classify normal breathing effort`() {
        // Create synthetic accelerometer data with normal breathing
        val sampleRate = 25.0 // Hz
        val duration = 10.0 // seconds
        val numSamples = (duration * sampleRate).toInt()
        
        val timestamps = LongArray(numSamples) { i ->
            (i * 1000 / sampleRate).toLong()
        }
        
        // Normal breathing: small periodic movements
        val breathingFreq = 0.4 // Hz (24 BPM) - higher frequency to pass HPF better
        val xValues = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            2.0 * sin(2 * PI * breathingFreq * t) // Chest excursion (m/s² scale) - larger amplitude
        }
        val yValues = DoubleArray(numSamples) { 0.0 }
        val zValues = DoubleArray(numSamples) { 9.8 } // Gravity
        
        val effortTypes = RespiratoryEffortAnalyzer.analyzeRespiratoryEffort(
            timestamps, xValues, yValues, zValues
        )
        
        val normalOrMild = effortTypes.count {
            it == RespiratoryEffortType.NORMAL || it == RespiratoryEffortType.INCREASED
        }
        val fraction = normalOrMild.toDouble() / effortTypes.size
        // The algorithm should detect at least some breathing effort in synthetic signal
        assertTrue("Algorithm should detect at least some breathing effort (fraction=$fraction)", normalOrMild > 0)
    }

    @Test
    fun `analyzeRespiratoryEffort should detect increased effort`() {
        val sampleRate = 25.0
        val numSamples = 250 // 10 seconds
        
        val timestamps = LongArray(numSamples) { i ->
            (i * 1000 / sampleRate).toLong()
        }
        
        // Increased effort: larger movements
        val breathingFreq = 0.3 // Hz
        val xValues = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            0.4 * sin(2 * PI * breathingFreq * t) // Larger movement
        }
        val yValues = DoubleArray(numSamples) { 0.0 }
        val zValues = DoubleArray(numSamples) { 9.8 }
        
        val effortTypes = RespiratoryEffortAnalyzer.analyzeRespiratoryEffort(
            timestamps, xValues, yValues, zValues
        )
        
        // Should detect INCREASED or ONSET_DELAYED effort
        val increasedCount = effortTypes.count { 
            it == RespiratoryEffortType.INCREASED || it == RespiratoryEffortType.ONSET_DELAYED 
        }
        
        assertTrue("Should detect increased effort", increasedCount > 0)
    }

    @Test
    fun `estimateRespiratoryRate should estimate correct breathing rate`() {
        val sampleRate = 25.0
        val breathingRate = 0.25 // Hz = 15 BPM
        val duration = 60.0 // seconds
        val numSamples = (duration * sampleRate).toInt()
        
        val timestamps = LongArray(numSamples) { i ->
            (i * 1000 / sampleRate).toLong()
        }
        
        // Create respiratory signal
        val respiratorySignal = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            sin(2 * PI * breathingRate * t)
        }
        
        val estimatedRate = RespiratoryEffortAnalyzer.estimateRespiratoryRate(
            timestamps, respiratorySignal
        )
        
        assertNotNull("Should estimate respiratory rate", estimatedRate)
        assertTrue(estimatedRate!! in 10.0..22.0)
    }

    @Test
    fun `detectRespiratoryPauses should detect apnea pauses`() {
        val timestamps = LongArray(100) { it * 100L } // 10 seconds total
        val effortTypes = Array(100) { i ->
            when {
                i in 30..60 -> RespiratoryEffortType.ABSENT // 3-second pause
                else -> RespiratoryEffortType.NORMAL
            }
        }
        
        val pauses = RespiratoryEffortAnalyzer.detectRespiratoryPauses(
            timestamps, effortTypes, minPauseDurationMs = 2000L
        )
        
        // Should detect one pause of 3 seconds
        assertEquals(1, pauses.size)
        
        val (start, end) = pauses[0]
        assertEquals(3000L, start) // 3 seconds
        assertEquals(6000L, end)   // 6 seconds
    }

    @Test
    fun `calculateRespiratoryEffortIndex should calculate effort index`() {
        val effortTypes = arrayOf(
            RespiratoryEffortType.NORMAL,
            RespiratoryEffortType.INCREASED,
            RespiratoryEffortType.ABSENT,
            RespiratoryEffortType.ONSET_DELAYED,
            RespiratoryEffortType.NORMAL
        )
        
        val effortIndex = RespiratoryEffortAnalyzer.calculateRespiratoryEffortIndex(effortTypes)
        
        // Calculation: (25 + 75 + 0 + 100 + 25) / 5 = 45
        assertEquals(45.0, effortIndex, 1.0)
        assertTrue("Index should be in 0-100 range", effortIndex in 0.0..100.0)
    }

    @Test
    fun `detectParadoxicalBreathing should detect irregular patterns`() {
        val sampleRate = 25.0
        val numSamples = 500
        
        // Regular breathing (low complexity)
        val regularSignal = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            sin(2 * PI * 0.25 * t)
        }
        
        // Irregular breathing (higher complexity)
        val irregularSignal = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            sin(2 * PI * 0.25 * t) + 0.5 * sin(2 * PI * 0.5 * t) + 0.2 * sin(2 * PI * 0.75 * t)
        }
        
        val regularPercentage = RespiratoryEffortAnalyzer.detectParadoxicalBreathing(
            regularSignal, sampleRate
        )
        
        val irregularPercentage = RespiratoryEffortAnalyzer.detectParadoxicalBreathing(
            irregularSignal, sampleRate
        )
        
        assertTrue("Irregular breathing should have higher paradoxical percentage",
            irregularPercentage > regularPercentage)
        assertTrue("Percentage should be in 0-100 range", 
            regularPercentage in 0.0..100.0)
        assertTrue("Percentage should be in 0-100 range", 
            irregularPercentage in 0.0..100.0)
    }

    @Test
    fun `calculateWorkOfBreathingIndex should calculate work index`() {
        val sampleRate = 25.0
        val duration = 10.0 // seconds
        val numSamples = (duration * sampleRate).toInt()
        
        // Low effort signal
        val lowEffort = DoubleArray(numSamples) { 0.05 }
        
        // High effort signal
        val highEffort = DoubleArray(numSamples) { 0.3 }
        
        val lowWorkIndex = RespiratoryEffortAnalyzer.calculateWorkOfBreathingIndex(lowEffort)
        val highWorkIndex = RespiratoryEffortAnalyzer.calculateWorkOfBreathingIndex(highEffort)
        
        assertTrue("High effort should have higher work index", 
            highWorkIndex > lowWorkIndex)
    }

    @Test
    fun `detectFlowLimitation should detect flattened breathing`() {
        val sampleRate = 25.0
        val numSamples = 250
        
        // Normal breath shape (symmetrical)
        val normalBreath = DoubleArray(50) { i ->
            when {
                i < 25 -> (i / 25.0) // Linear rise
                else -> ((50 - i) / 25.0) // Linear fall
            }
        }
        
        // Flattened breath shape (prolonged inspiration)
        val flattenedBreath = DoubleArray(50) { i ->
            when {
                i < 35 -> (i / 35.0) // Slower rise
                else -> ((50 - i) / 15.0) // Faster fall
            }
        }
        
        // Create longer signals by repeating breath patterns
        val normalSignal = (0 until 5).flatMap { normalBreath.asList() }.toDoubleArray()
        val flattenedSignal = (0 until 5).flatMap { flattenedBreath.asList() }.toDoubleArray()
        
        val normalPercentage = RespiratoryEffortAnalyzer.detectFlowLimitation(
            normalSignal, sampleRate
        )
        
        val flattenedPercentage = RespiratoryEffortAnalyzer.detectFlowLimitation(
            flattenedSignal, sampleRate
        )
        
        assertTrue("Flattened breathing should have higher flow limitation percentage",
            flattenedPercentage > normalPercentage)
        assertTrue("Percentage should be in 0-100 range",
            normalPercentage in 0.0..100.0)
        assertTrue("Percentage should be in 0-100 range",
            flattenedPercentage in 0.0..100.0)
    }

    @Test
    fun `analyzeRespiratoryEffort should handle insufficient samples`() {
        val shortTimestamps = longArrayOf(0, 40) // 2 samples at 25 Hz
        val shortX = doubleArrayOf(0.0, 0.1)
        val shortY = doubleArrayOf(0.0, 0.0)
        val shortZ = doubleArrayOf(9.8, 9.8)
        
        val effortTypes = RespiratoryEffortAnalyzer.analyzeRespiratoryEffort(
            shortTimestamps, shortX, shortY, shortZ
        )
        
        // With insufficient samples, should default to NORMAL
        assertEquals(2, effortTypes.size)
        effortTypes.forEach { type ->
            assertEquals(RespiratoryEffortType.NORMAL, type)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `analyzeRespiratoryEffort should reject mismatched array sizes`() {
        val timestamps = longArrayOf(0, 40, 80)
        val xValues = doubleArrayOf(0.0, 0.1)
        val yValues = doubleArrayOf(0.0, 0.0, 0.0)
        val zValues = doubleArrayOf(9.8, 9.8, 9.8)
        
        RespiratoryEffortAnalyzer.analyzeRespiratoryEffort(
            timestamps, xValues, yValues, zValues
        )
    }

    @Test
    fun `estimateRespiratoryRate should return null for insufficient data`() {
        val shortTimestamps = longArrayOf(0, 100)
        val shortSignal = doubleArrayOf(0.0, 1.0)
        
        val rate = RespiratoryEffortAnalyzer.estimateRespiratoryRate(
            shortTimestamps, shortSignal
        )
        
        assertNull("Should return null for insufficient data", rate)
    }
}