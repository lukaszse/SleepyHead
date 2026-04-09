package com.example.androidapp.domain.model

/**
 * Aggregate representing one HRV recording session.
 *
 * A session starts when the BLE device connects and ends when the user
 * disconnects or when no RR data is received for ≥ 2 hours (auto-end).
 *
 * @property id Unique session identifier (UUID).
 * @property startTime Epoch milliseconds when the session started.
 * @property endTime Epoch milliseconds when the session ended, or `null` while active.
 * @property snapshots Ordered list of per-minute RMSSD snapshots recorded during the session.
 */
data class HrvSession(
    val id: String,
    val startTime: Long,
    val endTime: Long? = null,
    val snapshots: List<HrvSnapshot> = emptyList()
) {

    /**
     * Running average of all RMSSD snapshots in this session.
     * Returns `null` if no snapshots have been recorded yet.
     */
    val averageRmssd: Double? =
        if (snapshots.isEmpty()) null
        else snapshots.map { it.rmssdMs }.average()
}
