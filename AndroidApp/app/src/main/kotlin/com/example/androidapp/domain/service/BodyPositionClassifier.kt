package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.BodyPosition
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Body position classifier from accelerometer data.
 *
 * Uses DC (static) component of accelerometer signal to determine
 * body orientation relative to gravity.
 *
 * Based on: Najafi B, et al. "Ambulatory system for human motion analysis
 * using a kinematic sensor: monitoring of daily physical activity in the
 * elderly." IEEE Trans Biomed Eng. 2003;50(6):711-23.
 */
object BodyPositionClassifier {

    // Thresholds for position classification (in g units)
    private const val SUPINE_THRESHOLD = 0.8
    private const val LATERAL_THRESHOLD = 0.6
    private const val UPRIGHT_THRESHOLD = 0.8
    
    // Smoothing window for stable position detection
    private const val SMOOTHING_WINDOW_MS = 5000L // 5 seconds
    
    // Minimum samples for reliable classification
    /** Minimum samples required for smoothed classification (short buffers use per-sample g). */
    private const val MIN_SAMPLES = 2

    /**
     * Classifies body position from accelerometer samples.
     *
     * @param timestamps Sample timestamps in milliseconds
     * @param xValues X-axis accelerometer values in m/s²
     * @param yValues Y-axis accelerometer values in m/s²  
     * @param zValues Z-axis accelerometer values in m/s²
     * @return Array of classified body positions for each sample
     */
    fun classifyPositions(
        timestamps: LongArray,
        xValues: DoubleArray,
        yValues: DoubleArray,
        zValues: DoubleArray
    ): Array<BodyPosition> {
        require(timestamps.size == xValues.size && 
                timestamps.size == yValues.size && 
                timestamps.size == zValues.size) {
            "All input arrays must have same size"
        }
        
        if (timestamps.size < MIN_SAMPLES) {
            return Array(timestamps.size) { BodyPosition.UNKNOWN }
        }

        // 1. Normalize accelerometer values to g units
        val normalized = normalizeToGravity(xValues, yValues, zValues)
        
        // 2. Apply smoothing for stable position detection
        val smoothed = smoothAccelerometerData(timestamps, normalized)
        
        // 3. Classify each sample
        return smoothed.map { (x, y, z) ->
            classifySinglePosition(x, y, z)
        }.toTypedArray()
    }

    /**
     * Detects position changes (transitions between positions).
     *
     * @param positions Array of classified positions
     * @param minDurationMs Minimum duration to consider stable position
     * @return List of (startTime, endTime, position) for each position segment
     */
    fun detectPositionChanges(
        timestamps: LongArray,
        positions: Array<BodyPosition>,
        minDurationMs: Long = 30000L // 30 seconds minimum
    ): List<Triple<Long, Long, BodyPosition>> {
        if (timestamps.isEmpty() || positions.isEmpty()) {
            return emptyList()
        }
        
        val segments = mutableListOf<Triple<Long, Long, BodyPosition>>()
        var currentPosition = positions.first()
        var segmentStart = timestamps.first()
        var segmentStartIndex = 0
        
        for (i in 1 until positions.size) {
            if (positions[i] != currentPosition) {
                val segmentDuration = timestamps[i] - segmentStart
                
                if (segmentDuration >= minDurationMs) {
                    segments.add(Triple(segmentStart, timestamps[i], currentPosition))
                }
                
                currentPosition = positions[i]
                segmentStart = timestamps[i]
                segmentStartIndex = i
            }
        }
        
        // Add final segment
        val finalDuration = timestamps.last() - segmentStart
        if (finalDuration >= minDurationMs) {
            segments.add(Triple(segmentStart, timestamps.last(), currentPosition))
        }
        
        return segments
    }

