package com.example.androidapp.domain.model

/**
 * Type of apnea event, classified primarily by the presence or absence of
 * respiratory effort (from the accelerometer channel).
 *
 * Distribution in the general OSA population:
 * - OBSTRUCTIVE: ~84% of cases
 * - CENTRAL: ~1% in isolation, ~15% as a component of mixed
 * - MIXED: ~15%
 */
enum class ApneaType {

    /** Mechanical airway obstruction with preserved respiratory effort. */
    OBSTRUCTIVE,

    /** Cessation of respiratory drive from the CNS — no chest movement. */
    CENTRAL,

    /** Begins as central (no effort), transitions to obstructive (effort present). */
    MIXED
}

