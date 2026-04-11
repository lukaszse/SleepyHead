package com.example.androidapp.domain.model

/**
 * Value object representing a single SpO₂ reading from a pulse oximeter.
 *
 * Pulse oximeters typically report data every 1–4 seconds. This model
 * captures all fields available from BLE PLX (Service `0x1822`) or
 * proprietary protocols.
 *
 * @property timestamp Epoch milliseconds when this sample was received.
 * @property spo2Percent Oxygen saturation in percent (valid range: 70–100).
 * @property pulseRate Pulse rate in beats per minute as measured by the oximeter.
 * @property perfusionIndex Perfusion Index in percent (0.0–20.0), or `null` if not available.
 */
data class SpO2Sample(
    val timestamp: Long,
    val spo2Percent: Int,
    val pulseRate: Int,
    val perfusionIndex: Double? = null
)

