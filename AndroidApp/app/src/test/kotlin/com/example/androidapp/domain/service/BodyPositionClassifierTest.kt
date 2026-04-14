package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.BodyPosition
import org.junit.Assert.*
import org.junit.Test

class BodyPositionClassifierTest {

    @Test
    fun `classifyPositions should classify supine position`() {
        // Supine: Z axis points up (+9.8 m/s²), X and Y near 0
        val timestamps = longArrayOf(0, 1000, 2000, 3000, 4000)
        val xValues = doubleArrayOf(0.1, 0.2, -0.1, 0.3, 0.0)
        val yValues = doubleArrayOf(-0.2, 0.1, 0.0, -0.1, 0.2)
        val zValues = doubleArrayOf(9.8, 9.7, 9.8, 9.6, 9.8)
        
        val positions = BodyPositionClassifier.classifyPositions(
            timestamps, xValues, yValues, zValues
        )
        
        // All samples should be classified as SUPINE
        positions.forEach { position ->
            assertEquals(BodyPosition.SUPINE, position)
        }
    }

    @Test
    fun `classifyPositions should classify prone position`() {
        // Prone: Z axis points down (-9.8 m/s²)
        val timestamps = longArrayOf(0, 1000, 2000)
        val xValues = doubleArrayOf(0.0, 0.1, -0.1)
        val yValues = doubleArrayOf(0.0, -0.1, 0.1)
        val zValues = doubleArrayOf(-9.8, -9.7, -9.8)
        
        val positions = BodyPositionClassifier.classifyPositions(
            timestamps, xValues, yValues, zValues
        )
        
        positions.forEach { position ->
            assertEquals(BodyPosition.PRONE, position)
        }
    }

    @Test
    fun `classifyPositions should classify lateral positions`() {
        // Left lateral: X axis points left (-9.8 m/s²)
        val timestamps = longArrayOf(0, 1000)
        val xValues = doubleArrayOf(-9.8, -9.7)
        val yValues = doubleArrayOf(0.1, -0.1)
        val zValues = doubleArrayOf(0.2, 0.1)
        
        val positions = BodyPositionClassifier.classifyPositions(
            timestamps, xValues, yValues, zValues
        )
        
        positions.forEach { position ->
            assertEquals(BodyPosition.LEFT_LATERAL, position)
        }
        
        // Right lateral: X axis points right (+9.8 m/s²)
        val xValuesRight = doubleArrayOf(9.8, 9.7)
        val positionsRight = BodyPositionClassifier.classifyPositions(
            timestamps, xValuesRight, yValues, zValues
        )
        
        positionsRight.forEach { position ->
            assertEquals(BodyPosition.RIGHT_LATERAL, position)
        }
    }

    @Test
    fun `classifyPositions should classify upright position`() {
        // Upright: No single axis strongly aligned with gravity
        val timestamps = longArrayOf(0, 1000, 2000)
        val xValues = doubleArrayOf(2.0, 1.8, 2.1)
        val yValues = doubleArrayOf(2.0, 1.9, 2.2)
        val zValues = doubleArrayOf(2.0, 1.7, 2.3)
        
        val positions = BodyPositionClassifier.classifyPositions(
            timestamps, xValues, yValues, zValues
        )
        
        // Should classify as UPRIGHT or UNKNOWN depending on exact values
        positions.forEach { position ->
            assertTrue(position == BodyPosition.UPRIGHT || position == BodyPosition.UNKNOWN)
        }
    }

    @Test
    fun `detectPositionChanges should detect transitions`() {
        val timestamps = LongArray(10) { it * 10000L } // 0, 10s, 20s, ..., 90s
        val positions = arrayOf(
            BodyPosition.SUPINE, BodyPosition.SUPINE, BodyPosition.SUPINE, // 0-30s supine
            BodyPosition.LEFT_LATERAL, BodyPosition.LEFT_LATERAL, // 30-50s left lateral
            BodyPosition.SUPINE, BodyPosition.SUPINE, BodyPosition.SUPINE, // 50-80s supine
            BodyPosition.RIGHT_LATERAL, BodyPosition.RIGHT_LATERAL // 80-100s right lateral
        )
        
        val segments = BodyPositionClassifier.detectPositionChanges(
            timestamps, positions, minDurationMs = 20000L // 20s minimum
        )
        
        // Should detect 3 segments: supine (30s), left lateral (20s), supine (30s)
        // Right lateral only 20s - exactly at threshold
        assertEquals(3, segments.size)
        
        assertEquals(0L, segments[0].first)
        assertEquals(30000L, segments[0].second)
        assertEquals(BodyPosition.SUPINE, segments[0].third)
        
        assertEquals(30000L, segments[1].first)
        assertEquals(50000L, segments[1].second)
        assertEquals(BodyPosition.LEFT_LATERAL, segments[1].third)
        
        assertEquals(50000L, segments[2].first)
        assertEquals(80000L, segments[2].second)
        assertEquals(BodyPosition.SUPINE, segments[2].third)
    }

