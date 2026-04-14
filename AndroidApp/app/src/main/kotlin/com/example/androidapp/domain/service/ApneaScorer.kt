package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.ApneaEpoch
import com.example.androidapp.domain.model.ApneaEvent
import com.example.androidapp.domain.model.ApneaType
import com.example.androidapp.domain.model.BodyPosition
import com.example.androidapp.domain.model.Confidence
import com.example.androidapp.domain.model.RespiratoryEffortType
import kotlin.math.max
import kotlin.math.min

/**
 * Apnea event scorer that fuses multiple sensor channels for sleep apnea detection.
 *
 * Implements rule-based scoring according to AASM guidelines with multi-channel
 * sensor fusion for improved accuracy.
 *
 * Based on: American Academy of Sleep Medicine. "The AASM Manual for the
 * Scoring of Sleep and Associated Events: Rules, Terminology and Technical
 * Specifications." Version 2.6. 2020.
 */
object ApneaScorer {

    // Epoch duration (standard: 60 seconds for sleep staging)
    const val EPOCH_DURATION_MS = 60000L
    
    // Scoring thresholds
    private const val MIN_APNEA_DURATION_MS = 10000L    // 10 seconds minimum
    private const val MAX_APNEA_DURATION_MS = 120000L   // 120 seconds maximum
    
    // Channel weights for sensor fusion
    private const val CVHR_WEIGHT = 0.4
    private const val EDR_WEIGHT = 0.3
    private const val EFFORT_WEIGHT = 0.2
    private const val POSITION_WEIGHT = 0.1
    
    // Minimum confidence thresholds
    private const val HIGH_CONFIDENCE_THRESHOLD = 0.7
    private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.4
    
    // SpO₂ availability flag (for Milestone 2)
    private var spo2Available = false

    /**
     * Scores apnea events for a 60-second epoch using multi-channel fusion.
     *
     * @param epochStartMs Start timestamp of epoch
     * @param features Map of extracted features from all channels
     * @param bodyPosition Predominant body position during epoch
     * @return ApneaEpoch with scoring results
     */
    fun scoreEpoch(
        epochStartMs: Long,
        features: Map<String, Any>,
        bodyPosition: BodyPosition
    ): ApneaEpoch {
        // 1. Extract features from each channel
        val cvhrScore = extractCvhrScore(features)
        val edrScore = extractEdrScore(features)
        val effortScore = extractEffortScore(features)
        val positionScore = extractPositionScore(bodyPosition)
        val spo2Score = if (spo2Available) extractSpo2Score(features) else 0.0
        val effortTypeFromFeatures = features["respiratory_effort"] as? RespiratoryEffortType
            ?: RespiratoryEffortType.NORMAL
        
        // 2. Fuse scores using weighted combination
        val fusedScore = fuseChannelScores(
            cvhrScore, edrScore, effortScore, positionScore, spo2Score
        )
        
        // 3. Determine if apnea event is detected
        val eventDetected = fusedScore >= MEDIUM_CONFIDENCE_THRESHOLD
        
        // 4. Create apnea event if detected
        val apneaEvent = if (eventDetected) {
            createApneaEvent(
                epochStartMs = epochStartMs,
                fusedScore = fusedScore,
                channelScores = mapOf(
                    "cvhr" to cvhrScore,
                    "edr" to edrScore,
                    "effort" to effortScore,
                    "position" to positionScore,
                    "spo2" to spo2Score
                ),
                respiratoryEffortType = effortTypeFromFeatures
            )
        } else {
            null
        }
        
        return ApneaEpoch(
            epochStartMs = epochStartMs,
            features = features,
            eventDetected = eventDetected,
            apneaEvent = apneaEvent
        )
    }

    /**
     * Sets SpO₂ availability flag (for Milestone 2).
     */
    fun setSpo2Available(available: Boolean) {
        spo2Available = available
    }

    /**
     * Calculates estimated AHI from scored epochs.
     *
     * @param epochs List of scored epochs
     * @return Estimated Apnea-Hypopnea Index (events per hour)
     */
    fun calculateEstimatedAhi(epochs: List<ApneaEpoch>): Double {
        if (epochs.isEmpty()) {
            return 0.0
        }
        
        val eventCount = epochs.count { it.eventDetected }
        val totalHours = epochs.size * EPOCH_DURATION_MS / (1000.0 * 60 * 60)
        
        return if (totalHours > 0) eventCount / totalHours else 0.0
    }

    /**
     * Classifies apnea type based on channel patterns.
     *
     * @param channelScores Individual channel scores
     * @param effortType Respiratory effort pattern
     * @return Apnea type classification
     */
    fun classifyApneaType(
        channelScores: Map<String, Double>,
        effortType: RespiratoryEffortType
    ): ApneaType {
        val cvhrScore = channelScores["cvhr"] ?: 0.0
        val edrScore = channelScores["edr"] ?: 0.0
        val effortScore = channelScores["effort"] ?: 0.0
        
        return when {
            // Obstructive: CVHR present + respiratory effort present/paradoxical
            cvhrScore > 0.6 && effortScore > 0.4 -> {
                when (effortType) {
                    RespiratoryEffortType.INCREASED,
                    RespiratoryEffortType.ONSET_DELAYED -> ApneaType.OBSTRUCTIVE
                    else -> ApneaType.MIXED
                }
            }
            
            // Central: CVHR present + absent/reduced respiratory effort
            cvhrScore > 0.6 && effortScore < 0.3 -> ApneaType.CENTRAL
            
            // Mixed: features of both
            cvhrScore > 0.4 && edrScore > 0.4 && effortScore in 0.3..0.6 -> ApneaType.MIXED
            
            // Default to obstructive (most common)
            else -> ApneaType.OBSTRUCTIVE
        }
    }

