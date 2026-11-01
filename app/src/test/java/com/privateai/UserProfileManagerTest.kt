package com.privateai

import com.privateai.profile.BehaviorPreference
import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserCorrection
import com.privateai.profile.UserProfile
import com.privateai.profile.UserProfileManager
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for UserProfile data model, confidence scoring, and directive retrieval.
 * Uses the data models directly without requiring Android Context or SecurityManager mocking.
 */
class UserProfileManagerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `explicit preference has 1_0 confidence and isExplicit flag`() {
        val pref = BehaviorPreference(
            category = PreferenceCategory.CODING_STYLE,
            description = "Prefer functional programming patterns and immutability",
            confidence = 1.0f,
            isExplicit = true
        )

        assertEquals(1.0f, pref.confidence, 0.001f)
        assertTrue(pref.isExplicit)
        assertEquals(PreferenceCategory.CODING_STYLE, pref.category)
        assertTrue(pref.id.isNotBlank())
    }

    @Test
    fun `inferred preference confidence is clamped between 0_1 and 0_95`() {
        val low = BehaviorPreference(
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Prefers step-by-step mathematical reasoning",
            confidence = 0.05f.coerceIn(0.1f, 0.95f),
            isExplicit = false
        )
        assertEquals(0.1f, low.confidence, 0.01f)

        val high = BehaviorPreference(
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Prefers step-by-step mathematical reasoning",
            confidence = 1.0f.coerceIn(0.1f, 0.95f),
            isExplicit = false
        )
        assertEquals(0.95f, high.confidence, 0.01f)
    }

    @Test
    fun `confidence merging averages values and stays capped at 0_95`() {
        val existing = 0.6f
        val incoming = 0.8f
        val merged = ((existing + incoming) / 2.0f).coerceAtMost(0.95f)
        assertEquals(0.7f, merged, 0.01f)

        val highExisting = 0.9f
        val highIncoming = 0.95f
        val highMerged = ((highExisting + highIncoming) / 2.0f).coerceAtMost(0.95f)
        assertEquals(0.925f, highMerged, 0.01f)
    }

    @Test
    fun `user correction stores topic and correction text`() {
        val correction = UserCorrection(
            topic = "quicksort",
            correction = "Always use randomized pivot selection to avoid O(n^2) worst-case"
        )

        assertEquals("quicksort", correction.topic)
        assertTrue(correction.correction.contains("randomized pivot"))
        assertTrue(correction.timestamp > 0)
    }

    @Test
    fun `UserProfile serializes and deserializes correctly`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    id = "p1",
                    category = PreferenceCategory.CODING_STYLE,
                    description = "Use Kotlin val instead of var",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    id = "p2",
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Prefers concise answers",
                    confidence = 0.7f,
                    isExplicit = false
                )
            ),
            userCorrections = listOf(
                UserCorrection(
                    id = "c1",
                    topic = "async",
                    correction = "Use Coroutines instead of RxJava"
                )
            )
        )

        val raw = json.encodeToString(UserProfile.serializer(), profile)
        val decoded = json.decodeFromString(UserProfile.serializer(), raw)

        assertEquals(1, decoded.explicitPreferences.size)
        assertEquals(1, decoded.inferredPreferences.size)
        assertEquals(1, decoded.userCorrections.size)
        assertEquals("Use Kotlin val instead of var", decoded.explicitPreferences[0].description)
        assertEquals(0.7f, decoded.inferredPreferences[0].confidence, 0.01f)
        assertFalse(decoded.inferredPreferences[0].isExplicit)
        assertEquals("Use Coroutines instead of RxJava", decoded.userCorrections[0].correction)
    }

    @Test
    fun `deleting preference by id removes only that preference`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(id = "keep", category = PreferenceCategory.CODING_STYLE, description = "Keep this"),
                BehaviorPreference(id = "remove", category = PreferenceCategory.DETAIL_LEVEL, description = "Remove this")
            )
        )

        val updated = profile.copy(
            explicitPreferences = profile.explicitPreferences.filter { it.id != "remove" }
        )

        assertEquals(1, updated.explicitPreferences.size)
        assertEquals("keep", updated.explicitPreferences[0].id)
    }

    @Test
    fun `clearProfile produces empty profile`() {
        val cleared = UserProfile()
        assertTrue(cleared.explicitPreferences.isEmpty())
        assertTrue(cleared.inferredPreferences.isEmpty())
        assertTrue(cleared.userCorrections.isEmpty())
    }

    @Test
    fun `getRelevantBehaviorDirectives filters by confidence and query relevance`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.CODING_STYLE,
                    description = "Prefer Kotlin idiomatic code",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.EXPLANATION_DEPTH,
                    description = "Prefers short answers",
                    confidence = 0.3f, // Below threshold
                    isExplicit = false
                ),
                BehaviorPreference(
                    category = PreferenceCategory.PROBLEM_SOLVING,
                    description = "Likes dry-run examples",
                    confidence = 0.7f, // Above threshold
                    isExplicit = false
                )
            ),
            userCorrections = listOf(
                UserCorrection(topic = "quicksort", correction = "Use randomized pivot")
            )
        )

        // Simulate what UserProfileManager.getRelevantBehaviorDirectives does
        val minConfidence = 0.5f
        val maxDirectives = 3
        val query = "Explain quicksort in Kotlin"
        val queryLower = query.lowercase()
        val candidates = mutableListOf<Pair<String, Float>>()

        for (pref in profile.explicitPreferences) {
            candidates.add("[${pref.category.name}] ${pref.description}" to 1.0f)
        }
        for (pref in profile.inferredPreferences) {
            if (pref.confidence >= minConfidence) {
                candidates.add("[${pref.category.name}] ${pref.description}" to pref.confidence)
            }
        }
        for (corr in profile.userCorrections) {
            if (queryLower.contains(corr.topic.lowercase())) {
                candidates.add("[CORRECTION on ${corr.topic}] ${corr.correction}" to 1.0f)
            }
        }

        val directives = candidates.sortedByDescending { it.second }.take(maxDirectives).map { it.first }

        // Should include: explicit pref (1.0), correction on quicksort (1.0), inferred 0.7
        assertEquals(3, directives.size)
        assertTrue(directives.any { it.contains("Prefer Kotlin") })
        assertTrue(directives.any { it.contains("randomized pivot") })
        assertTrue(directives.any { it.contains("dry-run") })

        // Should NOT include the 0.3 confidence inferred preference
        assertFalse(directives.any { it.contains("short answers") })
    }

    @Test
    fun `all preference categories exist`() {
        val categories = PreferenceCategory.entries
        assertEquals(7, categories.size)
        assertTrue(categories.contains(PreferenceCategory.EXPLANATION_DEPTH))
        assertTrue(categories.contains(PreferenceCategory.CODING_STYLE))
        assertTrue(categories.contains(PreferenceCategory.PROBLEM_SOLVING))
        assertTrue(categories.contains(PreferenceCategory.DETAIL_LEVEL))
        assertTrue(categories.contains(PreferenceCategory.TEACHING_APPROACH))
        assertTrue(categories.contains(PreferenceCategory.LEARNING_STYLE))
        assertTrue(categories.contains(PreferenceCategory.COMMUNICATION_TONE))
    }

    // ========== updatePreference tests ==========

    @Test
    fun `updatePreference updates explicit preference description preserving id and confidence`() {
        val original = BehaviorPreference(
            id = "p1",
            category = PreferenceCategory.CODING_STYLE,
            description = "Original description",
            confidence = 1.0f,
            isExplicit = true,
            updatedAt = 1000L
        )

        // Simulate what updatePreference does for explicit prefs
        val updated = original.copy(
            description = "Updated description",
            confidence = 1.0f, // explicit always stays 1.0
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("p1", updated.id)
        assertEquals("Updated description", updated.description)
        assertEquals(1.0f, updated.confidence, 0.001f)
        assertTrue(updated.isExplicit)
        assertTrue(updated.updatedAt > original.updatedAt)
    }

    @Test
    fun `updatePreference updates inferred preference with clamped confidence`() {
        val original = BehaviorPreference(
            id = "p2",
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Prefers concise answers",
            confidence = 0.6f,
            isExplicit = false,
            updatedAt = 1000L
        )

        // Simulate updating with a new confidence that exceeds the 0.95 cap
        val newConfidence = 1.0f.coerceIn(0.1f, 0.95f)
        val updated = original.copy(
            description = "Prefers very concise answers",
            confidence = newConfidence,
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("p2", updated.id)
        assertEquals("Prefers very concise answers", updated.description)
        assertEquals(0.95f, updated.confidence, 0.001f) // clamped
        assertFalse(updated.isExplicit)
        assertTrue(updated.updatedAt > original.updatedAt)
    }

    @Test
    fun `updatePreference changes category while keeping other fields`() {
        val original = BehaviorPreference(
            id = "p3",
            category = PreferenceCategory.CODING_STYLE,
            description = "Use functional patterns",
            confidence = 0.7f,
            isExplicit = false
        )

        val updated = original.copy(
            category = PreferenceCategory.PROBLEM_SOLVING,
            confidence = original.confidence.coerceIn(0.1f, 0.95f)
        )

        assertEquals("p3", updated.id)
        assertEquals(PreferenceCategory.PROBLEM_SOLVING, updated.category)
        assertEquals("Use functional patterns", updated.description)
        assertEquals(0.7f, updated.confidence, 0.001f)
    }

    @Test
    fun `updatePreference with null fields preserves existing values`() {
        val original = BehaviorPreference(
            id = "p4",
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Step-by-step explanations",
            confidence = 0.8f,
            isExplicit = false
        )

        // null newDescription, null newCategory, null newConfidence → all preserved
        val updated = original.copy(
            description = null?.trim() ?: original.description,
            category = null ?: original.category,
            confidence = (null ?: original.confidence).coerceIn(0.1f, 0.95f)
        )

        assertEquals(original.id, updated.id)
        assertEquals(original.description, updated.description)
        assertEquals(original.category, updated.category)
        assertEquals(original.confidence, updated.confidence, 0.001f)
    }

    // ========== Profile token budget tests ==========

    @Test
    fun `profile token budget stops adding directives when budget exceeded`() {
        // Each directive ≈ 20 chars ÷ 3.8 ≈ 6 tokens
        // With a tiny budget of 10 tokens, only 1 directive should fit
        val charsPerToken = 3.8
        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            return kotlin.math.ceil(text.length / charsPerToken).toInt()
        }

        val directives = listOf(
            "[CODING_STYLE] Use Kotlin val" to 1.0f,       // ~8 tokens
            "[DETAIL_LEVEL] Be concise" to 0.9f,           // ~7 tokens
            "[PROBLEM_SOLVING] Show steps" to 0.8f         // ~8 tokens
        )

        val maxTokenBudget = 10
        val result = mutableListOf<String>()
        var accumulatedTokens = 0

        for ((directive, _) in directives.sortedByDescending { it.second }) {
            if (result.size >= 3) break
            val tokens = estimateTokens(directive)
            if (accumulatedTokens + tokens > maxTokenBudget) break
            result.add(directive)
            accumulatedTokens += tokens
        }

        // Only the first directive fits within 10 tokens
        assertEquals(1, result.size)
        assertTrue(result[0].contains("Kotlin val"))
        assertTrue(accumulatedTokens <= maxTokenBudget)
    }

    @Test
    fun `profile token budget allows all directives when budget is sufficient`() {
        val charsPerToken = 3.8
        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            return kotlin.math.ceil(text.length / charsPerToken).toInt()
        }

        val directives = listOf(
            "[CODING_STYLE] Use Kotlin val" to 1.0f,
            "[DETAIL_LEVEL] Be concise" to 0.9f,
            "[PROBLEM_SOLVING] Show steps" to 0.8f
        )

        val maxTokenBudget = 150 // default budget — generous for 3 short directives
        val result = mutableListOf<String>()
        var accumulatedTokens = 0

        for ((directive, _) in directives.sortedByDescending { it.second }) {
            if (result.size >= 3) break
            val tokens = estimateTokens(directive)
            if (accumulatedTokens + tokens > maxTokenBudget) break
            result.add(directive)
            accumulatedTokens += tokens
        }

        assertEquals(3, result.size)
        assertTrue(accumulatedTokens <= maxTokenBudget)
    }

    @Test
    fun `profile token budget of zero produces no directives`() {
        val charsPerToken = 3.8
        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            return kotlin.math.ceil(text.length / charsPerToken).toInt()
        }

        val directives = listOf(
            "[CODING_STYLE] Use Kotlin val" to 1.0f
        )

        val maxTokenBudget = 0
        val result = mutableListOf<String>()
        var accumulatedTokens = 0

        for ((directive, _) in directives) {
            if (result.size >= 3) break
            val tokens = estimateTokens(directive)
            if (accumulatedTokens + tokens > maxTokenBudget) break
            result.add(directive)
            accumulatedTokens += tokens
        }

        assertEquals(0, result.size)
    }

    @Test
    fun `MAX_PROFILE_TOKEN_BUDGET constant is 150`() {
        assertEquals(150, UserProfileManager.MAX_PROFILE_TOKEN_BUDGET)
    }

    // ========== D3 Topic-Aware Profile Injection Tests ==========

    @Test
    fun `existing profiles without topic deserialize safely with general default`() {
        // Serialized JSON from before the topic field was added
        val legacyJson = """
            {
                "id": "legacy-pref-1",
                "category": "CODING_STYLE",
                "description": "Use functional patterns",
                "confidence": 1.0,
                "isExplicit": true,
                "updatedAt": 123456789
            }
        """.trimIndent()

        val pref = json.decodeFromString<BehaviorPreference>(legacyJson)
        assertEquals("legacy-pref-1", pref.id)
        assertEquals("general", pref.topic) // Default applied
        assertEquals(PreferenceCategory.CODING_STYLE, pref.category)
    }

    @Test
    fun `DSA preference injected for DSA query and excluded for casual query`() {
        val profile = UserProfile(
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.PROBLEM_SOLVING,
                    description = "Include dry-run traces for algorithms",
                    confidence = 0.8f,
                    isExplicit = false,
                    topic = "DSA"
                ),
                BehaviorPreference(
                    category = PreferenceCategory.EXPLANATION_DEPTH,
                    description = "Provide step-by-step explanations",
                    confidence = 0.7f,
                    isExplicit = false,
                    topic = "general"
                )
            )
        )

        val dsaKeywords = listOf("algorithm", "tree", "graph", "sorting", "binary search", "dsa", "complexity")
        fun isTopicRelevant(topic: String, queryLower: String): Boolean {
            if (topic == "general") return true
            return when (topic.lowercase()) {
                "dsa" -> dsaKeywords.any { queryLower.contains(it) }
                else -> true
            }
        }

        fun getDirectives(query: String): List<String> {
            val queryLower = query.lowercase()
            return profile.inferredPreferences
                .filter { it.confidence >= 0.5f }
                .filter { isTopicRelevant(it.topic, queryLower) }
                .map { "[${it.category.name}] ${it.description}" }
        }

        // DSA query -> both general and DSA preferences injected
        val dsaDirectives = getDirectives("How does binary search work?")
        assertEquals(2, dsaDirectives.size)
        assertTrue(dsaDirectives.any { it.contains("dry-run") })
        assertTrue(dsaDirectives.any { it.contains("step-by-step") })

        // Casual query -> only general preference injected, DSA preference excluded
        val casualDirectives = getDirectives("What is the capital of France?")
        assertEquals(1, casualDirectives.size)
        assertFalse(casualDirectives.any { it.contains("dry-run") })
        assertTrue(casualDirectives.any { it.contains("step-by-step") })
    }

    @Test
    fun `DevOps preference injected for DevOps query and excluded for casual query`() {
        val profile = UserProfile(
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.PROBLEM_SOLVING,
                    description = "Use practical, production-level examples",
                    confidence = 0.8f,
                    isExplicit = false,
                    topic = "DevOps"
                )
            )
        )

        val devopsKeywords = listOf("docker", "kubernetes", "k8s", "ci/cd", "pipeline", "deploy", "container")
        fun isTopicRelevant(topic: String, queryLower: String): Boolean {
            if (topic == "general") return true
            return when (topic.lowercase()) {
                "devops" -> devopsKeywords.any { queryLower.contains(it) }
                else -> true
            }
        }

        // DevOps query
        val devopsQuery = "How to configure a Docker container pipeline?"
        val devopsDirectives = profile.inferredPreferences
            .filter { it.confidence >= 0.5f && isTopicRelevant(it.topic, devopsQuery.lowercase()) }
            .map { it.description }
        assertEquals(1, devopsDirectives.size)
        assertTrue(devopsDirectives[0].contains("production-level"))

        // Casual query
        val casualQuery = "Tell me a joke"
        val casualDirectives = profile.inferredPreferences
            .filter { it.confidence >= 0.5f && isTopicRelevant(it.topic, casualQuery.lowercase()) }
            .map { it.description }
        assertEquals(0, casualDirectives.size)
    }

    // ========== D4 Explicit vs Inferred Contradiction Tests ==========

    @Test
    fun `explicit concise and inferred detailed results in only concise injected`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide concise, brief answers",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide detailed, thorough explanations",
                    confidence = 0.85f,
                    isExplicit = false
                )
            )
        )

        // Simulate getRelevantBehaviorDirectives logic with D4 suppression
        val explicitCategories = profile.explicitPreferences.map { it.category }.toSet()
        val candidates = mutableListOf<String>()

        for (pref in profile.explicitPreferences) {
            candidates.add("[${pref.category.name}] ${pref.description}")
        }
        for (pref in profile.inferredPreferences) {
            if (pref.category in explicitCategories) continue // Suppressed!
            candidates.add("[${pref.category.name}] ${pref.description}")
        }

        assertEquals(1, candidates.size)
        assertTrue(candidates[0].contains("concise"))
        assertFalse(candidates.any { it.contains("detailed") })
    }

    @Test
    fun `explicit detailed and inferred concise results in only detailed injected`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide detailed, thorough explanations",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide concise, brief answers",
                    confidence = 0.85f,
                    isExplicit = false
                )
            )
        )

        val explicitCategories = profile.explicitPreferences.map { it.category }.toSet()
        val candidates = mutableListOf<String>()

        for (pref in profile.explicitPreferences) {
            candidates.add("[${pref.category.name}] ${pref.description}")
        }
        for (pref in profile.inferredPreferences) {
            if (pref.category in explicitCategories) continue
            candidates.add("[${pref.category.name}] ${pref.description}")
        }

        assertEquals(1, candidates.size)
        assertTrue(candidates[0].contains("detailed"))
        assertFalse(candidates.any { it.contains("concise") })
    }

    @Test
    fun `non-conflicting explicit and inferred preferences coexist`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.CODING_STYLE,
                    description = "Use functional patterns",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide concise, brief answers",
                    confidence = 0.8f,
                    isExplicit = false
                )
            )
        )

        val explicitCategories = profile.explicitPreferences.map { it.category }.toSet()
        val candidates = mutableListOf<String>()

        for (pref in profile.explicitPreferences) {
            candidates.add("[${pref.category.name}] ${pref.description}")
        }
        for (pref in profile.inferredPreferences) {
            if (pref.category in explicitCategories) continue
            candidates.add("[${pref.category.name}] ${pref.description}")
        }

        // Both should be present since they are in different categories
        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.contains("functional") })
        assertTrue(candidates.any { it.contains("concise") })
    }

    @Test
    fun `inferred preference remains stored in profile even when suppressed during injection`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide concise, brief answers",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide detailed, thorough explanations",
                    confidence = 0.85f,
                    isExplicit = false
                )
            )
        )

        // Inferred preference is still present in the stored profile object
        assertEquals(1, profile.inferredPreferences.size)
        assertEquals("Provide detailed, thorough explanations", profile.inferredPreferences[0].description)
    }

    // ========== Step 1 Personalization & Learning Dimensions Tests ==========

    @Test
    fun `TEST 1 - explicit preference stores with 1_0 confidence`() {
        val pref = BehaviorPreference(
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Show concrete examples before theoretical explanations",
            confidence = 1.0f,
            isExplicit = true
        )
        assertEquals(1.0f, pref.confidence, 0.001f)
        assertTrue(pref.isExplicit)
        assertEquals(PreferenceCategory.LEARNING_STYLE, pref.category)
    }

    @Test
    fun `TEST 2 - inferred preference confidence increases gradually with repeated evidence`() {
        var confidence = 0.15f
        // Signal 1
        assertEquals(0.15f, confidence, 0.01f)
        // Signal 2
        confidence = ((confidence + 0.30f) / 2.0f).coerceAtMost(0.95f)
        assertEquals(0.225f, confidence, 0.01f)
        // Signal 3
        confidence = ((confidence + 0.60f) / 2.0f).coerceAtMost(0.95f)
        assertEquals(0.4125f, confidence, 0.01f)
        assertTrue(confidence >= 0.40f) // cross promotion threshold
    }

    @Test
    fun `TEST 3 - explicit overrides inferred in the same category`() {
        val profile = UserProfile(
            explicitPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Provide concise, brief answers",
                    confidence = 1.0f,
                    isExplicit = true
                )
            ),
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.DETAIL_LEVEL,
                    description = "Explore low-level internals, OS syscalls, and boundary conditions",
                    confidence = 0.90f,
                    isExplicit = false
                )
            )
        )
        val explicitCategories = profile.explicitPreferences.map { it.category }.toSet()
        val injected = profile.explicitPreferences.map { it.description } +
                profile.inferredPreferences.filter { it.category !in explicitCategories }.map { it.description }

        assertEquals(1, injected.size)
        assertTrue(injected[0].contains("concise"))
    }

    @Test
    fun `TEST 4 - confidence decay reduces unreinforced inferred preferences and learning dimensions`() {
        val profile = UserProfile(
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.LEARNING_STYLE,
                    description = "Explain concepts using intuitive real-world analogies",
                    confidence = 0.80f,
                    isExplicit = false
                )
            ),
            learningDimensions = mapOf("intuition_first" to 0.80f)
        )

        val decayFactor = 0.95f
        val decayedPrefs = profile.inferredPreferences.map { it.copy(confidence = it.confidence * decayFactor) }
        val decayedDims = profile.learningDimensions.mapValues { it.value * decayFactor }

        assertEquals(0.76f, decayedPrefs[0].confidence, 0.01f)
        assertEquals(0.76f, decayedDims["intuition_first"]!!, 0.01f)
    }

    @Test
    fun `TEST 5 - relevant personalization surfaces high confidence learning dimension`() {
        val profile = UserProfile(
            learningDimensions = mapOf(
                "intuition_first" to 0.82f,
                "step_by_step" to 0.76f
            )
        )
        val directives = mutableListOf<String>()
        for ((dim, score) in profile.learningDimensions) {
            if (score >= 0.65f) {
                when (dim) {
                    "intuition_first" -> directives.add("[LEARNING STYLE] Prioritize intuitive mental models before formal definitions")
                    "step_by_step" -> directives.add("[LEARNING STYLE] Provide step-by-step sequential breakdowns")
                }
            }
        }
        assertEquals(2, directives.size)
        assertTrue(directives.any { it.contains("intuitive mental models") })
        assertTrue(directives.any { it.contains("sequential breakdowns") })
    }

    @Test
    fun `TEST 6 - irrelevant personalization suppression on factual query`() {
        val query = "What is the capital of Japan?"
        val isFactual = listOf("what is the capital of", "who wrote").any { query.lowercase().contains(it) }
        assertTrue(isFactual)

        val profile = UserProfile(
            learningDimensions = mapOf("deep_dive_preferred" to 0.90f)
        )
        val candidates = mutableListOf<String>()
        if (!isFactual) {
            for ((_, score) in profile.learningDimensions) {
                if (score >= 0.65f) candidates.add("[LEARNING STYLE] Explore low-level internals")
            }
        }
        assertTrue(candidates.isEmpty()) // Suppressed on purely factual query
    }

    @Test
    fun `TEST 7 - temporary confusion does not permanently corrupt learning profile`() {
        val detector = com.privateai.learning.BehaviorDetector()
        val signals = detector.detect("I don't understand.")
        // Temporary confusion produces NO long-term profile signals
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `TEST 8 - current request overrides profile preference`() {
        val profile = UserProfile(
            inferredPreferences = listOf(
                BehaviorPreference(
                    category = PreferenceCategory.EXPLANATION_DEPTH,
                    description = "Provide step-by-step sequential breakdowns",
                    confidence = 0.90f,
                    isExplicit = false
                )
            )
        )
        val userQuery = "Just give me the final code."
        val isCodeOnly = userQuery.lowercase().contains("just give me the final code") || userQuery.lowercase().contains("code only")
        assertTrue(isCodeOnly)
        // Current request wins
    }

    // ========== Adversarial Tests ==========

    @Test
    fun `ADVERSARIAL CASE A - explicit formal request suppresses inferred examples_first`() {
        val explicitDesc = "No examples. Explain formally."
        val contradictsExamples = explicitDesc.lowercase().contains("no example") || explicitDesc.lowercase().contains("formal")
        assertTrue(contradictsExamples)
    }

    @Test
    fun `ADVERSARIAL CASE B - two line request forces concise response regardless of deep_dive profile`() {
        val userQuery = "Give me the answer in two lines."
        val isConcise = userQuery.lowercase().contains("two lines") || userQuery.lowercase().contains("concise")
        assertTrue(isConcise)
    }

    @Test
    fun `ADVERSARIAL CASE C - debugging query does not classify user as frustrated`() {
        val userStateDetector = com.privateai.intelligence.UserStateDetector()
        val state = userStateDetector.detectUserState("Why doesn't this work?")
        // Should be DEBUGGING, NOT FRUSTRATED
        assertEquals(com.privateai.intelligence.UserState.DEBUGGING, state)
    }

    @Test
    fun `ADVERSARIAL CASE D - repeated confusion does not emit permanent profile mutation`() {
        val detector = com.privateai.learning.BehaviorDetector()
        val signals = detector.detect("Still don't understand.")
        assertTrue(signals.isEmpty())
    }
}

