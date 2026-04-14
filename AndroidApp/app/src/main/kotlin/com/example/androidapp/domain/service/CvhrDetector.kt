package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.CvhrCycle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cyclic Variation of Heart Rate (CVHR) detector for sleep apnea screening.
 *
 * Detects characteristic bradycardia-tachycardia cycles associated with
 * obstructive sleep apnea events.
 *
 * Based on: Guilleminault C, et al. "Cyclic variation of the heart rate in
 * sleep apnea syndrome. Mechanisms, and usefulness of 24 h electrocardiography
 * as a screening technique." Lancet. 1984;1(8369):126-31.
 */
object CvhrDetector {

    // CVHR detection parameters (empirically determined)
    private const val MIN_CYCLE_DURATION_MS = 30000L    // 30 seconds minimum
    private const val MAX_CYCLE_DURATION_MS = 120000L   // 120 seconds maximum
    private const val MIN_HEART_RATE_DELTA = 5.0        // Minimum HR change in BPM
    private const val MIN_CYCLES_FOR_APNEA = 3          // Minimum cycles for apnea pattern
    
    // Smoothing window must stay well below typical CVHR period (~30–60 s) or extrema vanish at 1 Hz sampling
    private const val SMOOTHING_WINDOW_MS = 800L
    
    // Peak detection parameters
    /**
     * Detects CVHR cycles from heart rate time series.
     *
     * @param timestamps Heart rate measurement timestamps in milliseconds
     * @param heartRates Heart rate values in BPM
     * @return List of detected CVHR cycles
     */
    fun detectCvhrCycles(
        timestamps: LongArray,
        heartRates: DoubleArray
    ): List<CvhrCycle> {
        if (timestamps.size < 10 || heartRates.size < 10) {
            return emptyList()
        }

        // 1. Smooth heart rate signal
        val smoothedHR = smoothHeartRate(timestamps, heartRates)
        
        // 2. Detect peaks and valleys
        val peaks = detectPeaks(timestamps, smoothedHR, isPeak = true)
        val valleys = detectPeaks(timestamps, smoothedHR, isPeak = false)
        
        // 3. Pair peaks and valleys to form cycles
        return pairPeaksAndValleys(peaks, valleys, smoothedHR)
    }

    /**
     * Calculates CVHR-based apnea-hypopnea index (AHI).
     *
     * @param cvhrCycles Detected CVHR cycles
     * @param recordingDurationMs Total recording duration in milliseconds
     * @return Estimated AHI (events per hour)
     */
    fun calculateCvhrAhi(cvhrCycles: List<CvhrCycle>, recordingDurationMs: Long): Double {
        if (recordingDurationMs <= 0) {
            return 0.0
        }
        
        val validCycles = cvhrCycles.filter { isValidApneaCycle(it) }
        val eventsPerHour = validCycles.size * 3600000.0 / recordingDurationMs
        
        return eventsPerHour
    }

    /**
     * Classifies sleep apnea severity based on CVHR-derived AHI.
     *
     * @param ahi Apnea-Hypopnea Index from CVHR analysis
     * @return Severity category
     */
    fun classifyApneaSeverity(ahi: Double): com.example.androidapp.domain.model.SeverityCategory {
        return when {
            ahi < 5.0 -> com.example.androidapp.domain.model.SeverityCategory.NORMAL
            ahi < 15.0 -> com.example.androidapp.domain.model.SeverityCategory.MILD
            ahi < 30.0 -> com.example.androidapp.domain.model.SeverityCategory.MODERATE
            else -> com.example.androidapp.domain.model.SeverityCategory.SEVERE
        }
    }

    // =========================================================================
    // Processing Steps
    // =========================================================================

    private fun smoothHeartRate(timestamps: LongArray, heartRates: DoubleArray): DoubleArray {
        if (timestamps.isEmpty()) {
            return doubleArrayOf()
        }

        return DoubleArray(heartRates.size) { i ->
            val windowStart = timestamps[i] - SMOOTHING_WINDOW_MS / 2
            val windowEnd = timestamps[i] + SMOOTHING_WINDOW_MS / 2
            
            val valuesInWindow = heartRates.filterIndexed { j, _ ->
                timestamps[j] in windowStart..windowEnd
            }
            
            if (valuesInWindow.isEmpty()) {
                heartRates[i]
            } else {
                valuesInWindow.average()
            }
        }
    }

    private fun detectPeaks(
        timestamps: LongArray,
        signal: DoubleArray,
        isPeak: Boolean
    ): List<PeakValley> {
        val peaks = mutableListOf<PeakValley>()
        
        // Minimum distance between peaks (keep below half-period of fastest CVHR ~30 s)
        val minDistanceMs = 5000L
        
        for (i in 1 until signal.size - 1) {
            val isExtremum = if (isPeak) {
                signal[i] > signal[i - 1] && signal[i] > signal[i + 1]
            } else {
                signal[i] < signal[i - 1] && signal[i] < signal[i + 1]
            }
            
            if (isExtremum) {
                // Local extrema + refractory spacing (prominence omitted: 1 Hz HR series is too sparse for µV-style rules)
                val lastPeakTime = peaks.lastOrNull()?.timestamp
                if (lastPeakTime == null || (timestamps[i] - lastPeakTime) >= minDistanceMs) {
                    peaks.add(PeakValley(timestamps[i], signal[i], isPeak))
                }
            }
        }
        
        return peaks
    }

