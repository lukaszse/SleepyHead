package com.example.androidapp.domain.service

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ECG-Derived Respiration (EDR) extraction service.
 *
 * Extracts respiratory signal from ECG using R-amplitude modulation.
 * Based on: Moody GB, Mark RG, Zoccola A, et al. "Derivation of respiratory
 * signals from multi-lead ECGs." Computers in Cardiology. 1985;12:113-116.
 */
object EdrExtractor {

    // Respiratory frequency range (normal breathing: 0.1-0.5 Hz)
    private const val RESP_LOW_HZ = 0.1
    private const val RESP_HIGH_HZ = 0.5
    
    // Sampling rate for EDR signal (after interpolation)
    private const val EDR_SAMPLE_RATE_HZ = 4.0 // 4 Hz sufficient for respiration
    
    // Cubic spline interpolation parameters
    private const val SPLINE_SMOOTHING = 0.5

    /**
     * Extracts respiratory signal from ECG R-peak amplitudes.
     *
     * @param ecgSamples Raw ECG samples in microvolts (μV)
     * @param rPeakIndices Sample indices of detected R-peaks
     * @return Pair of (timestamps in ms, EDR values) representing respiratory signal
     */
    fun extractEdrSignal(
        ecgSamples: DoubleArray,
        rPeakIndices: List<Int>
    ): Pair<DoubleArray, DoubleArray> {
        if (rPeakIndices.size < 4) {
            return Pair(doubleArrayOf(), doubleArrayOf())
        }

        // 1. Extract R-peak amplitudes
        val (rPeakTimes, rPeakAmplitudes) = extractRPeakAmplitudes(ecgSamples, rPeakIndices)
        
        // 2. Remove baseline wander using high-pass filter
        val filteredAmplitudes = removeBaselineWander(rPeakAmplitudes)
        
        // 3. Normalize amplitudes
        val normalized = normalizeAmplitudes(filteredAmplitudes)
        
        // 4. Interpolate to regular time grid
        return interpolateToRegularGrid(rPeakTimes, normalized)
    }

    /**
     * Estimates respiratory rate from EDR signal.
     *
     * @param edrTimestamps Timestamps in milliseconds
     * @param edrValues EDR signal values
     * @return Respiratory rate in breaths per minute (BPM), or null if cannot estimate
     */
    fun estimateRespiratoryRate(
        edrTimestamps: DoubleArray,
        edrValues: DoubleArray
    ): Double? {
        if (edrTimestamps.size < 10 || edrValues.size < 10) {
            return null
        }

        val filtered = bandPassFilterRespiratory(edrValues, EDR_SAMPLE_RATE_HZ)
        var breathPeriods = findBreathPeriods(edrTimestamps, filtered)
        if (breathPeriods.isEmpty()) {
            breathPeriods = findBreathPeriods(edrTimestamps, edrValues)
        }
        if (breathPeriods.isEmpty()) {
            return null
        }
        
        // 3. Calculate average respiratory rate
        val avgPeriodMs = breathPeriods.average()
        return 60000.0 / avgPeriodMs // Convert ms/breath to breaths/minute
    }

    // =========================================================================
    // Processing Steps
    // =========================================================================

    private fun extractRPeakAmplitudes(
        ecgSamples: DoubleArray,
        rPeakIndices: List<Int>
    ): Pair<DoubleArray, DoubleArray> {
        val times = DoubleArray(rPeakIndices.size)
        val amplitudes = DoubleArray(rPeakIndices.size)
        
        rPeakIndices.forEachIndexed { i, peakIndex ->
            // Convert sample index to milliseconds (130 Hz sampling)
            times[i] = peakIndex * (1000.0 / 130.0)
            
            // Extract R-peak amplitude (absolute value for bipolar ECG)
            amplitudes[i] = abs(ecgSamples[peakIndex])
        }
        
        return Pair(times, amplitudes)
    }

    private fun removeBaselineWander(amplitudes: DoubleArray): DoubleArray {
        if (amplitudes.size < 4) {
            return amplitudes.copyOf()
        }

        // Simple moving average as baseline estimate
        val windowSize = min(amplitudes.size / 4, 10)
        val baseline = movingAverage(amplitudes, windowSize)
        
        // Subtract baseline
        return DoubleArray(amplitudes.size) { i ->
            amplitudes[i] - baseline[i]
        }
    }

    private fun normalizeAmplitudes(amplitudes: DoubleArray): DoubleArray {
        if (amplitudes.isEmpty()) {
            return doubleArrayOf()
        }
        
        val mean = amplitudes.average()
        val std = sqrt(amplitudes.map { (it - mean) * (it - mean) }.average())
        
        if (std < 1e-10) {
            return DoubleArray(amplitudes.size) { 0.0 }
        }
        
        // Z-score normalization
        return amplitudes.map { (it - mean) / std }.toDoubleArray()
    }