    /**
     * Calculates percentage time in each position.
     *
     * @param timestamps Sample timestamps
     * @param positions Classified positions
     * @return Map of position to percentage time (0-100)
     */
    fun calculatePositionPercentages(
        timestamps: LongArray,
        positions: Array<BodyPosition>
    ): Map<BodyPosition, Double> {
        if (timestamps.size < 2 || timestamps.size != positions.size) {
            return emptyMap()
        }
        
        val totalDuration = timestamps.last() - timestamps.first()
        if (totalDuration <= 0) {
            return emptyMap()
        }
        
        val timeInPosition = mutableMapOf<BodyPosition, Long>()
        
        // Initialize all positions with zero time
        BodyPosition.values().forEach { position ->
            timeInPosition[position] = 0L
        }
        
        // Time between timestamps[i-1] and timestamps[i] is attributed to positions[i-1]
        for (i in 1 until timestamps.size) {
            val duration = timestamps[i] - timestamps[i - 1]
            val position = positions[i - 1]
            timeInPosition[position] = timeInPosition[position]!! + duration
        }
        
        // Convert to percentages
        return timeInPosition.mapValues { (_, time) ->
            (time * 100.0) / totalDuration
        }
    }

    // =========================================================================
    // Processing Steps
    // =========================================================================

    private data class NormalizedAccel(val x: Double, val y: Double, val z: Double)

    private fun normalizeToGravity(
        xValues: DoubleArray,
        yValues: DoubleArray,
        zValues: DoubleArray
    ): List<NormalizedAccel> {
        return xValues.indices.map { i ->
            val magnitude = sqrt(
                xValues[i] * xValues[i] +
                    yValues[i] * yValues[i] +
                    zValues[i] * zValues[i]
            )
            // Direction cosines in g (each axis projection onto gravity unit vector, magnitude ≤ 1)
            if (magnitude <= 1e-9) {
                NormalizedAccel(0.0, 0.0, 0.0)
            } else {
                NormalizedAccel(
                    x = xValues[i] / magnitude,
                    y = yValues[i] / magnitude,
                    z = zValues[i] / magnitude
                )
            }
        }
    }

    private fun smoothAccelerometerData(
        timestamps: LongArray,
        normalizedData: List<NormalizedAccel>
    ): List<NormalizedAccel> {
        return normalizedData.mapIndexed { i, data ->
            val windowStart = timestamps[i] - SMOOTHING_WINDOW_MS / 2
            val windowEnd = timestamps[i] + SMOOTHING_WINDOW_MS / 2
            
            val samplesInWindow = normalizedData.filterIndexed { j, _ ->
                timestamps[j] in windowStart..windowEnd
            }
            
            if (samplesInWindow.isEmpty()) {
                data
            } else {
                NormalizedAccel(
                    x = samplesInWindow.map { it.x }.average(),
                    y = samplesInWindow.map { it.y }.average(),
                    z = samplesInWindow.map { it.z }.average()
                )
            }
        }
    }

