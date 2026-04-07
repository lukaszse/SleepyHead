package com.example.androidapp.domain.model

/**
 * Immutable snapshot of RMSSD computed at a given point in time.
 *
 * @property timestamp Epoch milliseconds when this snapshot was recorded.
 * @property rmssdMs RMSSD value in milliseconds.
 * @property rrIntervalCount Number of RR intervals used in this window.
 * @property windowDurationMs Actual duration of the sliding window used (ms).
 */
data class HrvSnapshot(
    val timestamp: Long,
    val rmssdMs: Double,
    val rrIntervalCount: Int,
    val windowDurationMs: Long
)