    private fun interpolateToRegularGrid(
        rPeakTimes: DoubleArray,
        normalizedAmplitudes: DoubleArray
    ): Pair<DoubleArray, DoubleArray> {
        if (rPeakTimes.size < 4) {
            return Pair(doubleArrayOf(), doubleArrayOf())
        }

        // Create regular time grid (4 Hz = 250 ms intervals)
        val startTime = rPeakTimes.first()
        val endTime = rPeakTimes.last()
        val numSamples = ((endTime - startTime) * EDR_SAMPLE_RATE_HZ / 1000.0).toInt() + 1
        
        if (numSamples <= 0) {
            return Pair(doubleArrayOf(), doubleArrayOf())
        }

        val regularTimes = DoubleArray(numSamples) { i ->
            startTime + i * (1000.0 / EDR_SAMPLE_RATE_HZ)
        }
        
        // Cubic spline interpolation
        val interpolated = SignalFilter.cubicSplineInterpolate(
            rPeakTimes,
            normalizedAmplitudes,
            regularTimes
        )
        
        return Pair(regularTimes, interpolated)
    }

    private fun bandPassFilterRespiratory(
        signal: DoubleArray,
        sampleRateHz: Double
    ): DoubleArray {
        val (bCoeff, aCoeff) = SignalFilter.butterworthBandPass(
            order = 2,
            lowCutoffHz = RESP_LOW_HZ,
            highCutoffHz = RESP_HIGH_HZ,
            sampleRateHz = sampleRateHz
        )
        
        return SignalFilter.iirFilter(signal, bCoeff, aCoeff)
    }

    private fun findBreathPeriods(
        timestamps: DoubleArray,
        signal: DoubleArray
    ): List<Double> {
        val periods = mutableListOf<Double>()
        var lastZeroCrossing: Double? = null
        
        // Find zero-crossings with positive slope (inhalation start)
        for (i in 1 until signal.size) {
            if (signal[i - 1] <= 0 && signal[i] > 0) {
                val crossingTime = linearInterpolateZeroCrossing(
                    timestamps[i - 1], signal[i - 1],
                    timestamps[i], signal[i]
                )
                
                lastZeroCrossing?.let { lastTime ->
                    periods.add(crossingTime - lastTime)
                }
                
                lastZeroCrossing = crossingTime
            }
        }
        
        // Filter out unrealistic periods (~4–40 BPM at positive-slope crossings)
        return periods.filter { it in 1500.0..15000.0 }
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    private fun movingAverage(signal: DoubleArray, windowSize: Int): DoubleArray {
        require(windowSize > 0) { "Window size must be positive" }
        
        return DoubleArray(signal.size) { i ->
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(signal.size - 1, i + windowSize / 2)
            
            (start..end)
                .map { signal[it] }
                .average()
        }
    }

    private fun linearInterpolateZeroCrossing(
        x1: Double, y1: Double,
        x2: Double, y2: Double
    ): Double {
        // Linear interpolation to find exact zero crossing time
        val t = -y1 / (y2 - y1)
        return x1 + t * (x2 - x1)
    }

    /**
     * Calculates respiratory effort index from EDR signal.
     *
     * Higher values indicate increased respiratory effort.
     *
     * @param edrValues EDR signal values
     * @return Respiratory effort index (0-1 scale)
     */
    fun calculateRespiratoryEffortIndex(edrValues: DoubleArray): Double {
        if (edrValues.size < 10) {
            return 0.5 // Default medium effort
        }
        
        // Calculate signal power in respiratory band
        val power = edrValues.map { it * it }.average()
        
        // Normalize to 0-1 range (empirical thresholds)
        val normalized = min(1.0, max(0.0, power * 10.0))
        
        return normalized
    }

    /**
     * Detects respiratory pauses (apneas) from EDR signal.
     *
     * @param edrTimestamps Timestamps in milliseconds
     * @param edrValues EDR signal values
     * @param minPauseDurationMs Minimum pause duration to detect (default 10 seconds)
     * @return List of (startTime, endTime) pairs for detected pauses
     */
    fun detectRespiratoryPauses(
        edrTimestamps: DoubleArray,
        edrValues: DoubleArray,
        minPauseDurationMs: Double = 10000.0
    ): List<Pair<Double, Double>> {
        if (edrTimestamps.size < 20) {
            return emptyList()
        }

        val pauses = mutableListOf<Pair<Double, Double>>()
        
        // Calculate amplitude threshold (10th percentile)
        val sortedAmplitudes = edrValues.sorted()
        val thresholdIndex = (sortedAmplitudes.size * 0.1).toInt()
        val amplitudeThreshold = abs(sortedAmplitudes[thresholdIndex])
        
        var pauseStart: Double? = null
        
        for (i in edrValues.indices) {
            val isLowAmplitude = abs(edrValues[i]) < amplitudeThreshold
            
            if (isLowAmplitude && pauseStart == null) {
                // Start of potential pause
                pauseStart = edrTimestamps[i]
            } else if (!isLowAmplitude && pauseStart != null) {
                // End of potential pause
                val pauseEnd = edrTimestamps[i]
                val pauseDuration = pauseEnd - pauseStart
                
                if (pauseDuration >= minPauseDurationMs) {
                    pauses.add(Pair(pauseStart, pauseEnd))
                }
                
                pauseStart = null
            }
        }
        
        // Handle pause that continues to end of signal
        pauseStart?.let { start ->
            val pauseEnd = edrTimestamps.last()
            val pauseDuration = pauseEnd - start
            
            if (pauseDuration >= minPauseDurationMs) {
                pauses.add(Pair(start, pauseEnd))
            }
        }
        
        return pauses
    }
}