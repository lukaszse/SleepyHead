package com.example.androidapp.domain.model

/**
 * Value object representing heart rate data read from a sensor.
 *
 * @property bpm Current heart rate in beats per minute ([Int]).
 * @property rrIntervals List of R-R intervals in milliseconds ([List] of [Int]).
 */
data class HrData(
    val bpm: Int,
    val rrIntervals: List<Int>
)

