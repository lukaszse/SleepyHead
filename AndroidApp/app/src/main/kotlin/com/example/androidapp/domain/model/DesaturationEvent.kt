package com.example.androidapp.domain.model

/**
 * Value object representing a single SpO₂ desaturation event.
 *
 * A desaturation is defined as a drop of ≥3% (ODI₃) or ≥4% (ODI₄)
 * from the baseline SpO₂ level. Used for ODI calculation.
 *
 * @property startTimeMs Epoch milliseconds when the desaturation began.
 * @property nadirTimeMs Epoch milliseconds of the lowest SpO₂ point.
 * @property endTimeMs Epoch milliseconds when SpO₂ recovered to baseline.
 * @property baselineSpO2 SpO₂ baseline percentage before the drop.
 * @property nadirSpO2 Lowest SpO₂ percentage during the event.
 * @property dropPercent Magnitude of the drop: [baselineSpO2] − [nadirSpO2].
 */
data class DesaturationEvent(
    val startTimeMs: Long,
    val nadirTimeMs: Long,
    val endTimeMs: Long,
    val baselineSpO2: Int,
    val nadirSpO2: Int,
    val dropPercent: Int
) {
    init {
        require(dropPercent >= 0) { "dropPercent must be non-negative, was $dropPercent" }
        require(nadirSpO2 <= baselineSpO2) { "nadirSpO2 ($nadirSpO2) must be <= baselineSpO2 ($baselineSpO2)" }
        require(nadirTimeMs in startTimeMs..endTimeMs) { "nadirTimeMs must be between startTimeMs and endTimeMs" }
    }
}

