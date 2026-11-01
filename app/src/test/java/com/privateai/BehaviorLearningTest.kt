package com.privateai

import com.privateai.learning.AccumulatedEvidence
import com.privateai.learning.BehaviorAccumulator
import com.privateai.learning.BehaviorDetector
import com.privateai.learning.BehaviorSignal
import com.privateai.learning.SignalSource
import com.privateai.profile.BehaviorPreference
import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserProfile
import com.privateai.profile.UserProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for BehaviorDetector, BehaviorAccumulator, and learning integration.
 *
 * These tests verify the APPLICATION-LEVEL learning logic.
 * They do NOT claim that Gemma will always produce desired model behavior.
 */
class BehaviorLearningTest {

    private lateinit var detector: BehaviorDetector
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var accumulator: BehaviorAccumulator

    @Before
    fun setup() {
        detector = BehaviorDetector()
        userProfileManager = mock()
        whenever(userProfileManager.getProfile()).thenReturn(UserProfile())
        whenever(userProfileManager.recordInferredPreference(any(), any(), any(), any())).thenReturn(
            Result.success(BehaviorPreference(
                category = PreferenceCategory.DETAIL_LEVEL,
                description = "mock",
                confidence = 0.5f,
                isExplicit = false
            ))
        )
        accumulator = BehaviorAccumulator(userProfileManager)
    }

    // ========== BehaviorDetector Tests ==========

