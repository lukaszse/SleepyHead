package com.example.androidapp.domain.model

/**
 * Value object representing a single detected CVHR (Cyclic Variation of Heart Rate) cycle.
 *
 * A CVHR cycle is the pathognomonic bradycardia→tachycardia pattern that accompanies
 * each obstructive apnea episode. Typical period: 30–90 seconds.
 *
 * Reference: Guilleminault et al. (1984), Penzel et al. (2002).
 *
 * @property startTimeMs Epoch milliseconds of the cycle start (bradycardia onset).
 * @property endTimeMs Epoch milliseconds of the cycle end (return to baseline).
 * @property minHr Minimum heart rate during the bradycardia phase (bpm).
 * @property maxHr Maximum heart rate during the tachycardia phase (bpm).
 * @property deltaHr Amplitude of the cycle: [maxHr] − [minHr] (bpm). Must be > 10 bpm.
 * @property periodMs Duration of the full cycle in milliseconds (valid: 20 000–120 000 ms).
 */
data class CvhrCycle(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val minHr: Double,
    val maxHr: Double,
    val deltaHr: Double,
    val periodMs: Long
) {
    init {
        require(deltaHr >= 0) { "deltaHr must be non-negative, was $deltaHr" }
        require(maxHr >= minHr) { "maxHr ($maxHr) must be >= minHr ($minHr)" }
        require(endTimeMs >= startTimeMs) { "endTimeMs must be >= startTimeMs" }
    }
}

