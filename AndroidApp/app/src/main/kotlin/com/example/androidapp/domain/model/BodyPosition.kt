package com.example.androidapp.domain.model

/**
 * Body position during sleep, classified from the DC (gravity) component
 * of the accelerometer signal on the chest strap.
 *
 * Positional OSA is present in ~50–60% of patients (Oksenberg et al., 1997).
 * AHI in SUPINE position can be ≥2× higher than in lateral positions.
 */
enum class BodyPosition {

    /** Lying on the back — highest OSA risk. */
    SUPINE,

    /** Lying on the left side. */
    LEFT_LATERAL,

    /** Lying on the right side. */
    RIGHT_LATERAL,

    /** Lying face down. */
    PRONE,

    /** Sitting or standing — typically not a sleep position. */
    UPRIGHT,

    /** Position could not be determined (e.g. excessive movement, sensor off-body). */
    UNKNOWN
}

