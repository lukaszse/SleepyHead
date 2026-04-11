package com.example.androidapp.domain.model

/**
 * Aggregate representing a complete overnight sleep apnea screening report.
 *
 * Contains all data collected during one night: detected apnea events,
 * CVHR cycles, body position statistics, and the estimated AHI (eAHI).
 *
 * @property id Unique report identifier (UUID).
 * @property startTimeMs Epoch milliseconds when the recording started.
 * @property endTimeMs Epoch milliseconds when the recording ended, or `null` if still active.
 * @property epochs Ordered list of 60-second scoring epochs.
 * @property apneaEvents All detected apnea/hypopnea events during the night.
 * @property cvhrCycles All detected CVHR (bradycardia→tachycardia) cycles.
 * @property odiResult ODI calculation result, or `null` if no pulse oximeter was used.
 * @property spo2Available Whether a pulse oximeter was connected during the recording.
 */
data class NightReport(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val epochs: List<ApneaEpoch> = emptyList(),
    val apneaEvents: List<ApneaEvent> = emptyList(),
    val cvhrCycles: List<CvhrCycle> = emptyList(),
    val odiResult: OdiResult? = null,
    val spo2Available: Boolean = false
) {
    /**
     * Estimated Apnea-Hypopnea Index (eAHI): number of detected events per hour
     * of recording time.
     *
     * Returns `null` if the recording is still active ([endTimeMs] is `null`)
     * or the recording duration is less than 1 hour.
     */
    val estimatedAhi: Double?
        get() {
            val end = endTimeMs ?: return null
            val durationHours = (end - startTimeMs) / 3_600_000.0
            if (durationHours < 1.0) return null
            return apneaEvents.size / durationHours
        }

    /**
     * Duration of the recording in milliseconds, or `null` if still active.
     */
    val durationMs: Long?
        get() = endTimeMs?.let { it - startTimeMs }

    /**
     * Map of [BodyPosition] to percentage of time spent in that position.
     * Returns an empty map if no epochs are available.
     */
    val bodyPositionDistribution: Map<BodyPosition, Double>
        get() {
            if (epochs.isEmpty()) return emptyMap()
            return epochs.groupBy { it.bodyPosition }
                .mapValues { (_, v) -> v.size.toDouble() / epochs.size * 100.0 }
        }

    /**
     * Number of epochs where an apnea event was detected.
     */
    val epochsWithEvents: Int
        get() = epochs.count { it.eventDetected }

    /**
     * AHI severity category based on [estimatedAhi].
     * Returns `null` if eAHI is not yet available.
     */
    val severityCategory: SeverityCategory?
        get() {
            val eahi = estimatedAhi ?: return null
            return when {
                eahi < 5.0 -> SeverityCategory.NORMAL
                eahi < 15.0 -> SeverityCategory.MILD
                eahi < 30.0 -> SeverityCategory.MODERATE
                else -> SeverityCategory.SEVERE
            }
        }
}

