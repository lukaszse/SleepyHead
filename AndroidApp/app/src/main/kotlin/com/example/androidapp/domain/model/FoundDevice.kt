package com.example.androidapp.domain.model

/**
 * Value object representing a discovered heart-rate device during BLE scanning.
 *
 * @property deviceId Unique identifier of the device (e.g. "C0680226").
 * @property name Human-readable name of the device (e.g. "Polar H10 C0680226"), or `null` if unknown.
 */
data class FoundDevice(
    val deviceId: String,
    val name: String?
)