    // =========================================================================
    // Feature Extraction
    // =========================================================================

    private fun extractCvhrScore(features: Map<String, Any>): Double {
        val cvhrCycles = features["cvhr_cycles"] as? List<*> ?: emptyList<Any>()
        val cvhrCount = cvhrCycles.size
        
        // Score based on number of CVHR cycles in epoch
        return when {
            cvhrCount >= 3 -> 1.0
            cvhrCount == 2 -> 0.7
            cvhrCount == 1 -> 0.4
            else -> 0.0
        }
    }

    private fun extractEdrScore(features: Map<String, Any>): Double {
        val edrPauses = features["edr_pauses"] as? List<*> ?: emptyList<Any>()
        val hasEdrPause = edrPauses.isNotEmpty()
        
        val respiratoryRate = features["respiratory_rate"] as? Double ?: 0.0
        val rateVariability = features["respiratory_variability"] as? Double ?: 0.0
        
        var score = 0.0
        
        // Points for respiratory pauses
        if (hasEdrPause) score += 0.5
        
        // Points for abnormal respiratory rate (<8 or >24 BPM)
        if (respiratoryRate < 8.0 || respiratoryRate > 24.0) score += 0.3
        
        // Points for high variability
        if (rateVariability > 30.0) score += 0.2
        
        return min(1.0, score)
    }

    private fun extractEffortScore(features: Map<String, Any>): Double {
        val effortType = features["respiratory_effort"] as? RespiratoryEffortType 
            ?: RespiratoryEffortType.NORMAL
        
        val effortIndex = features["effort_index"] as? Double ?: 50.0
        val paradoxicalPercentage = features["paradoxical_percentage"] as? Double ?: 0.0
        val flowLimitation = features["flow_limitation"] as? Double ?: 0.0
        
        var score = 0.0
        
        // Base score from effort type
        score += when (effortType) {
            RespiratoryEffortType.ABSENT -> 0.8
            RespiratoryEffortType.ONSET_DELAYED -> 0.7
            RespiratoryEffortType.INCREASED -> 0.5
            RespiratoryEffortType.NORMAL -> 0.2
        }
        
        // Adjust based on quantitative measures
        if (effortIndex > 70.0) score += 0.1
        if (paradoxicalPercentage > 30.0) score += 0.1
        if (flowLimitation > 50.0) score += 0.1
        
        return min(1.0, score)
    }

    private fun extractPositionScore(bodyPosition: BodyPosition): Double {
        // Supine position carries highest risk for OSA
        return when (bodyPosition) {
            BodyPosition.SUPINE -> 0.8
            BodyPosition.PRONE -> 0.5
            BodyPosition.UPRIGHT -> 0.3
            BodyPosition.LEFT_LATERAL -> 0.2
            BodyPosition.RIGHT_LATERAL -> 0.2
            BodyPosition.UNKNOWN -> 0.5
        }
    }

    private fun extractSpo2Score(features: Map<String, Any>): Double {
        if (!spo2Available) return 0.0
        
        val odi3 = features["odi3"] as? Double ?: 0.0
        val odi4 = features["odi4"] as? Double ?: 0.0
        val t90 = features["t90"] as? Double ?: 0.0
        val desaturationEvents = features["desaturation_events"] as? List<*> ?: emptyList<Any>()
        
        var score = 0.0
        
        // Points for ODI
        when {
            odi4 >= 15.0 -> score += 0.6
            odi4 >= 5.0 -> score += 0.4
            odi3 >= 5.0 -> score += 0.3
        }
        
        // Points for T90
        if (t90 > 10.0) score += 0.2
        if (t90 > 30.0) score += 0.2
        
        // Points for desaturation events
        if (desaturationEvents.size >= 3) score += 0.2
        
        return min(1.0, score)
    }

    // =========================================================================
    // Sensor Fusion
    // =========================================================================

    private fun fuseChannelScores(
        cvhrScore: Double,
        edrScore: Double,
        effortScore: Double,
        positionScore: Double,
        spo2Score: Double
    ): Double {
        // Adjust weights based on SpO₂ availability
        val totalWeight = CVHR_WEIGHT + EDR_WEIGHT + EFFORT_WEIGHT + POSITION_WEIGHT +
                         (if (spo2Available) 0.2 else 0.0)
        
        val spo2Weight = if (spo2Available) 0.2 else 0.0
        
        // Weighted average
        val fusedScore = (cvhrScore * CVHR_WEIGHT +
                         edrScore * EDR_WEIGHT +
                         effortScore * EFFORT_WEIGHT +
                         positionScore * POSITION_WEIGHT +
                         spo2Score * spo2Weight) / totalWeight
        
        return min(1.0, max(0.0, fusedScore))
    }

