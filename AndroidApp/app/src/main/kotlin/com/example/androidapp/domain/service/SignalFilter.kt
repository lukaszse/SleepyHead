package com.example.androidapp.domain.service

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Domain service providing common DSP (Digital Signal Processing) utilities
 * for sleep apnea screening algorithms.
 *
 * All implementations are in pure Kotlin with no external dependencies.
 */
object SignalFilter {

    // =========================================================================
    // Butterworth IIR Filter Implementation
    // =========================================================================

    /**
     * Designs a Butterworth low-pass filter using the bilinear transform.
     *
     * @param order Filter order (1-4 recommended for stability)
     * @param cutoffHz Cutoff frequency in Hz
     * @param sampleRateHz Sampling rate in Hz
     * @return Pair of (bCoefficients, aCoefficients) for IIR filter
     */
    fun butterworthLowPass(
        order: Int,
        cutoffHz: Double,
        sampleRateHz: Double
    ): Pair<DoubleArray, DoubleArray> {
        require(order > 0) { "Filter order must be positive" }
        require(cutoffHz > 0 && cutoffHz < sampleRateHz / 2) {
            "Cutoff frequency must be between 0 and Nyquist frequency (${sampleRateHz / 2} Hz)"
        }

        // Pre-warp cutoff frequency for bilinear transform
        val omegaC = 2.0 * sampleRateHz * tan(PI * cutoffHz / sampleRateHz)
        
        // Get analog prototype poles
        val poles = butterworthAnalogPoles(order)
        
        // Apply bilinear transform to get digital coefficients
        return bilinearTransform(poles, omegaC, sampleRateHz)
    }

    /**
     * Designs a Butterworth band-pass filter.
     *
     * @param order Filter order per side (total order = 2*order)
     * @param lowCutoffHz Lower cutoff frequency in Hz
     * @param highCutoffHz Upper cutoff frequency in Hz
     * @param sampleRateHz Sampling rate in Hz
     * @return Pair of (bCoefficients, aCoefficients) for IIR filter
     */
    fun butterworthBandPass(
        order: Int,
        lowCutoffHz: Double,
        highCutoffHz: Double,
        sampleRateHz: Double
    ): Pair<DoubleArray, DoubleArray> {
        require(lowCutoffHz < highCutoffHz) { "Low cutoff must be less than high cutoff" }
        require(highCutoffHz < sampleRateHz / 2) {
            "High cutoff must be below Nyquist frequency (${sampleRateHz / 2} Hz)"
        }

        // Design low-pass and high-pass, then combine (cascade)
        val (bLow, aLow) = butterworthLowPass(order, highCutoffHz, sampleRateHz)
        val (bHigh, aHigh) = butterworthHighPass(order, lowCutoffHz, sampleRateHz)
        
        // Combine filters (cascade connection)
        return cascadeFilters(bLow, aLow, bHigh, aHigh)
    }

    /**
     * Designs a Butterworth high-pass filter.
     */
    internal fun butterworthHighPass(
        order: Int,
        cutoffHz: Double,
        sampleRateHz: Double
    ): Pair<DoubleArray, DoubleArray> {
        // Design low-pass with cutoff at (sampleRateHz/2 - cutoffHz)
        val (bLow, aLow) = butterworthLowPass(order, sampleRateHz / 2 - cutoffHz, sampleRateHz)
        
        // Transform low-pass to high-pass using spectral inversion
        return spectralInversion(bLow, aLow)
    }

    /**
     * Gets analog poles for Butterworth filter of given order.
     */
    private fun butterworthAnalogPoles(order: Int): List<Complex> =
        (0 until order).map { k ->
            val theta = PI * (2 * k + order + 1) / (2 * order)
            Complex(-sin(theta), cos(theta))
        }