    @Test
    fun `calculatePositionPercentages should calculate correct percentages`() {
        val timestamps = longArrayOf(0, 30000, 60000, 90000, 120000) // 0, 30s, 60s, 90s, 120s
        val positions = arrayOf(
            BodyPosition.SUPINE, // 0–30s
            BodyPosition.LEFT_LATERAL, // 30–60s
            BodyPosition.SUPINE, // 60–90s
            BodyPosition.RIGHT_LATERAL, // 90–120s start
            BodyPosition.RIGHT_LATERAL // end sample (same segment)
        )
        
        val percentages = BodyPositionClassifier.calculatePositionPercentages(
            timestamps, positions
        )
        
        assertEquals(50.0, percentages[BodyPosition.SUPINE]!!, 1.0)
        assertEquals(25.0, percentages[BodyPosition.LEFT_LATERAL]!!, 1.0)
        assertEquals(25.0, percentages[BodyPosition.RIGHT_LATERAL]!!, 1.0)
        assertEquals(0.0, percentages[BodyPosition.PRONE]!!, 0.1)
        assertEquals(0.0, percentages[BodyPosition.UPRIGHT]!!, 0.1)
        assertEquals(0.0, percentages[BodyPosition.UNKNOWN]!!, 0.1)
    }

    @Test
    fun `isRestlessSleep should detect excessive position changes`() {
        // Create many short position segments (restless sleep)
        val segments = listOf(
            Triple(0L, 60000L, BodyPosition.SUPINE),      // 1 min
            Triple(60000L, 120000L, BodyPosition.LEFT_LATERAL), // 1 min
            Triple(120000L, 180000L, BodyPosition.SUPINE),      // 1 min
            Triple(180000L, 240000L, BodyPosition.RIGHT_LATERAL), // 1 min
            Triple(240000L, 300000L, BodyPosition.SUPINE),      // 1 min
            Triple(300000L, 360000L, BodyPosition.LEFT_LATERAL)  // 1 min
        )
        
        // 6 changes in 6 minutes = 60 changes per hour
        val isRestless = BodyPositionClassifier.isRestlessSleep(
            segments, maxChangesPerHour = 10
        )
        
        assertTrue("Should detect restless sleep", isRestless)
        
        // Fewer changes should not be restless
        val fewSegments = segments.take(1)
        val isNotRestless = BodyPositionClassifier.isRestlessSleep(
            fewSegments, maxChangesPerHour = 10
        )
        
        assertFalse("Should not detect restless sleep with few changes", isNotRestless)
    }

    @Test
    fun `calculateSleepEfficiency should calculate efficiency score`() {
        val percentages = mapOf(
            BodyPosition.SUPINE to 60.0,
            BodyPosition.LEFT_LATERAL to 20.0,
            BodyPosition.RIGHT_LATERAL to 10.0,
            BodyPosition.PRONE to 5.0,
            BodyPosition.UPRIGHT to 5.0,
            BodyPosition.UNKNOWN to 0.0
        )
        
        val efficiency = BodyPositionClassifier.calculateSleepEfficiency(percentages)
        
        // Calculation: 60*1.0 + 20*0.8 + 10*0.8 + 5*0.7 + 5*0.3 = 60 + 16 + 8 + 3.5 + 1.5 = 89
        assertEquals(89.0, efficiency, 1.0)
    }

    @Test
    fun `isPredominantlySupine should check supine percentage`() {
        val predominantlySupine = mapOf(BodyPosition.SUPINE to 60.0)
        val notPredominantlySupine = mapOf(BodyPosition.SUPINE to 40.0)
        
        assertTrue(
            "Should be predominantly supine",
            BodyPositionClassifier.isPredominantlySupine(predominantlySupine, 50.0)
        )
        
        assertFalse(
            "Should not be predominantly supine",
            BodyPositionClassifier.isPredominantlySupine(notPredominantlySupine, 50.0)
        )
    }

    @Test
    fun `calculatePositionSeverityScore should calculate risk score`() {
        val highRiskPercentages = mapOf(BodyPosition.SUPINE to 80.0)
        val lowRiskPercentages = mapOf(BodyPosition.LEFT_LATERAL to 80.0)
        
        val highRiskScore = BodyPositionClassifier.calculatePositionSeverityScore(highRiskPercentages)
        val lowRiskScore = BodyPositionClassifier.calculatePositionSeverityScore(lowRiskPercentages)
        
        assertTrue("Supine position should have higher risk score", 
            highRiskScore > lowRiskScore)
        
        // Scores should be in 0-100 range
        assertTrue(highRiskScore in 0.0..100.0)
        assertTrue(lowRiskScore in 0.0..100.0)
    }

    @Test
    fun `classifyPositions should handle insufficient samples`() {
        val shortTimestamps = longArrayOf(0)
        val shortX = doubleArrayOf(0.0)
        val shortY = doubleArrayOf(0.0)
        val shortZ = doubleArrayOf(9.8)
        
        val positions = BodyPositionClassifier.classifyPositions(
            shortTimestamps, shortX, shortY, shortZ
        )
        
        // With insufficient samples, should return UNKNOWN
        assertEquals(1, positions.size)
        assertEquals(BodyPosition.UNKNOWN, positions[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `classifyPositions should reject mismatched array sizes`() {
        val timestamps = longArrayOf(0, 1000)
        val xValues = doubleArrayOf(0.0)
        val yValues = doubleArrayOf(0.0, 0.0)
        val zValues = doubleArrayOf(9.8, 9.8)
        
        BodyPositionClassifier.classifyPositions(timestamps, xValues, yValues, zValues)
    }
}