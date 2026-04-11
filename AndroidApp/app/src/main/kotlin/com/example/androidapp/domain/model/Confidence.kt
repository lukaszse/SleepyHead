package com.example.androidapp.domain.model

/**
 * Confidence level assigned to a detected apnea event.
 *
 * Confidence depends on how many independent channels confirm the event:
 * - HIGH: ≥3 channels agree (e.g. CVHR + EDR + SpO₂ desaturation)
 * - MEDIUM: 2 channels agree (e.g. CVHR + EDR cessation)
 * - LOW: single channel or weak evidence
 *
 * In H10-only mode (without SpO₂), all confidence levels are reduced
 * by one step compared to full dual-device mode.
 */
enum class Confidence {

    /** Strong multi-channel evidence. */
    HIGH,

    /** Moderate evidence from at least two channels. */
    MEDIUM,

    /** Weak or single-channel evidence. */
    LOW
}

