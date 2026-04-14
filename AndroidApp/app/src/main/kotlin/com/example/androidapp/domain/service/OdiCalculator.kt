package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.DesaturationEvent
import com.example.androidapp.domain.model.OdiResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Oxygen Desaturation Index (ODI) calculator.
 *
 * Calculates ODI₃ and ODI₄ (≥3% and ≥4% desaturations per hour)
 * from SpO₂ time series data.
 *
 * Based on: American Academy of Sleep Medicine (AASM) scoring rules.
 */
object OdiCalculator {

    // Desaturation event parameters
    private const val MIN_DESATURATION_PERCENT_3 = 3.0
    private const val MIN_DESATURATION_PERCENT_4 = 4.0
    private const val MIN_EVENT_DURATION_MS = 10000L     // 10 seconds minimum
    private const val MAX_EVENT_DURATION_MS = 120000L    // 120 seconds maximum
    
    // Baseline calculation
    private const val BASELINE_WINDOW_MS = 120000L       // 2 minutes for baseline
    private const val MIN_BASELINE_SAMPLES = 3
    
    // Signal quality thresholds
    private const val MIN_VALID_SPO2 = 70.0
    private const val MAX_VALID_SPO2 = 100.0
    private const val MAX_SPO2_CHANGE_PER_SECOND = 5.0   // %/second

    /**
     * Calculates ODI from SpO₂ time series.
     *
     * @param timestamps SpO₂ measurement timestamps in milliseconds
     * @param spo2Values SpO₂ percentage values (70-100%)
     * @return ODI result containing ODI₃, ODI₄, and desaturation events
     */
    fun calculateOdi(
        timestamps: LongArray,
        spo2Values: DoubleArray
    ): OdiResult {
        if (timestamps.size < MIN_BASELINE_SAMPLES || spo2Values.size < MIN_BASELINE_SAMPLES) {
            return OdiResult(
                odi3 = 0.0,
                odi4 = 0.0,
                desaturationCount = 0,
                totalHours = 0.0
            )
        }

        // 1. Validate and clean SpO₂ data
        val (validTimestamps, validSpo2) = validateAndCleanSpo2Data(timestamps, spo2Values)
        
        if (validTimestamps.isEmpty()) {
            return OdiResult(
                odi3 = 0.0,
                odi4 = 0.0,
                desaturationCount = 0,
                totalHours = 0.0
            )
        }

        // 2. Detect desaturation events
        val desaturationEvents = detectDesaturationEvents(validTimestamps, validSpo2)
        
        // 3. Calculate ODI indices
        val totalHours = calculateRecordingHours(validTimestamps)
        val (odi3, odi4) = calculateOdiIndices(desaturationEvents, totalHours)
        
        return OdiResult(
            odi3 = odi3,
            odi4 = odi4,
            desaturationCount = desaturationEvents.size,
            totalHours = totalHours
        )
    }

