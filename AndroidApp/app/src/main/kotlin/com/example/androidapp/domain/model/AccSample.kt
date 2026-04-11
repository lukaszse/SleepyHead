package com.example.androidapp.domain.model

/**
 * Value object representing a single 3-axis accelerometer sample from the sensor.
 *
 * Polar H10 streams accelerometer data at 25–200 Hz. The sensor is placed on
 * the chest, so axis Z primarily captures respiratory chest movement while
 * the DC (gravity) component encodes body position.
 *
 * @property timestamp Epoch milliseconds when this sample was captured.
 * @property xMg Acceleration on the X axis in milli-g (left ↔ right).
 * @property yMg Acceleration on the Y axis in milli-g (up ↔ down).
 * @property zMg Acceleration on the Z axis in milli-g (front ↔ back).
 */
data class AccSample(
    val timestamp: Long,
    val xMg: Int,
    val yMg: Int,
    val zMg: Int
)