    /**
     * Applies bilinear transform to convert analog poles to digital coefficients.
     */
    private fun bilinearTransform(
        poles: List<Complex>,
        omegaC: Double,
        sampleRateHz: Double
    ): Pair<DoubleArray, DoubleArray> {
        val T = 1.0 / sampleRateHz
        val zHalfSample = Complex(2.0 / T, 0.0)

        return poles.fold(Pair(listOf(1.0), listOf(1.0))) { (bAcc, aAcc), pole ->
            val s = pole * omegaC
            val zNum = (zHalfSample + s) / (zHalfSample - s)
            
            // Second-order section coefficients
            val bSection = listOf(1.0, -2.0 * zNum.real, zNum.real * zNum.real + zNum.imag * zNum.imag)
            val aSection = listOf(1.0, -2.0, 1.0)
            
            Pair(
                polynomialMultiply(bAcc.toDoubleArray(), bSection.toDoubleArray()).toList(),
                polynomialMultiply(aAcc.toDoubleArray(), aSection.toDoubleArray()).toList()
            )
        }.let { (bList, aList) ->
            Pair(bList.toDoubleArray(), aList.toDoubleArray())
        }
    }

    /**
     * Applies IIR filter to signal using direct form II transposed structure.
     *
     * @param signal Input signal
     * @param bCoeff Feedforward coefficients (b[0] to b[M])
     * @param aCoeff Feedback coefficients (a[0] to a[N], a[0] = 1)
     * @return Filtered signal
     */
    fun iirFilter(
        signal: DoubleArray,
        bCoeff: DoubleArray,
        aCoeff: DoubleArray
    ): DoubleArray {
        require(aCoeff.isNotEmpty() && aCoeff[0] == 1.0) {
            "First feedback coefficient a[0] must be 1.0"
        }

        val stateSize = maxOf(bCoeff.size, aCoeff.size) - 1
        val state = DoubleArray(stateSize)
        
        return DoubleArray(signal.size) { i ->
            // Direct Form II Transposed
            var w = signal[i]
            
            // Feedback part
            (1 until aCoeff.size).forEach { j ->
                if (i - j >= 0) {
                    w -= aCoeff[j] * state[j - 1]
                }
            }
            
            // Feedforward part
            var y = bCoeff[0] * w
            (1 until bCoeff.size).forEach { j ->
                if (i - j >= 0) {
                    y += bCoeff[j] * state[j - 1]
                }
            }
            
            // Update state
            if (state.isNotEmpty()) {
                state.copyInto(state, 1, 0, state.size - 1)
                state[0] = w
            }
            
            y
        }
    }

    // =========================================================================
    // Cubic Spline Interpolation
    // =========================================================================

    /**
     * Performs cubic spline interpolation on unevenly spaced data.
     *
     * @param x Original x-coordinates (must be strictly increasing)
     * @param y Original y-values
     * @param newX New x-coordinates for interpolation
     * @return Interpolated y-values at newX positions
     */
    fun cubicSplineInterpolate(
        x: DoubleArray,
        y: DoubleArray,
        newX: DoubleArray
    ): DoubleArray {
        require(x.size == y.size && x.size >= 2) { "Need at least 2 points for interpolation" }
        require(x.size == x.distinct().size) { "X coordinates must be unique" }
        
        // Calculate second derivatives
        val y2 = calculateSplineSecondDerivatives(x, y)
        
        // Interpolate at new points
        return newX.map { xi -> splineEvaluate(x, y, y2, xi) }.toDoubleArray()
    }

    /**
     * Calculates second derivatives for cubic spline.
     */
    private fun calculateSplineSecondDerivatives(x: DoubleArray, y: DoubleArray): DoubleArray {
        val n = x.size
        val y2 = DoubleArray(n)
        val u = DoubleArray(n - 1)
        
        // Natural spline boundary conditions
        y2[0] = 0.0
        u[0] = 0.0
        
        // Decomposition loop (needs indexed access)
        for (i in 1 until n - 1) {
            val sig = (x[i] - x[i - 1]) / (x[i + 1] - x[i - 1])
            val p = sig * y2[i - 1] + 2.0
            y2[i] = (sig - 1.0) / p
            u[i] = (6.0 * ((y[i + 1] - y[i]) / (x[i + 1] - x[i]) - 
                           (y[i] - y[i - 1]) / (x[i] - x[i - 1])) / 
                    (x[i + 1] - x[i - 1]) - sig * u[i - 1]) / p
        }
        
        // Back substitution
        var qn = 0.0
        var un = 0.0
        y2[n - 1] = (un - qn * u[n - 2]) / (qn * y2[n - 2] + 1.0)
        
        for (k in n - 2 downTo 0) {
            y2[k] = y2[k] * y2[k + 1] + u[k]
        }
        
        return y2
    }

