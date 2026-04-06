package com.example.androidapp.domain.service

import kotlin.math.sqrt

/**
 * Domain service responsible for HRV (Heart Rate Variability) computations.
 *
 * All calculations follow the **Task Force (1996)** standard:
 * > Task Force of the European Society of Cardiology and NASPE.
 * > "Heart rate variability: standards of measurement, physiological
 * > interpretation, and clinical use." Eur Heart J, 17(3), 354–381.
 */
object HrvCalculator {

    /** 5-minute sliding window as recommended by Task Force (1996). */
    const val WINDOW_MS: Long = 5 * 60 * 1000L

    /** Minimum RR intervals required before emitting a meaningful RMSSD value. */
    const val MIN_INTERVALS: Int = 20

    /**
     * Computes RMSSD (Root Mean Square of Successive Differences) from a list
     * of RR intervals in milliseconds.
     *
     * Formula:
     * ```
     * dRR[i]  = RR[i+1] − RR[i]
     * RMSSD   = √( Σ(dRR[i]²) / (N−1) )
     * ```
     *
     * @param rrIntervals List of RR intervals in milliseconds.
     * @return RMSSD value in milliseconds, or `null` if fewer than [MIN_INTERVALS]
     *         intervals are provided.
     */
    fun rmssd(rrIntervals: List<Int>): Double? {
        if (rrIntervals.size < MIN_INTERVALS) return null

        val differences = rrIntervals.zipWithNext { a, b -> (b - a).toDouble() }
        val meanSquare = differences.map { it * it }.average()
        return sqrt(meanSquare)
    }
}

