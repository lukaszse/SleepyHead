package com.example.androidapp.domain.model

/**
 * Value object representing a single 60-second scoring epoch.
 *
 * Each epoch aggregates features from all active channels (CVHR, EDR, ACC, SpO₂)
 * and is classified by the [ApneaScorer] as containing an apnea event or not.
 *
 * @property epochStartMs Epoch milliseconds marking the start of this 60-second window.
 * @property features Map of feature name → value extracted during this epoch (see CONCEPT-001 §8.2).
 *   Values are typically numeric scores; some keys may hold lists or enums for richer channel state.
 * @property eventDetected Whether an apnea/hypopnea event was detected in this epoch.
 * @property apneaEvent The detected [ApneaEvent], or `null` if no event was found.
 * @property bodyPosition Body position during this epoch, or [BodyPosition.UNKNOWN] if unavailable.
 */
data class ApneaEpoch(
    val epochStartMs: Long,
    val features: Map<String, Any>,
    val eventDetected: Boolean,
    val apneaEvent: ApneaEvent? = null,
    val bodyPosition: BodyPosition = BodyPosition.UNKNOWN
) {
    companion object {
        /** Standard epoch duration in milliseconds (60 seconds). */
        const val EPOCH_DURATION_MS: Long = 60_000L
    }
}

