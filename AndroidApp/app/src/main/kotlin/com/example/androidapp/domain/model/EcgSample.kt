package com.example.androidapp.domain.model

/**
 * Value object representing a single raw ECG sample from the sensor.
 *
 * Polar H10 streams ECG at **130 Hz**, where each sample contains the
 * voltage measured by the single-lead electrode on the chest strap.
 *
 * @property timestamp Epoch milliseconds when this sample was captured.
 * @property voltageUv Voltage in microvolts (µV). Typical R-peak amplitude: 500–2000 µV.
 */
data class EcgSample(
    val timestamp: Long,
    val voltageUv: Int
)