    /**
     * Detects individual desaturation events.
     *
     * @param timestamps Valid SpO₂ timestamps
     * @param spo2Values Valid SpO₂ values
     * @return List of detected desaturation events
     */
    fun detectDesaturationEvents(
        timestamps: LongArray,
        spo2Values: DoubleArray
    ): List<DesaturationEvent> {
        val events = mutableListOf<DesaturationEvent>()
        var eventStart: Int? = null
        var baselineSpo2: Double? = null
        
        for (i in spo2Values.indices) {
            // Calculate baseline if not set
            if (baselineSpo2 == null) {
                baselineSpo2 = calculateBaselineSpo2(timestamps, spo2Values, i)
            }
            
            // Check for desaturation start
            if (eventStart == null && baselineSpo2 != null) {
                val desaturation = baselineSpo2 - spo2Values[i]
                
                if (desaturation >= MIN_DESATURATION_PERCENT_3) {
                    eventStart = i
                }
            }
            
            // Check for desaturation end or recovery
            if (eventStart != null && baselineSpo2 != null) {
                val eventDuration = timestamps[i] - timestamps[eventStart]
                val currentDesaturation = baselineSpo2 - spo2Values[i]
                
                // Check if event should end
                val shouldEnd = when {
                    // Event too long
                    eventDuration > MAX_EVENT_DURATION_MS -> true
                    
                    // Partial recovery (within 1% of baseline)
                    currentDesaturation < 1.0 -> true
                    
                    // New baseline established (different by >2%)
                    i > eventStart + 10 -> {
                        val newBaseline = calculateBaselineSpo2(
                            timestamps, spo2Values, i, lookForward = true
                        )
                        newBaseline != null && abs(baselineSpo2 - newBaseline) > 2.0
                    }
                    
                    else -> false
                }
                
                if (shouldEnd) {
                    // Find nadir (lowest SpO₂) during event
                    val nadirIndex = (eventStart..i).minByOrNull { spo2Values[it] } ?: eventStart
                    val baselineInt = baselineSpo2!!.roundToInt().coerceIn(0, 100)
                    val nadirInt = spo2Values[nadirIndex].roundToInt().coerceIn(0, 100)
                    val dropInt = (baselineInt - nadirInt).coerceAtLeast(0)

                    val event = DesaturationEvent(
                        startTimeMs = timestamps[eventStart],
                        nadirTimeMs = timestamps[nadirIndex],
                        endTimeMs = timestamps[i],
                        baselineSpO2 = baselineInt,
                        nadirSpO2 = nadirInt,
                        dropPercent = dropInt
                    )
                    
                    // Only add if meets duration and depth criteria
                    if (isValidDesaturationEvent(event)) {
                        events.add(event)
                    }
                    
                    // Reset for next event
                    eventStart = null
                    baselineSpo2 = calculateBaselineSpo2(timestamps, spo2Values, i)
                }
            }
            
            // Update baseline periodically, but not while a desaturation is open (would shrink the event)
            if (eventStart == null && i % 10 == 0) {
                baselineSpo2 = calculateBaselineSpo2(timestamps, spo2Values, i)
            }
        }
        
        // Handle event that continues to end of recording
        eventStart?.let { start ->
            val nadirIndex = (start until spo2Values.size).minByOrNull { spo2Values[it] } ?: start
            val baselineDouble = baselineSpo2 ?: spo2Values[start]
            val baselineInt = baselineDouble.roundToInt().coerceIn(0, 100)
            val nadirInt = spo2Values[nadirIndex].roundToInt().coerceIn(0, 100)
            val dropInt = (baselineInt - nadirInt).coerceAtLeast(0)

            val event = DesaturationEvent(
                startTimeMs = timestamps[start],
                nadirTimeMs = timestamps[nadirIndex],
                endTimeMs = timestamps.last(),
                baselineSpO2 = baselineInt,
                nadirSpO2 = nadirInt,
                dropPercent = dropInt
            )
            
            if (isValidDesaturationEvent(event)) {
                events.add(event)
            }
        }
        
        return events
    }

    /**
     * Calculates T90 (percentage of time with SpO₂ < 90%).
     *
     * @param timestamps SpO₂ timestamps
     * @param spo2Values SpO₂ values
     * @return T90 percentage (0-100)
     */
    fun calculateT90(timestamps: LongArray, spo2Values: DoubleArray): Double {
        if (timestamps.size < 2) {
            return 0.0
        }

        var totalTimeBelow90 = 0L
        var wasBelow90 = false
        var segmentStart = 0L
        
        for (i in spo2Values.indices) {
            val isBelow90 = spo2Values[i] < 90.0
            
            if (isBelow90 && !wasBelow90) {
                // Start of below-90 segment
                segmentStart = timestamps[i]
                wasBelow90 = true
            } else if (!isBelow90 && wasBelow90) {
                // End of below-90 segment
                totalTimeBelow90 += timestamps[i] - segmentStart
                wasBelow90 = false
            }
        }
        
        // Handle segment that continues to end
        if (wasBelow90) {
            totalTimeBelow90 += timestamps.last() - segmentStart
        }
        
        val totalDuration = timestamps.last() - timestamps.first()
        return if (totalDuration > 0) {
            (totalTimeBelow90 * 100.0) / totalDuration
        } else {
            0.0
        }
    }

