package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NightReportTest {

    // ── estimatedAhi ────────────────────────────────────────────────

    @Test
    fun `estimatedAhi returns null when endTime is null`() {
        val report = NightReport(id = "r1", startTimeMs = 0L, endTimeMs = null)
        assertNull(report.estimatedAhi)
    }

    @Test
    fun `estimatedAhi returns null when duration less than 1 hour`() {
        val report = NightReport(
            id = "r1",
            startTimeMs = 0L,
            endTimeMs = 59 * 60 * 1000L // 59 min
        )
        assertNull(report.estimatedAhi)
    }

    @Test
    fun `estimatedAhi computes correctly for 8 hour recording`() {
        val eightHoursMs = 8 * 3_600_000L
        val events = (1..40).map {
            ApneaEvent(
                startTimeMs = it * 100_000L,
                endTimeMs = it * 100_000L + 15_000L,
                type = ApneaType.OBSTRUCTIVE,
                confidence = Confidence.MEDIUM
            )
        }
        val report = NightReport(
            id = "r1",
            startTimeMs = 0L,
            endTimeMs = eightHoursMs,
            apneaEvents = events
        )
        // 40 events / 8 hours = 5.0
        assertEquals(5.0, report.estimatedAhi!!, 0.001)
    }

    @Test
    fun `estimatedAhi is zero when no events`() {
        val report = NightReport(
            id = "r1",
            startTimeMs = 0L,
            endTimeMs = 2 * 3_600_000L,
            apneaEvents = emptyList()
        )
        assertEquals(0.0, report.estimatedAhi!!, 0.001)
    }

    // ── severityCategory ────────────────────────────────────────────

    @Test
    fun `severityCategory returns null when eAHI not available`() {
        val report = NightReport(id = "r1", startTimeMs = 0L, endTimeMs = null)
        assertNull(report.severityCategory)
    }

    @Test
    fun `severityCategory NORMAL when eAHI less than 5`() {
        val report = createReportWithEvents(eventCount = 4, durationHours = 2) // AHI = 2.0
        assertEquals(SeverityCategory.NORMAL, report.severityCategory)
    }

    @Test
    fun `severityCategory MILD when eAHI between 5 and 15`() {
        val report = createReportWithEvents(eventCount = 20, durationHours = 2) // AHI = 10.0
        assertEquals(SeverityCategory.MILD, report.severityCategory)
    }

    @Test
    fun `severityCategory MODERATE when eAHI between 15 and 30`() {
        val report = createReportWithEvents(eventCount = 40, durationHours = 2) // AHI = 20.0
        assertEquals(SeverityCategory.MODERATE, report.severityCategory)
    }

    @Test
    fun `severityCategory SEVERE when eAHI 30 or more`() {
        val report = createReportWithEvents(eventCount = 60, durationHours = 2) // AHI = 30.0
        assertEquals(SeverityCategory.SEVERE, report.severityCategory)
    }

    // ── durationMs ──────────────────────────────────────────────────

    @Test
    fun `durationMs returns null when endTime is null`() {
        val report = NightReport(id = "r1", startTimeMs = 0L, endTimeMs = null)
        assertNull(report.durationMs)
    }

    @Test
    fun `durationMs returns correct value`() {
        val report = NightReport(id = "r1", startTimeMs = 1000L, endTimeMs = 5000L)
        assertEquals(4000L, report.durationMs)
    }

    // ── bodyPositionDistribution ────────────────────────────────────

    @Test
    fun `bodyPositionDistribution is empty when no epochs`() {
        val report = NightReport(id = "r1", startTimeMs = 0L)
        assertTrue(report.bodyPositionDistribution.isEmpty())
    }

    @Test
    fun `bodyPositionDistribution computes percentages`() {
        val epochs = listOf(
            createEpoch(0L, BodyPosition.SUPINE),
            createEpoch(60000L, BodyPosition.SUPINE),
            createEpoch(120000L, BodyPosition.LEFT_LATERAL),
            createEpoch(180000L, BodyPosition.LEFT_LATERAL),
            createEpoch(240000L, BodyPosition.LEFT_LATERAL),
        )
        val report = NightReport(id = "r1", startTimeMs = 0L, epochs = epochs)
        val dist = report.bodyPositionDistribution
        assertEquals(40.0, dist[BodyPosition.SUPINE]!!, 0.001)
        assertEquals(60.0, dist[BodyPosition.LEFT_LATERAL]!!, 0.001)
    }

    // ── epochsWithEvents ────────────────────────────────────────────

    @Test
    fun `epochsWithEvents counts correctly`() {
        val epochs = listOf(
            createEpoch(0L, eventDetected = true),
            createEpoch(60000L, eventDetected = false),
            createEpoch(120000L, eventDetected = true),
        )
        val report = NightReport(id = "r1", startTimeMs = 0L, epochs = epochs)
        assertEquals(2, report.epochsWithEvents)
    }

    @Test
    fun `epochsWithEvents is zero when no events`() {
        val epochs = listOf(createEpoch(0L, eventDetected = false))
        val report = NightReport(id = "r1", startTimeMs = 0L, epochs = epochs)
        assertEquals(0, report.epochsWithEvents)
    }

    // ── defaults ────────────────────────────────────────────────────

    @Test
    fun `defaults to empty lists and no SpO2`() {
        val report = NightReport(id = "r1", startTimeMs = 0L)
        assertTrue(report.epochs.isEmpty())
        assertTrue(report.apneaEvents.isEmpty())
        assertTrue(report.cvhrCycles.isEmpty())
        assertNull(report.odiResult)
        assertFalse(report.spo2Available)
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun createReportWithEvents(eventCount: Int, durationHours: Int): NightReport {
        val durationMs = durationHours * 3_600_000L
        val events = (1..eventCount).map {
            ApneaEvent(
                startTimeMs = it * 10_000L,
                endTimeMs = it * 10_000L + 15_000L,
                type = ApneaType.OBSTRUCTIVE,
                confidence = Confidence.MEDIUM
            )
        }
        return NightReport(
            id = "test",
            startTimeMs = 0L,
            endTimeMs = durationMs,
            apneaEvents = events
        )
    }

    private fun createEpoch(
        startMs: Long,
        position: BodyPosition = BodyPosition.UNKNOWN,
        eventDetected: Boolean = false
    ): ApneaEpoch = ApneaEpoch(
        epochStartMs = startMs,
        features = emptyMap(),
        eventDetected = eventDetected,
        bodyPosition = position
    )
}