    private fun pairPeaksAndValleys(
        peaks: List<PeakValley>,
        valleys: List<PeakValley>,
        heartRates: DoubleArray
    ): List<CvhrCycle> {
        val cycles = mutableListOf<CvhrCycle>()
        
        // Sort all extrema by timestamp
        val allExtrema = (peaks + valleys).sortedBy { it.timestamp }
        
        var i = 0
        while (i < allExtrema.size - 1) {
            val current = allExtrema[i]
            val next = allExtrema[i + 1]
            
            // Check if we have a peak-valley or valley-peak pair
            if (current.isPeak != next.isPeak) {
                val cycleDuration = abs(next.timestamp - current.timestamp)
                
                if (cycleDuration in MIN_CYCLE_DURATION_MS..MAX_CYCLE_DURATION_MS) {
                    val (startTime, endTime, minHr, maxHr) = if (current.isPeak) {
                        // Peak followed by valley (bradycardia phase)
                        Quadruple(
                            current.timestamp,
                            next.timestamp,
                            next.value,
                            current.value
                        )
                    } else {
                        // Valley followed by peak (tachycardia phase)
                        Quadruple(
                            current.timestamp,
                            next.timestamp,
                            current.value,
                            next.value
                        )
                    }
                    
                    val deltaHr = maxHr - minHr
                    
                    if (deltaHr >= MIN_HEART_RATE_DELTA) {
                        cycles.add(
                            CvhrCycle(
                                startTimeMs = startTime,
                                endTimeMs = endTime,
                                minHr = minHr,
                                maxHr = maxHr,
                                deltaHr = deltaHr,
                                periodMs = cycleDuration
                            )
                        )
                    }
                }
                
                i += 2 // Skip both extrema since we paired them
            } else {
                i += 1 // Same type, move to next
            }
        }
        
        return cycles
    }

    private fun isValidApneaCycle(cycle: CvhrCycle): Boolean {
        // Check cycle duration
        if (cycle.periodMs !in MIN_CYCLE_DURATION_MS..MAX_CYCLE_DURATION_MS) {
            return false
        }
        
        // Check heart rate delta
        if (cycle.deltaHr < MIN_HEART_RATE_DELTA) {
            return false
        }
        
        // Check that min HR is reasonable (not artifact)
        if (cycle.minHr < 30.0 || cycle.minHr > 100.0) {
            return false
        }
        
        // Check that max HR is reasonable
        if (cycle.maxHr < 50.0 || cycle.maxHr > 150.0) {
            return false
        }
        
        return true
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    /**
     * Detects apnea clusters (multiple CVHR cycles in sequence).
     *
     * @param cvhrCycles Detected CVHR cycles
     * @param maxGapBetweenCyclesMs Maximum gap between cycles to form a cluster
     * @return List of clusters, each containing consecutive cycles
     */
    fun detectApneaClusters(
        cvhrCycles: List<CvhrCycle>,
        maxGapBetweenCyclesMs: Long = 60000L // 1 minute
    ): List<List<CvhrCycle>> {
        if (cvhrCycles.isEmpty()) {
            return emptyList()
        }
        
        val clusters = mutableListOf<MutableList<CvhrCycle>>()
        var currentCluster = mutableListOf<CvhrCycle>()
        
        cvhrCycles.sortedBy { it.startTimeMs }.forEach { cycle ->
            if (currentCluster.isEmpty()) {
                currentCluster.add(cycle)
            } else {
                val lastCycle = currentCluster.last()
                val gap = cycle.startTimeMs - lastCycle.endTimeMs
                
                if (gap <= maxGapBetweenCyclesMs) {
                    currentCluster.add(cycle)
                } else {
                    if (currentCluster.size >= MIN_CYCLES_FOR_APNEA) {
                        clusters.add(currentCluster)
                    }
                    currentCluster = mutableListOf(cycle)
                }
            }
        }
        
        // Add last cluster if valid
        if (currentCluster.size >= MIN_CYCLES_FOR_APNEA) {
            clusters.add(currentCluster)
        }
        
        return clusters
    }

    /**
     * Calculates respiratory disturbance index (RDI) from CVHR analysis.
     * RDI includes both apneas and respiratory effort-related arousals.
     *
     * @param cvhrCycles Detected CVHR cycles
     * @param recordingDurationMs Total recording duration
     * @return Respiratory Disturbance Index
     */
    fun calculateRdi(cvhrCycles: List<CvhrCycle>, recordingDurationMs: Long): Double {
        // For CVHR-based detection, RDI is similar to AHI but may be slightly higher
        // due to inclusion of respiratory effort events
        val ahi = calculateCvhrAhi(cvhrCycles, recordingDurationMs)
        
        // Empirical correction factor based on literature
        // CVHR detects both apneas and flow limitation events
        return ahi * 1.2
    }

    // =========================================================================
    // Data Classes
    // =========================================================================

    private data class PeakValley(
        val timestamp: Long,
        val value: Double,
        val isPeak: Boolean // true for peak, false for valley
    )
    
    private data class Quadruple(
        val first: Long,
        val second: Long,
        val third: Double,
        val fourth: Double
    )
}