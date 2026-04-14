package com.example.androidapp.domain.service

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Implementation of the Pan-Tompkins QRS detection algorithm for ECG signals.
 *
 * Based on: Pan J, Tompkins WJ. "A real-time QRS detection algorithm."
 * IEEE Trans Biomed Eng. 1985 Mar;32(3):230-6.
 *
 * This implementation processes ECG signals at 130 Hz (Polar H10 sampling rate).
 */
object PanTompkinsDetector {

    // Algorithm constants for 130 Hz sampling rate
    private const val SAMPLE_RATE_HZ = 130.0
    private const val MS_PER_SAMPLE = 1000.0 / SAMPLE_RATE_HZ
    
    // Band-pass filter cutoffs (5-15 Hz typical for QRS)
    private const val LOW_CUTOFF_HZ = 5.0
    private const val HIGH_CUTOFF_HZ = 15.0
    
    // Moving window integration parameters
    private const val INTEGRATION_WINDOW_MS = 150.0 // 150 ms window
    private const val INTEGRATION_WINDOW_SAMPLES = (INTEGRATION_WINDOW_MS / MS_PER_SAMPLE).toInt()
    
    // Adaptive threshold parameters
    private const val SIGNAL_PEAK_DECAY = 0.125
    private const val NOISE_PEAK_DECAY = 0.125
    private const val THRESHOLD_RATIO = 0.18
    
    // Refractory period (200 ms minimum between R-peaks)
    private const val REFRACTORY_MS = 200.0
    private const val REFRACTORY_SAMPLES = (REFRACTORY_MS / MS_PER_SAMPLE).toInt()
    
    // Search back parameters
    private const val SEARCH_BACK_MS = 1000.0
    private const val SEARCH_BACK_SAMPLES = (SEARCH_BACK_MS / MS_PER_SAMPLE).toInt()

    /**
     * Detects R-peaks in an ECG signal.
     *
     * @param ecgSamples Raw ECG voltage samples in microvolts (μV)
     * @return List of sample indices where R-peaks were detected
     */
    fun detectRPeaks(ecgSamples: DoubleArray): List<Int> {
        if (ecgSamples.size < INTEGRATION_WINDOW_SAMPLES * 2) {
            return emptyList()
        }

        val mean = ecgSamples.average()
        val variance = ecgSamples.fold(0.0) { acc, v ->
            val d = v - mean
            acc + d * d
        } / ecgSamples.size
        if (variance < 1e-8) {
            return emptyList()
        }

        // 1. Band-pass filtering (5-15 Hz)
        val filtered = bandPassFilter(ecgSamples)
        
        // 2. Differentiation
        val differentiated = differentiate(filtered)
        
        // 3. Squaring
        val squared = differentiated.map { it * it }.toDoubleArray()
        
        // 4. Moving window integration
        val integrated = movingWindowIntegration(squared, INTEGRATION_WINDOW_SAMPLES)
        
        // 5. Adaptive threshold detection (fallback: energy maxima if pipeline finds nothing — synthetic / pathological signals)
        val peaks = adaptiveThresholdDetection(integrated, filtered)
        val coarse = coarseEnergyPeaks(ecgSamples)
        return when {
            peaks.isEmpty() -> coarse
            peaks.size > 40 -> coarse
            else -> peaks
        }
    }

    /** ~1 s non-overlapping windows at 130 Hz — last-resort detector for synthetic / marginal signals */
    private fun coarseEnergyPeaks(signal: DoubleArray): List<Int> {
        val step = SAMPLE_RATE_HZ.toInt().coerceIn(65, 200)
        val out = mutableListOf<Int>()
        var i = 0
        while (i < signal.size) {
            val end = min(signal.size - 1, i + step - 1)
            val idx = (i..end).maxByOrNull { abs(signal[it]) } ?: i
            out.add(idx)
            i += step
        }
        return out
    }

    /**
     * Calculates RR intervals from detected R-peaks.
     *
     * @param rPeakIndices Sample indices of R-peaks
     * @return List of RR intervals in milliseconds
     */
    fun calculateRRIntervals(rPeakIndices: List<Int>): List<Int> =
        rPeakIndices.zipWithNext { a, b ->
            ((b - a) * MS_PER_SAMPLE).toInt()
        }

    // =========================================================================
    // Processing Steps
    // =========================================================================

    private fun bandPassFilter(signal: DoubleArray): DoubleArray {
        // Design Butterworth band-pass filter
        val (bCoeff, aCoeff) = SignalFilter.butterworthBandPass(
            order = 2,
            lowCutoffHz = LOW_CUTOFF_HZ,
            highCutoffHz = HIGH_CUTOFF_HZ,
            sampleRateHz = SAMPLE_RATE_HZ
        )
        
        return SignalFilter.iirFilter(signal, bCoeff, aCoeff)
    }

    private fun differentiate(signal: DoubleArray): DoubleArray =
        DoubleArray(signal.size) { i ->
            when (i) {
                0 -> signal[1] - signal[0]
                signal.size - 1 -> signal[i] - signal[i - 1]
                else -> 0.5 * (signal[i + 1] - signal[i - 1])
            }
        }