    /**
     * Calculates average SpO₂ during recording.
     */
    fun calculateAverageSpo2(spo2Values: DoubleArray): Double {
        if (spo2Values.isEmpty()) {
            return 0.0
        }
        
        val validValues = spo2Values.filter { it in MIN_VALID_SPO2..MAX_VALID_SPO2 }
        return validValues.average()
    }

    // =========================================================================
    // Processing Steps
    // =========================================================================

    private fun validateAndCleanSpo2Data(
        timestamps: LongArray,
        spo2Values: DoubleArray
    ): Pair<LongArray, DoubleArray> {
        require(timestamps.size == spo2Values.size) { "Timestamps and values must have same size" }
        
        val validTimestamps = mutableListOf<Long>()
        val validSpo2 = mutableListOf<Double>()
        
        for (i in spo2Values.indices) {
            val value = spo2Values[i]
            val isValid = value in MIN_VALID_SPO2..MAX_VALID_SPO2
            
            // Check rate of change (artifact detection)
            val rateOfChange = if (i > 0 && validTimestamps.isNotEmpty()) {
                val timeDiff = (timestamps[i] - validTimestamps.last()) / 1000.0 // seconds
                if (timeDiff > 0) {
                    abs(value - validSpo2.last()) / timeDiff
                } else {
                    0.0
                }
            } else {
                0.0
            }
            
            val isStable = rateOfChange <= MAX_SPO2_CHANGE_PER_SECOND
            
            if (isValid && isStable) {
                validTimestamps.add(timestamps[i])
                validSpo2.add(value)
            }
        }
        
        return Pair(validTimestamps.toLongArray(), validSpo2.toDoubleArray())
    }

    private fun calculateBaselineSpo2(
        timestamps: LongArray,
        spo2Values: DoubleArray,
        currentIndex: Int,
        lookForward: Boolean = false
    ): Double? {
        val windowStart = timestamps[currentIndex] - BASELINE_WINDOW_MS / 2
        val windowEnd = timestamps[currentIndex] + BASELINE_WINDOW_MS / 2
        
        val valuesInWindow = spo2Values.filterIndexed { j, _ ->
            val inPast = !lookForward && j < currentIndex
            val inFuture = lookForward && j > currentIndex
            val inTimeWindow = timestamps[j] in windowStart..windowEnd
            
            (inPast || inFuture) && inTimeWindow
        }
        
        return if (valuesInWindow.size >= MIN_BASELINE_SAMPLES) {
            valuesInWindow.average()
        } else {
            null
        }
    }

    private fun calculateOdiIndices(
        events: List<DesaturationEvent>,
        totalHours: Double
    ): Pair<Double, Double> {
        if (totalHours <= 0.0) {
            return Pair(0.0, 0.0)
        }
        
        val odi3Events = events.count { it.dropPercent >= MIN_DESATURATION_PERCENT_3 }
        val odi4Events = events.count { it.dropPercent >= MIN_DESATURATION_PERCENT_4 }
        
        val odi3 = odi3Events / totalHours
        val odi4 = odi4Events / totalHours
        
        return Pair(odi3, odi4)
    }

    private fun calculateRecordingHours(timestamps: LongArray): Double {
        if (timestamps.size < 2) {
            return 0.0
        }
        
        val durationMs = timestamps.last() - timestamps.first()
        return durationMs / (1000.0 * 60.0 * 60.0) // ms to hours
    }

