package com.example.androidapp.domain.model

/**
 * Classification of respiratory effort derived from the AC (oscillatory)
 * component of the chest-mounted accelerometer signal.
 *
 * This is the key differentiator between obstructive and central apnea:
 * - Obstructive apnea: effort is INCREASED (patient struggles against closed airway)
 * - Central apnea: effort is ABSENT (brain stops sending breathing signal)
 * - Mixed apnea: effort transitions from ABSENT to INCREASED (ONSET_DELAYED)
 */
enum class RespiratoryEffortType {

    /** Normal, regular respiratory movement detected. */
    NORMAL,

    /** Increased or chaotic respiratory effort — suggests obstructive apnea. */
    INCREASED,

    /** No respiratory movement detected — suggests central apnea. */
    ABSENT,

    /** Effort absent initially, then appearing with delay — suggests mixed apnea. */
    ONSET_DELAYED
}