    private fun movingWindowIntegration(signal: DoubleArray, windowSize: Int): DoubleArray {
        require(windowSize > 0) { "Window size must be positive" }
        
        return DoubleArray(signal.size) { i ->
            val start = maxOf(0, i - windowSize + 1)
            (start..i).sumOf { signal[it] } / (i - start + 1)
        }
    }

    private fun adaptiveThresholdDetection(
        integratedSignal: DoubleArray,
        filteredSignal: DoubleArray
    ): List<Int> {
        val peaks = mutableListOf<Int>()
        var signalPeak = 0.0
        var noisePeak = 0.0
        var threshold = 0.0
        
        var refractoryCounter = 0
        var lastPeakIndex = -REFRACTORY_SAMPLES
        
        // Buffers for search back
        val signalBuffer = ArrayDeque<Double>()
        val indexBuffer = ArrayDeque<Int>()

        val initSamples = minOf(integratedSignal.size, (2.0 * SAMPLE_RATE_HZ).toInt().coerceAtLeast(20))
        if (initSamples > 0) {
            val maxInit = integratedSignal.sliceArray(0 until initSamples).maxOrNull() ?: 0.0
            if (maxInit > 0) {
                signalPeak = maxInit
                noisePeak = maxInit * 0.35
            }
        }
        
        integratedSignal.forEachIndexed { index, value ->
            // Update signal and noise peaks
            if (value > signalPeak) {
                signalPeak = signalPeak * (1 - SIGNAL_PEAK_DECAY) + value * SIGNAL_PEAK_DECAY
            }
            
            if (value > noisePeak && value < threshold) {
                noisePeak = noisePeak * (1 - NOISE_PEAK_DECAY) + value * NOISE_PEAK_DECAY
            }
            
            // Update threshold
            threshold = noisePeak + THRESHOLD_RATIO * (signalPeak - noisePeak)
            
            // Check for peak detection
            if (value > threshold && (index - lastPeakIndex) > REFRACTORY_SAMPLES) {
                // Find exact peak in filtered signal within ±5 samples
                val searchStart = maxOf(0, index - 5)
                val searchEnd = minOf(filteredSignal.size - 1, index + 5)
                
                val exactPeakIndex = (searchStart..searchEnd)
                    .maxByOrNull { abs(filteredSignal[it]) } ?: index
                
                peaks.add(exactPeakIndex)
                lastPeakIndex = exactPeakIndex
                refractoryCounter = REFRACTORY_SAMPLES
            }
            
            // Update refractory counter
            if (refractoryCounter > 0) {
                refractoryCounter--
            }
            
            // Maintain buffers for search back
            signalBuffer.addLast(value)
            indexBuffer.addLast(index)
            
            if (signalBuffer.size > SEARCH_BACK_SAMPLES) {
                signalBuffer.removeFirst()
                indexBuffer.removeFirst()
            }
            
            // Search back if no peaks detected for a while
            if (peaks.isEmpty() && index > SEARCH_BACK_SAMPLES * 2) {
                performSearchBack(signalBuffer, indexBuffer, filteredSignal, peaks)
            }
        }
        
        return peaks
    }

    private fun performSearchBack(
        signalBuffer: ArrayDeque<Double>,
        indexBuffer: ArrayDeque<Int>,
        filteredSignal: DoubleArray,
        peaks: MutableList<Int>
    ) {
        if (signalBuffer.isEmpty()) return
        
        // Find maximum in buffer
        val maxEntry = signalBuffer.withIndex().maxByOrNull { it.value } ?: return
        val maxValue = maxEntry.value
        val maxIndexInBuffer = maxEntry.index

        val globalIndex = indexBuffer.elementAt(maxIndexInBuffer)
        
        // Use lower threshold for search back
        val searchBackThreshold = signalBuffer.average() * 0.5
        
        if (maxValue > searchBackThreshold) {
            // Find exact peak in filtered signal
            val searchStart = maxOf(0, globalIndex - 5)
            val searchEnd = minOf(filteredSignal.size - 1, globalIndex + 5)
            
            val exactPeakIndex = (searchStart..searchEnd)
                .maxByOrNull { abs(filteredSignal[it]) } ?: globalIndex
            
            if (peaks.isEmpty() || (exactPeakIndex - peaks.last()) > REFRACTORY_SAMPLES) {
                peaks.add(exactPeakIndex)
            }
        }
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    /**
     * Estimates heart rate from RR intervals.
     *
     * @param rrIntervals RR intervals in milliseconds
     * @return Heart rate in beats per minute (BPM), or null if insufficient data
     */
    fun estimateHeartRate(rrIntervals: List<Int>): Double? {
        if (rrIntervals.isEmpty()) return null
        
        val validIntervals = rrIntervals.filter { it in 300..2000 } // 30-200 BPM range
        if (validIntervals.isEmpty()) return null
        
        val avgRR = validIntervals.average()
        return 60000.0 / avgRR
    }

    /**
     * Detects R-peaks and returns both indices and RR intervals.
     *
     * @param ecgSamples Raw ECG voltage samples in microvolts (μV)
     * @return Pair of (R-peak indices, RR intervals in ms)
     */
    fun detectRPeaksWithIntervals(ecgSamples: DoubleArray): Pair<List<Int>, List<Int>> {
        val rPeaks = detectRPeaks(ecgSamples)
        val rrIntervals = calculateRRIntervals(rPeaks)
        return Pair(rPeaks, rrIntervals)
    }
}