    // =========================================================================
    // Event Creation
    // =========================================================================

    private fun createApneaEvent(
        epochStartMs: Long,
        fusedScore: Double,
        channelScores: Map<String, Double>,
        respiratoryEffortType: RespiratoryEffortType
    ): ApneaEvent {
        val confidence = when {
            fusedScore >= HIGH_CONFIDENCE_THRESHOLD -> Confidence.HIGH
            fusedScore >= MEDIUM_CONFIDENCE_THRESHOLD -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        
        val apneaType = classifyApneaType(channelScores, respiratoryEffortType)
        
        // Estimate duration based on score (higher score = longer event)
        val durationMs = when (confidence) {
            Confidence.HIGH -> 45000L // 45 seconds
            Confidence.MEDIUM -> 30000L // 30 seconds
            Confidence.LOW -> 15000L // 15 seconds
        }
        
        return ApneaEvent(
            startTimeMs = epochStartMs,
            endTimeMs = epochStartMs + durationMs,
            type = apneaType,
            confidence = confidence,
            spo2Nadir = if (spo2Available) estimateSpo2Nadir(channelScores) else null
        )
    }

    private fun estimateSpo2Nadir(channelScores: Map<String, Double>): Int? {
        if (!spo2Available) return null
        
        val spo2Score = channelScores["spo2"] ?: 0.0
        
        // Estimate SpO₂ nadir based on spo2Score
        return when {
            spo2Score > 0.8 -> 85 // Severe desaturation
            spo2Score > 0.6 -> 88 // Moderate desaturation
            spo2Score > 0.4 -> 91 // Mild desaturation
            else -> 94 // Minimal desaturation
        }
    }

    // =========================================================================
    // Utility Functions
    // =========================================================================

    /**
     * Calculates sleep apnea severity from estimated AHI.
     */
    fun classifySeverityFromAhi(ahi: Double): com.example.androidapp.domain.model.SeverityCategory {
        return when {
            ahi < 5.0 -> com.example.androidapp.domain.model.SeverityCategory.NORMAL
            ahi < 15.0 -> com.example.androidapp.domain.model.SeverityCategory.MILD
            ahi < 30.0 -> com.example.androidapp.domain.model.SeverityCategory.MODERATE
            else -> com.example.androidapp.domain.model.SeverityCategory.SEVERE
        }
    }

    /**
     * Calculates overall confidence from multiple epochs.
     */
    fun calculateOverallConfidence(epochs: List<ApneaEpoch>): Confidence {
        if (epochs.isEmpty()) {
            return Confidence.LOW
        }
        
        val eventEpochs = epochs.filter { it.eventDetected }
        if (eventEpochs.isEmpty()) {
            return Confidence.LOW
        }
        
        val avgConfidence = eventEpochs
            .mapNotNull { it.apneaEvent?.confidence }
            .map { confidence ->
                when (confidence) {
                    Confidence.HIGH -> 1.0
                    Confidence.MEDIUM -> 0.7
                    Confidence.LOW -> 0.4
                }
            }
            .average()
        
        return when {
            avgConfidence >= 0.85 -> Confidence.HIGH
            avgConfidence >= 0.6 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    /**
     * Detects predominant apnea type from scored epochs.
     */
    fun detectPredominantApneaType(epochs: List<ApneaEpoch>): ApneaType {
        val eventEpochs = epochs.filter { it.eventDetected && it.apneaEvent != null }
        
        if (eventEpochs.isEmpty()) {
            return ApneaType.OBSTRUCTIVE // Default
        }
        
        val typeCounts = eventEpochs
            .mapNotNull { it.apneaEvent?.type }
            .groupingBy { it }
            .eachCount()
        
        return typeCounts.maxByOrNull { it.value }?.key ?: ApneaType.OBSTRUCTIVE
    }

    /**
     * Calculates position-specific AHI (supine vs non-supine).
     */
    fun calculatePositionSpecificAhi(
        epochs: List<ApneaEpoch>,
        positionEpochs: Map<BodyPosition, List<ApneaEpoch>>
    ): Map<BodyPosition, Double> {
        val result = mutableMapOf<BodyPosition, Double>()
        
        positionEpochs.forEach { (position, positionEpochList) ->
            val ahi = calculateEstimatedAhi(positionEpochList)
            result[position] = ahi
        }
        
        return result
    }

    /**
     * Estimates sleep efficiency from scored epochs.
     * Assumes epochs with events represent disturbed sleep.
     */
    fun estimateSleepEfficiency(epochs: List<ApneaEpoch>): Double {
        if (epochs.isEmpty()) {
            return 100.0
        }
        
        val disturbedEpochs = epochs.count { it.eventDetected }
        val efficiency = 100.0 - (disturbedEpochs * 100.0 / epochs.size)
        
        return max(0.0, min(100.0, efficiency))
    }
}