    private fun classifySinglePosition(x: Double, y: Double, z: Double): BodyPosition {
        // Calculate angles relative to gravity
        val roll = atan2(y, sqrt(x * x + z * z)) * 180 / Math.PI
        val pitch = atan2(x, sqrt(y * y + z * z)) * 180 / Math.PI
        
        // Check which axis is most aligned with gravity
        val absX = Math.abs(x)
        val absY = Math.abs(y)
        val absZ = Math.abs(z)
        
        return when {
            // Supine (lying on back) - Z axis points up/down
            absZ > SUPINE_THRESHOLD && z > 0 -> BodyPosition.SUPINE
            absZ > SUPINE_THRESHOLD && z < 0 -> BodyPosition.PRONE
            
            // Lateral positions - X or Y axis aligned with gravity
            absX > LATERAL_THRESHOLD && x > 0 -> BodyPosition.RIGHT_LATERAL
            absX > LATERAL_THRESHOLD && x < 0 -> BodyPosition.LEFT_LATERAL
            absY > LATERAL_THRESHOLD && y > 0 -> {
                // Need to check roll angle for precise lateral classification
                if (roll > 45) BodyPosition.RIGHT_LATERAL
                else if (roll < -45) BodyPosition.LEFT_LATERAL
                else BodyPosition.UNKNOWN
            }
            
            // Upright - no single axis strongly aligned with gravity
            absZ < UPRIGHT_THRESHOLD && absX < UPRIGHT_THRESHOLD && absY < UPRIGHT_THRESHOLD -> {
                if (pitch > 30) BodyPosition.UPRIGHT
                else BodyPosition.UNKNOWN
            }
            
            else -> BodyPosition.UNKNOWN
        }
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    /**
     * Detects restless sleep based on position changes.
     *
     * @param positionSegments Detected position segments
     * @param maxChangesPerHour Threshold for restless sleep
     * @return True if sleep is restless (excessive position changes)
     */
    fun isRestlessSleep(
        positionSegments: List<Triple<Long, Long, BodyPosition>>,
        maxChangesPerHour: Int = 10
    ): Boolean {
        if (positionSegments.size < 2) {
            return false
        }
        
        val totalDuration = positionSegments.last().second - positionSegments.first().first
        val hours = totalDuration / (1000.0 * 60 * 60)
        
        if (hours <= 0) {
            return false
        }
        
        val transitionCount = positionSegments.size - 1
        val changesPerHour = transitionCount / hours
        return changesPerHour > maxChangesPerHour
    }

    /**
     * Calculates sleep efficiency based on position changes.
     * Assumes supine position is optimal for sleep apnea screening.
     *
     * @param positionPercentages Percentage time in each position
     * @return Sleep efficiency score (0-100)
     */
    fun calculateSleepEfficiency(positionPercentages: Map<BodyPosition, Double>): Double {
        val supinePercentage = positionPercentages[BodyPosition.SUPINE] ?: 0.0
        val leftLateral = positionPercentages[BodyPosition.LEFT_LATERAL] ?: 0.0
        val rightLateral = positionPercentages[BodyPosition.RIGHT_LATERAL] ?: 0.0
        val uprightPercentage = positionPercentages[BodyPosition.UPRIGHT] ?: 0.0
        val pronePercentage = positionPercentages[BodyPosition.PRONE] ?: 0.0
        val unknownPercentage = positionPercentages[BodyPosition.UNKNOWN] ?: 0.0

        return supinePercentage * 1.0 +
            leftLateral * 0.8 +
            rightLateral * 0.8 +
            pronePercentage * 0.7 +
            uprightPercentage * 0.3 +
            unknownPercentage * 0.5
    }

    /**
     * Detects if patient is predominantly in supine position
     * (most relevant for obstructive sleep apnea).
     *
     * @param positionPercentages Percentage time in each position
     * @param threshold Minimum percentage for "predominant" (default 50%)
     * @return True if supine position is predominant
     */
    fun isPredominantlySupine(
        positionPercentages: Map<BodyPosition, Double>,
        threshold: Double = 50.0
    ): Boolean {
        val supinePercentage = positionPercentages[BodyPosition.SUPINE] ?: 0.0
        return supinePercentage >= threshold
    }

    /**
     * Estimates sleep position severity for apnea risk.
     * Supine position carries highest risk for OSA.
     *
     * @param positionPercentages Percentage time in each position
     * @return Position severity score (0-100, higher = more risk)
     */
    fun calculatePositionSeverityScore(positionPercentages: Map<BodyPosition, Double>): Double {
        val weights = mapOf(
            BodyPosition.SUPINE to 1.0,        // Highest risk
            BodyPosition.PRONE to 0.7,         // Moderate risk
            BodyPosition.UPRIGHT to 0.3,       // Low risk (not really sleeping)
            BodyPosition.LEFT_LATERAL to 0.2,  // Lower risk
            BodyPosition.RIGHT_LATERAL to 0.2, // Lower risk
            BodyPosition.UNKNOWN to 0.5        // Medium risk (uncertain)
        )
        
        return weights.entries.sumOf { (position, weight) ->
            (positionPercentages[position] ?: 0.0) * weight
        }
    }
}