    /**
     * Evaluates cubic spline at point x.
     */
    private fun splineEvaluate(
        x: DoubleArray,
        y: DoubleArray,
        y2: DoubleArray,
        xi: Double
    ): Double {
        // Binary search for interval
        val klo = (0 until x.size - 1).firstOrNull { xi in x[it]..x[it + 1] } ?: 0
        val khi = klo + 1
        
        val h = x[khi] - x[klo]
        require(h > 0) { "X values not strictly increasing" }
        
        val a = (x[khi] - xi) / h
        val b = (xi - x[klo]) / h
        
        return a * y[klo] + b * y[khi] + 
               ((a * a * a - a) * y2[klo] + (b * b * b - b) * y2[khi]) * (h * h) / 6.0
    }

    // =========================================================================
    // Moving Average and RMS
    // =========================================================================

    /**
     * Applies moving average filter to signal.
     *
     * @param signal Input signal
     * @param windowSize Size of moving window (must be odd)
     * @return Smoothed signal
     */
    fun movingAverage(signal: DoubleArray, windowSize: Int): DoubleArray {
        require(windowSize > 0 && windowSize % 2 == 1) { 
            "Window size must be positive and odd" 
        }
        
        val halfWindow = windowSize / 2
        
        return DoubleArray(signal.size) { i ->
            val start = maxOf(0, i - halfWindow)
            val end = minOf(signal.size - 1, i + halfWindow)
            
            (start..end)
                .map { signal[it] }
                .average()
        }
    }

    /**
     * Calculates Root Mean Square (RMS) of signal in sliding window.
     *
     * @param signal Input signal
     * @param windowSize Size of sliding window
     * @return RMS values
     */
    fun slidingRms(signal: DoubleArray, windowSize: Int): DoubleArray {
        require(windowSize > 0) { "Window size must be positive" }
        
        return DoubleArray(signal.size) { i ->
            val start = maxOf(0, i - windowSize + 1)
            
            sqrt(
                (start..i)
                    .map { signal[it] * signal[it] }
                    .average()
            )
        }
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    private fun polynomialMultiply(p1: DoubleArray, p2: DoubleArray): DoubleArray {
        val result = DoubleArray(p1.size + p2.size - 1)
        
        p1.forEachIndexed { i, coeff1 ->
            p2.forEachIndexed { j, coeff2 ->
                result[i + j] += coeff1 * coeff2
            }
        }
        
        return result
    }

    private fun cascadeFilters(
        b1: DoubleArray, a1: DoubleArray,
        b2: DoubleArray, a2: DoubleArray
    ): Pair<DoubleArray, DoubleArray> =
        Pair(
            polynomialMultiply(b1, b2),
            polynomialMultiply(a1, a2)
        )

    private fun spectralInversion(b: DoubleArray, a: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val bHigh = b.mapIndexed { i, coeff -> 
            if (i % 2 == 1) -coeff else coeff 
        }.toDoubleArray()
        
        return Pair(bHigh, a)
    }

    /**
     * Simple complex number representation for filter design.
     */
    private data class Complex(val real: Double, val imag: Double) {
        operator fun times(scalar: Double): Complex = Complex(real * scalar, imag * scalar)
        operator fun times(other: Complex): Complex = Complex(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
        )
        operator fun plus(other: Complex): Complex = Complex(real + other.real, imag + other.imag)
        operator fun minus(other: Complex): Complex = Complex(real - other.real, imag - other.imag)
        operator fun div(other: Complex): Complex {
            val denominator = other.real * other.real + other.imag * other.imag
            return Complex(
                (real * other.real + imag * other.imag) / denominator,
                (imag * other.real - real * other.imag) / denominator
            )
        }
    }
}