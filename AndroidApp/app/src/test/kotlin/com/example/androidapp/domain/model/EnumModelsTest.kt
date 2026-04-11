package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumModelsTest {

    // ── BodyPosition ────────────────────────────────────────────────

    @Test
    fun `BodyPosition has 6 values`() {
        assertEquals(6, BodyPosition.entries.size)
    }

    @Test
    fun `BodyPosition contains expected values`() {
        val expected = setOf("SUPINE", "LEFT_LATERAL", "RIGHT_LATERAL", "PRONE", "UPRIGHT", "UNKNOWN")
        val actual = BodyPosition.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    // ── RespiratoryEffortType ───────────────────────────────────────

    @Test
    fun `RespiratoryEffortType has 4 values`() {
        assertEquals(4, RespiratoryEffortType.entries.size)
    }

    @Test
    fun `RespiratoryEffortType contains expected values`() {
        val expected = setOf("NORMAL", "INCREASED", "ABSENT", "ONSET_DELAYED")
        val actual = RespiratoryEffortType.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    // ── ApneaType ───────────────────────────────────────────────────

    @Test
    fun `ApneaType has 3 values`() {
        assertEquals(3, ApneaType.entries.size)
    }

    @Test
    fun `ApneaType contains expected values`() {
        val expected = setOf("OBSTRUCTIVE", "CENTRAL", "MIXED")
        val actual = ApneaType.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    // ── Confidence ──────────────────────────────────────────────────

    @Test
    fun `Confidence has 3 values`() {
        assertEquals(3, Confidence.entries.size)
    }

    @Test
    fun `Confidence contains expected values`() {
        val expected = setOf("HIGH", "MEDIUM", "LOW")
        val actual = Confidence.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    // ── SeverityCategory ────────────────────────────────────────────

    @Test
    fun `SeverityCategory has 4 values`() {
        assertEquals(4, SeverityCategory.entries.size)
    }

    @Test
    fun `SeverityCategory contains expected values`() {
        val expected = setOf("NORMAL", "MILD", "MODERATE", "SEVERE")
        val actual = SeverityCategory.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}

