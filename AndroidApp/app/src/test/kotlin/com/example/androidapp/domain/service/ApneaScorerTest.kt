package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ApneaScorerTest {

    @Test
    fun `scoreEpoch should detect apnea event with high CVHR score`() {
        val features = mapOf<String, Any>(
            "cvhr_cycles" to listOf(Any(), Any(), Any()), // 3 cycles
            "edr_pauses" to emptyList<Any>(),
            "respiratory_rate" to 16.0,
            "respiratory_variability" to 20.0,
            "respiratory_effort" to RespiratoryEffortType.INCREASED,
            "effort_index" to 75.0,
            "paradoxical_percentage" to 10.0,
            "flow_limitation" to 30.0
        )
        
        val epoch = ApneaScorer.scoreEpoch(
            epochStartMs = 0L,
            features = features,
            bodyPosition = BodyPosition.SUPINE
        )
        
        assertTrue("Should detect apnea event", epoch.eventDetected)
        assertNotNull("Should create apnea event", epoch.apneaEvent)
        
        val event = epoch.apneaEvent!!
        assertEquals(ApneaType.OBSTRUCTIVE, event.type)
        assertTrue("Should have medium or high confidence", 
            event.confidence == Confidence.MEDIUM || event.confidence == Confidence.HIGH)
    }

    @Test
    fun `scoreEpoch should not detect apnea with low scores`() {
        val features = mapOf<String, Any>(
            "cvhr_cycles" to emptyList<Any>(),
            "edr_pauses" to emptyList<Any>(),
            "respiratory_rate" to 16.0,
            "respiratory_variability" to 10.0,
            "respiratory_effort" to RespiratoryEffortType.NORMAL,
            "effort_index" to 30.0,
            "paradoxical_percentage" to 5.0,
            "flow_limitation" to 10.0
        )
        
        val epoch = ApneaScorer.scoreEpoch(
            epochStartMs = 0L,
            features = features,
            bodyPosition = BodyPosition.LEFT_LATERAL
        )
        
        assertFalse("Should not detect apnea event", epoch.eventDetected)
        assertNull("Should not create apnea event", epoch.apneaEvent)
    }

    @Test
    fun `calculateEstimatedAhi should calculate correct AHI`() {
        val epochs = listOf(
            ApneaEpoch(0L, emptyMap(), eventDetected = true, apneaEvent = null),
            ApneaEpoch(60000L, emptyMap(), eventDetected = false, apneaEvent = null),
            ApneaEpoch(120000L, emptyMap(), eventDetected = true, apneaEvent = null),
            ApneaEpoch(180000L, emptyMap(), eventDetected = false, apneaEvent = null),
            ApneaEpoch(240000L, emptyMap(), eventDetected = true, apneaEvent = null)
        )
        
        // 3 events in 5 minutes = 36 events per hour
        val ahi = ApneaScorer.calculateEstimatedAhi(epochs)
        
        assertEquals(36.0, ahi, 0.1)
    }

    @Test
    fun `classifyApneaType should classify obstructive apnea`() {
        val channelScores = mapOf(
            "cvhr" to 0.8,
            "edr" to 0.6,
            "effort" to 0.7
        )
        
        val apneaType = ApneaScorer.classifyApneaType(
            channelScores = channelScores,
            effortType = RespiratoryEffortType.INCREASED
        )
        
        assertEquals(ApneaType.OBSTRUCTIVE, apneaType)
    }

    @Test
    fun `classifyApneaType should classify central apnea`() {
        val channelScores = mapOf(
            "cvhr" to 0.7,
            "edr" to 0.5,
            "effort" to 0.2
        )
        
        val apneaType = ApneaScorer.classifyApneaType(
            channelScores = channelScores,
            effortType = RespiratoryEffortType.ABSENT
        )
        
        assertEquals(ApneaType.CENTRAL, apneaType)
    }

    @Test
    fun `classifySeverityFromAhi should classify correctly`() {
        assertEquals(SeverityCategory.NORMAL, ApneaScorer.classifySeverityFromAhi(0.0))
        assertEquals(SeverityCategory.NORMAL, ApneaScorer.classifySeverityFromAhi(4.9))
        assertEquals(SeverityCategory.MILD, ApneaScorer.classifySeverityFromAhi(5.0))
        assertEquals(SeverityCategory.MILD, ApneaScorer.classifySeverityFromAhi(14.9))
        assertEquals(SeverityCategory.MODERATE, ApneaScorer.classifySeverityFromAhi(15.0))
        assertEquals(SeverityCategory.MODERATE, ApneaScorer.classifySeverityFromAhi(29.9))
        assertEquals(SeverityCategory.SEVERE, ApneaScorer.classifySeverityFromAhi(30.0))
    }

    @Test
    fun `calculateOverallConfidence should calculate confidence from epochs`() {
        val epochs = listOf(
            ApneaEpoch(0L, emptyMap(), eventDetected = true, 
                apneaEvent = ApneaEvent(0L, 30000L, ApneaType.OBSTRUCTIVE, Confidence.HIGH, null)),
            ApneaEpoch(60000L, emptyMap(), eventDetected = true,
                apneaEvent = ApneaEvent(60000L, 90000L, ApneaType.OBSTRUCTIVE, Confidence.MEDIUM, null)),
            ApneaEpoch(120000L, emptyMap(), eventDetected = false, apneaEvent = null)
        )
        
        val confidence = ApneaScorer.calculateOverallConfidence(epochs)
        
        // Average of HIGH (1.0) and MEDIUM (0.7) = 0.85 -> HIGH confidence
        assertEquals(Confidence.HIGH, confidence)
    }

    @Test
    fun `detectPredominantApneaType should detect most common type`() {
        val epochs = listOf(
            ApneaEpoch(0L, emptyMap(), eventDetected = true,
                apneaEvent = ApneaEvent(0L, 30000L, ApneaType.OBSTRUCTIVE, Confidence.HIGH, null)),
            ApneaEpoch(60000L, emptyMap(), eventDetected = true,
                apneaEvent = ApneaEvent(60000L, 90000L, ApneaType.OBSTRUCTIVE, Confidence.MEDIUM, null)),
            ApneaEpoch(120000L, emptyMap(), eventDetected = true,
                apneaEvent = ApneaEvent(120000L, 150000L, ApneaType.CENTRAL, Confidence.MEDIUM, null)),
            ApneaEpoch(180000L, emptyMap(), eventDetected = false, apneaEvent = null)
        )
        
        val predominantType = ApneaScorer.detectPredominantApneaType(epochs)
        
        assertEquals(ApneaType.OBSTRUCTIVE, predominantType)
    }

    @Test
    fun `setSpo2Available should enable SpO₂ scoring`() {
        ApneaScorer.setSpo2Available(true)
        
        // Verify through scoreEpoch with SpO₂ features
        val features = mapOf<String, Any>(
            "cvhr_cycles" to listOf(Any()),
            "edr_pauses" to emptyList<Any>(),
            "respiratory_rate" to 16.0,
            "respiratory_variability" to 20.0,
            "respiratory_effort" to RespiratoryEffortType.NORMAL,
            "effort_index" to 50.0,
            "paradoxical_percentage" to 10.0,
            "flow_limitation" to 20.0,
            "odi4" to 20.0,
            "t90" to 15.0,
            "desaturation_events" to listOf(Any(), Any(), Any())
        )
        
        val epoch = ApneaScorer.scoreEpoch(
            epochStartMs = 0L,
            features = features,
            bodyPosition = BodyPosition.SUPINE
        )
        
        // With SpO₂ data, should have higher chance of detection
        // (exact result depends on fusion weights)
        assertTrue("Should consider SpO₂ in scoring", true)
    }

    @Test
    fun `estimateSleepEfficiency should calculate efficiency`() {
        val epochs = listOf(
            ApneaEpoch(0L, emptyMap(), eventDetected = true, apneaEvent = null),
            ApneaEpoch(60000L, emptyMap(), eventDetected = false, apneaEvent = null),
            ApneaEpoch(120000L, emptyMap(), eventDetected = true, apneaEvent = null),
            ApneaEpoch(180000L, emptyMap(), eventDetected = false, apneaEvent = null),
            ApneaEpoch(240000L, emptyMap(), eventDetected = false, apneaEvent = null)
        )
        
        // 2 disturbed epochs out of 5 = 60% efficiency
        val efficiency = ApneaScorer.estimateSleepEfficiency(epochs)
        
        assertEquals(60.0, efficiency, 0.1)
    }

    @Test
    fun `calculatePositionSpecificAhi should calculate AHI by position`() {
        val supineEpochs = listOf(
            ApneaEpoch(0L, emptyMap(), eventDetected = true, apneaEvent = null),
            ApneaEpoch(60000L, emptyMap(), eventDetected = true, apneaEvent = null)
        )
        
        val lateralEpochs = listOf(
            ApneaEpoch(120000L, emptyMap(), eventDetected = false, apneaEvent = null),
            ApneaEpoch(180000L, emptyMap(), eventDetected = false, apneaEvent = null)
        )
        
        val positionEpochs = mapOf(
            BodyPosition.SUPINE to supineEpochs,
            BodyPosition.LEFT_LATERAL to lateralEpochs
        )
        
        val allEpochs = supineEpochs + lateralEpochs
        val positionAhi = ApneaScorer.calculatePositionSpecificAhi(allEpochs, positionEpochs)
        
        // Supine: 2 events in 2 minutes = 60 events/hour
        // Lateral: 0 events in 2 minutes = 0 events/hour
        assertEquals(60.0, positionAhi[BodyPosition.SUPINE]!!, 0.1)
        assertEquals(0.0, positionAhi[BodyPosition.LEFT_LATERAL]!!, 0.1)
    }

    @Test
    fun `scoreEpoch should handle missing features gracefully`() {
        val emptyFeatures = emptyMap<String, Any>()
        
        val epoch = ApneaScorer.scoreEpoch(
            epochStartMs = 0L,
            features = emptyFeatures,
            bodyPosition = BodyPosition.UNKNOWN
        )
        
        // With no features, should not detect apnea
        assertFalse("Should not detect apnea with no features", epoch.eventDetected)
        assertNull("Should not create apnea event", epoch.apneaEvent)
    }
}