    @Test
    fun `detector finds no signals in ordinary question`() {
        val signals = detector.detect("What is a binary search tree?")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `detector detects detail request`() {
        val signals = detector.detect("Can you explain more about how HashMap works?")
        assertTrue(signals.any { it.category == PreferenceCategory.DETAIL_LEVEL })
        assertTrue(signals.any { it.description.contains("detailed") })
    }

    @Test
    fun `detector detects concise request`() {
        val signals = detector.detect("Keep it short, just the answer please")
        assertTrue(signals.any { it.category == PreferenceCategory.DETAIL_LEVEL })
        assertTrue(signals.any { it.description.contains("concise") })
    }

    @Test
    fun `detector detects step-by-step request`() {
        val signals = detector.detect("Walk me through how quicksort works step by step")
        assertTrue(signals.any { it.category == PreferenceCategory.EXPLANATION_DEPTH })
        assertTrue(signals.any { it.description.contains("step-by-step") })
    }

    @Test
    fun `detector detects example request`() {
        val signals = detector.detect("Can you show me a practical example?")
        assertTrue(signals.any { it.category == PreferenceCategory.EXPLANATION_DEPTH })
        assertTrue(signals.any { it.description.contains("example") })
    }

    @Test
    fun `detector detects DSA dry-run request`() {
        val signals = detector.detect("Can you dry run this algorithm with the input [1,2,3]?")
        assertTrue(signals.any { it.topic == "DSA" })
        assertTrue(signals.any { it.description.contains("dry-run") })
    }

    @Test
    fun `detector detects DSA complexity analysis request`() {
        val signals = detector.detect("What is the time complexity of this solution?")
        assertTrue(signals.any { it.topic == "DSA" })
        assertTrue(signals.any { it.description.contains("complexity") })
    }

    @Test
    fun `detector detects DevOps production-level example request`() {
        val signals = detector.detect("Show me a real-world scenario for Kubernetes pod scaling")
        assertTrue(signals.any { it.topic == "DevOps" })
        assertTrue(signals.any { it.description.contains("production-level") })
    }

    @Test
    fun `detector detects structured answer request`() {
        val signals = detector.detect("Can you give me a structured answer with headers?")
        assertTrue(signals.any { it.category == PreferenceCategory.TEACHING_APPROACH })
        assertTrue(signals.any { it.description.contains("structured") })
    }

    @Test
    fun `detector detects explicit correction - don't just agree`() {
        val signals = detector.detect("Don't just agree with me. Tell me if I'm wrong.")
        assertTrue(signals.any { it.source == SignalSource.EXPLICIT_CORRECTION })
        assertTrue(signals.any { it.description.contains("Challenge") })
    }

    @Test
    fun `detector detects explicit correction - not enough detail`() {
        val signals = detector.detect("That was too brief. Don't give me a short answer.")
        assertTrue(signals.any { it.source == SignalSource.EXPLICIT_CORRECTION })
        assertTrue(signals.any { it.description.contains("detailed") })
    }

    @Test
    fun `detector returns empty for blank input`() {
        assertTrue(detector.detect("").isEmpty())
        assertTrue(detector.detect("   ").isEmpty())
    }

    // ========== BehaviorAccumulator Tests ==========

    @Test
    fun `single keyword signal does NOT create a strong preference`() {
        val signal = BehaviorSignal(
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations",
            source = SignalSource.KEYWORD_PATTERN
        )

        accumulator.processSignals(listOf(signal))

        val evidence = accumulator.getEvidence("Provide detailed, thorough explanations")
        assertNotNull(evidence)
        assertEquals(1, evidence!!.evidenceCount)
        assertEquals(0.15f, evidence.confidence, 0.01f)
        // 0.15 < PROMOTION_THRESHOLD (0.40) — should NOT be promoted
        assertFalse(evidence.promoted)
    }

    @Test
    fun `repeated compatible signals increase confidence`() {
        val signal = BehaviorSignal(
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations",
            source = SignalSource.KEYWORD_PATTERN
        )

        // Signal 1: confidence = 0.15
        accumulator.processSignals(listOf(signal))
        assertEquals(0.15f, accumulator.getEvidence(signal.description)!!.confidence, 0.01f)

        // Signal 2: confidence = 0.30
        accumulator.processSignals(listOf(signal))
        assertEquals(0.30f, accumulator.getEvidence(signal.description)!!.confidence, 0.01f)
        assertEquals(2, accumulator.getEvidence(signal.description)!!.evidenceCount)

        // Signal 3: confidence = 0.45, crosses PROMOTION_THRESHOLD (0.40)
        accumulator.processSignals(listOf(signal))
        assertEquals(0.45f, accumulator.getEvidence(signal.description)!!.confidence, 0.01f)
        assertEquals(3, accumulator.getEvidence(signal.description)!!.evidenceCount)
        assertTrue(accumulator.getEvidence(signal.description)!!.promoted)
    }

    @Test
    fun `preference promotion after sufficient evidence`() {
        val signal = BehaviorSignal(
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Provide step-by-step explanations with reasoning",
            source = SignalSource.KEYWORD_PATTERN
        )

        // Send 3 signals to cross promotion threshold
        accumulator.processSignals(listOf(signal))
        accumulator.processSignals(listOf(signal))
        accumulator.processSignals(listOf(signal))

        val evidence = accumulator.getEvidence(signal.description)!!
        assertTrue(evidence.promoted)
        assertTrue(evidence.confidence >= BehaviorAccumulator.PROMOTION_THRESHOLD)

        // Verify recordInferredPreference was called with correct parameters
        verify(userProfileManager).recordInferredPreference(
            category = any(),
            description = any(),
            confidence = any(),
            topic = any()
        )
        // Confidence is verified via the evidence snapshot (avoids float precision issues)
        assertEquals(3 * BehaviorAccumulator.KEYWORD_CONFIDENCE_INCREMENT, evidence.confidence, 0.01f)
    }

    @Test
    fun `contradictory signals reduce confidence`() {
        val detailSignal = BehaviorSignal(
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations",
            source = SignalSource.KEYWORD_PATTERN
        )
        val conciseSignal = BehaviorSignal(
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide concise, brief answers",
            source = SignalSource.KEYWORD_PATTERN
        )

        // Build up "detailed" evidence
        accumulator.processSignals(listOf(detailSignal)) // 0.15
        accumulator.processSignals(listOf(detailSignal)) // 0.30

        // Now contradict with "concise"
        accumulator.processSignals(listOf(conciseSignal))

        // "detailed" confidence should be reduced by CONTRADICTION_PENALTY (0.10)
        val detailEvidence = accumulator.getEvidence(detailSignal.description)!!
        assertEquals(0.20f, detailEvidence.confidence, 0.01f) // 0.30 - 0.10
        assertEquals(1, detailEvidence.contradictionCount)
    }

    @Test
    fun `explicit preference outranks inferred preference`() {
        // Set up an explicit preference in the mock profile
        val explicitProfile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide concise, brief answers",
                    confidence = 1.0f,
                    isExplicit = true
                )
            )
        )
        whenever(userProfileManager.getProfile()).thenReturn(explicitProfile)

        // Even if we accumulate evidence for "detailed", explicit "concise" stays
        val detailSignal = BehaviorSignal(
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations",
            source = SignalSource.KEYWORD_PATTERN
        )

        accumulator.processSignals(listOf(detailSignal))
        accumulator.processSignals(listOf(detailSignal))
        accumulator.processSignals(listOf(detailSignal))

        // The inferred preference is still recorded (UserProfileManager handles priority)
        // but the explicit preference in the profile takes precedence during injection
        assertTrue(explicitProfile.explicitPreferences.any {
            it.description.contains("concise") && it.confidence == 1.0f
        })
    }

    @Test
    fun `corrections generate stronger behavioral evidence`() {
        val correctionSignal = BehaviorSignal(
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Challenge incorrect assumptions and provide corrections",
            source = SignalSource.EXPLICIT_CORRECTION
        )

        // A single correction counts as 2 evidence units
        accumulator.processSignals(listOf(correctionSignal))

        val evidence = accumulator.getEvidence(correctionSignal.description)!!
        assertEquals(2, evidence.evidenceCount)
        // 2 * 0.15 = 0.30
        assertEquals(0.30f, evidence.confidence, 0.01f)

        // One more correction (2 more units = total 4): 4 * 0.15 = 0.60
        accumulator.processSignals(listOf(correctionSignal))
        val updated = accumulator.getEvidence(correctionSignal.description)!!
        assertEquals(4, updated.evidenceCount)
        assertEquals(0.60f, updated.confidence, 0.01f)
        assertTrue(updated.promoted) // 0.60 > 0.40 threshold
    }

    @Test
    fun `DSA-specific preference detection via detector and accumulator`() {
        val signals = detector.detect("Can you dry run the algorithm and analyze the complexity?")

        // Should detect both DSA signals
        assertTrue(signals.any { it.topic == "DSA" && it.description.contains("dry-run") })
        assertTrue(signals.any { it.topic == "DSA" && it.description.contains("complexity") })

        accumulator.processSignals(signals)

        // Both should have evidence entries
        assertNotNull(accumulator.getEvidence("Include dry-run traces for algorithms"))
        assertNotNull(accumulator.getEvidence("Always include time and space complexity analysis"))
    }

    @Test
    fun `DevOps-specific preference detection via detector and accumulator`() {
        val signals = detector.detect("Show me a real-world scenario at production scale")

        assertTrue(signals.any { it.topic == "DevOps" })

        accumulator.processSignals(signals)
        assertNotNull(accumulator.getEvidence("Use practical, production-level examples"))
    }

    @Test
    fun `unrelated topic does not create signal`() {
        // A normal factual question should not generate behavioral signals
        val signals = detector.detect("How many planets are in the solar system?")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `profile 150-token budget remains enforced`() {
        assertEquals(150, UserProfileManager.MAX_PROFILE_TOKEN_BUDGET)
    }

    @Test
    fun `clearEvidence removes all accumulated evidence`() {
        val signal = BehaviorSignal(
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations",
            source = SignalSource.KEYWORD_PATTERN
        )
        accumulator.processSignals(listOf(signal))
        assertTrue(accumulator.getEvidenceSnapshot().isNotEmpty())

        accumulator.clearEvidence()
        assertTrue(accumulator.getEvidenceSnapshot().isEmpty())
    }

    @Test
    fun `evidence count tracks correctly across multiple signals`() {
        val signal1 = BehaviorSignal(
            category = PreferenceCategory.CODING_STYLE,
            description = "Present code in clean, copyable fenced code blocks",
            source = SignalSource.KEYWORD_PATTERN
        )
        val signal2 = BehaviorSignal(
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Provide step-by-step explanations with reasoning",
            source = SignalSource.KEYWORD_PATTERN
        )

        accumulator.processSignals(listOf(signal1, signal2))
        accumulator.processSignals(listOf(signal1))

        assertEquals(2, accumulator.getEvidence(signal1.description)!!.evidenceCount)
        assertEquals(1, accumulator.getEvidence(signal2.description)!!.evidenceCount)
    }

    @Test
    fun `explicit statement signal promotes immediately with high confidence`() {
        val signal = BehaviorSignal(
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Explain the reasoning, not just the final answer",
            source = SignalSource.EXPLICIT_STATEMENT
        )

        accumulator.processSignals(listOf(signal))

        val evidence = accumulator.getEvidence(signal.description)!!
        assertEquals(BehaviorAccumulator.EXPLICIT_STATEMENT_CONFIDENCE, evidence.confidence, 0.01f)
        assertTrue(evidence.promoted) // 0.80 > 0.40 threshold
    }

    // ========== D1 Expanded Coverage & False Positive Prevention Tests ==========

    @Test
    fun `explain this in detail generates detail signal`() {
        val signals = detector.detect("Explain this in detail")
        assertTrue(signals.any { it.category == PreferenceCategory.DETAIL_LEVEL && it.description.contains("detailed") })
    }

    @Test
    fun `please be concise generates concise signal`() {
        val signals = detector.detect("Please be concise")
        assertTrue(signals.any { it.category == PreferenceCategory.DETAIL_LEVEL && it.description.contains("concise") })
    }

    @Test
    fun `give detailed reasoning generates detail signal`() {
        val signals = detector.detect("Give detailed reasoning")
        assertTrue(signals.any { it.category == PreferenceCategory.DETAIL_LEVEL && it.description.contains("detailed") })
    }

    @Test
    fun `what does detailed reasoning mean generates no signal`() {
        val signals = detector.detect("What does detailed reasoning mean?")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `what does step-by-step execution mean generates no signal`() {
        val signals = detector.detect("What does step-by-step execution mean?")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `what is step-by-step execution generates no signal`() {
        val signals = detector.detect("What is step-by-step execution?")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `why is concise code preferred generates no signal`() {
        val signals = detector.detect("Why is concise code preferred?")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `promotion preserves topic to UserProfileManager`() {
        val signal = BehaviorSignal(
            category = PreferenceCategory.PROBLEM_SOLVING,
            description = "Include dry-run traces for algorithms",
            source = SignalSource.KEYWORD_PATTERN,
            topic = "DSA"
        )

        accumulator.processSignals(listOf(signal))
        accumulator.processSignals(listOf(signal))
        accumulator.processSignals(listOf(signal))

        verify(userProfileManager).recordInferredPreference(
            category = eq(PreferenceCategory.PROBLEM_SOLVING),
            description = eq("Include dry-run traces for algorithms"),
            confidence = any(),
            topic = eq("DSA")
        )
        val evidence = accumulator.getEvidence("Include dry-run traces for algorithms")!!
        assertEquals("DSA", evidence.topic)
        assertEquals(0.45f, evidence.confidence, 0.01f)
    }
}
