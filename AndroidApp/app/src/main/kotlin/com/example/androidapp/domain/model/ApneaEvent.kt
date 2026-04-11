package com.example.androidapp.domain.model

/**
 * Value object representing a detected apnea (or hypopnea) event.
 *
 * An event is flagged when multiple independent channels (CVHR, EDR, SpO₂, ACC)
 * agree on a cessation or reduction of breathing for ≥10 seconds.
 *
 * @property startTimeMs Epoch milliseconds when the event began.
 * @property endTimeMs Epoch milliseconds when the event ended.
 * @property type Classification of the apnea event (obstructive, central, mixed).
 * @property confidence Confidence level based on the number of confirming channels.
 * @property spo2Nadir Lowest SpO₂ during the event, or `null` if no oximeter is connected.
 */
data class ApneaEvent(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val type: ApneaType,
    val confidence: Confidence,
    val spo2Nadir: Int? = null
) {
    /** Duration of the apnea event in milliseconds. */
    val durationMs: Long get() = endTimeMs - startTimeMs

    init {
        require(endTimeMs >= startTimeMs) { "endTimeMs must be >= startTimeMs" }
        require(spo2Nadir == null || spo2Nadir in 0..100) {
            "spo2Nadir must be in 0..100 or null, was $spo2Nadir"
        }
    }
}