    private fun isValidDesaturationEvent(event: DesaturationEvent): Boolean {
        // Check duration
        val duration = event.endTimeMs - event.startTimeMs
        if (duration !in MIN_EVENT_DURATION_MS..MAX_EVENT_DURATION_MS) {
            return false
        }
        
        // Check depth (at least 3% for ODI₃)
        if (event.dropPercent.toDouble() < MIN_DESATURATION_PERCENT_3) {
            return false
        }
        
        // Check that baseline is reasonable
        if (event.baselineSpO2 !in MIN_VALID_SPO2.toInt()..MAX_VALID_SPO2.toInt()) {
            return false
        }
        
        // Check that nadir is reasonable
        if (event.nadirSpO2 !in MIN_VALID_SPO2.toInt()..MAX_VALID_SPO2.toInt()) {
            return false
        }
        
        return true
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    /**
     * Classifies ODI-based sleep apnea severity.
     *
     * @param odi3 ODI₃ value
     * @param odi4 ODI₄ value
     * @return Severity category
     */
    fun classifyOdiSeverity(odi3: Double, odi4: Double): com.example.androidapp.domain.model.SeverityCategory {
        // Use ODI₄ if available, otherwise ODI₃
        val odiToUse = if (odi4 > 0.0) odi4 else odi3
        
        return when {
            odiToUse < 5.0 -> com.example.androidapp.domain.model.SeverityCategory.NORMAL
            odiToUse < 15.0 -> com.example.androidapp.domain.model.SeverityCategory.MILD
            odiToUse < 30.0 -> com.example.androidapp.domain.model.SeverityCategory.MODERATE
            else -> com.example.androidapp.domain.model.SeverityCategory.SEVERE
        }
    }

    /**
     * Calculates oxygen saturation variability index.
     *
     * Higher values indicate more unstable oxygenation.
     *
     * @param spo2Values SpO₂ values
     * @return Variability index (0-100)
     */
    fun calculateSpo2Variability(spo2Values: DoubleArray): Double {
        if (spo2Values.size < 10) {
            return 0.0
        }
        
        // Calculate standard deviation
        val mean = spo2Values.average()
        val variance = spo2Values.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // Normalize to 0-100 scale (assuming std dev up to 5% is normal)
        return min(100.0, max(0.0, stdDev * 20.0))
    }

    /**
     * Detects prolonged hypoxemia events (SpO₂ < 88% for >5 minutes).
     *
     * @param timestamps SpO₂ timestamps
     * @param spo2Values SpO₂ values
     * @param minDurationMs Minimum duration for hypoxemia (default 5 minutes)
     * @return List of (startTime, endTime, minSpO2) for hypoxemia events
     */
    fun detectHypoxemiaEvents(
        timestamps: LongArray,
        spo2Values: DoubleArray,
        minDurationMs: Long = 300000L // 5 minutes
    ): List<Triple<Long, Long, Double>> {
        val events = mutableListOf<Triple<Long, Long, Double>>()
        var eventStart: Int? = null
        var minSpo2InEvent = 100.0
        
        for (i in spo2Values.indices) {
            val isHypoxemic = spo2Values[i] < 88.0
            
            if (isHypoxemic && eventStart == null) {
                // Start of potential hypoxemia event
                eventStart = i
                minSpo2InEvent = spo2Values[i]
            } else if (isHypoxemic && eventStart != null) {
                // Continue event, track minimum SpO₂
                minSpo2InEvent = min(minSpo2InEvent, spo2Values[i])
            } else if (!isHypoxemic && eventStart != null) {
                // End of event
                val duration = timestamps[i] - timestamps[eventStart]
                
                if (duration >= minDurationMs) {
                    events.add(Triple(timestamps[eventStart], timestamps[i], minSpo2InEvent))
                }
                
                eventStart = null
                minSpo2InEvent = 100.0
            }
        }
        
        // Handle event that continues to end of recording
        eventStart?.let { start ->
            val duration = timestamps.last() - timestamps[start]
            
            if (duration >= minDurationMs) {
                events.add(Triple(timestamps[start], timestamps.last(), minSpo2InEvent))
            }
        }
        
        return events
    }
}