package com.example.androidapp.domain.model

/**
 * Value object representing the Oxygen Desaturation Index (ODI) result.
 *
 * ODI correlates strongly with AHI (r ≈ 0.85–0.95 in severe OSA).
 *
 * @property odi3 Number of ≥3% desaturation events per hour of recording.
 * @property odi4 Number of ≥4% desaturation events per hour of recording.
 * @property desaturationCount Total number of desaturation events (≥3% threshold).
 * @property totalHours Total recording duration in hours.
 */
data class OdiResult(
    val odi3: Double,
    val odi4: Double,
    val desaturationCount: Int,
    val totalHours: Double
) {
    init {
        require(odi3 >= 0.0) { "odi3 must be non-negative, was $odi3" }
        require(odi4 >= 0.0) { "odi4 must be non-negative, was $odi4" }
        require(desaturationCount >= 0) { "desaturationCount must be non-negative" }
        require(totalHours > 0.0) { "totalHours must be positive, was $totalHours" }
    }
}

