package com.example.androidapp.domain.model

/**
 * OSA severity category based on AHI thresholds (AASM classification).
 *
 * | AHI Range   | Category   |
 * |-------------|------------|
 * | < 5         | NORMAL     |
 * | 5 – 14.9    | MILD       |
 * | 15 – 29.9   | MODERATE   |
 * | ≥ 30        | SEVERE     |
 */
enum class SeverityCategory {

    /** AHI < 5 — no sleep apnea. */
    NORMAL,

    /** AHI 5–14.9 — mild OSA. */
    MILD,

    /** AHI 15–29.9 — moderate OSA, daytime sleepiness and sleep fragmentation. */
    MODERATE,

    /** AHI ≥ 30 — severe OSA, significant desaturation and cardiovascular risk. */
    SEVERE
}

