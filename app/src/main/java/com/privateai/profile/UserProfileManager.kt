package com.privateai.profile

import android.content.Context
import android.util.Log
import com.privateai.security.SecurityManager
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages encrypted persistence, confidence scoring, and retrieval of user behavior preferences,
 * continuous learning dimensions, and struggled concepts.
 */
class UserProfileManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    /** Estimates token count from string length, consistent with ContextManager. */
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return kotlin.math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    private val profileDir: File
        get() = File(context.noBackupFilesDir, PROFILE_DIR).also { it.mkdirs() }

    private val profileFile: File
        get() = File(profileDir, PROFILE_FILE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Loads and decrypts the user profile from disk.
     */
    fun getProfile(): UserProfile {
        if (!profileFile.exists()) return UserProfile()
        return try {
            val decryptedBytes = securityManager.decryptFile(profileFile).getOrThrow()
            val raw = String(decryptedBytes, Charsets.UTF_8)
            json.decodeFromString<UserProfile>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user profile", e)
            UserProfile()
        }
    }

    /**
     * Saves and encrypts the user profile to disk.
     */
    fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        val raw = json.encodeToString(UserProfile.serializer(), profile)
        securityManager.encryptToFile(
            data = raw.toByteArray(Charsets.UTF_8),
            destFile = profileFile
        ).getOrThrow()
        Log.d(TAG, "User profile saved successfully")
    }

    /**
     * Adds an explicitly stated preference with 1.0 confidence.
     */
    fun addExplicitPreference(
        category: PreferenceCategory,
        description: String,
        topic: String = "general"
    ): Result<BehaviorPreference> = runCatching {
        require(description.isNotBlank()) { "Preference description cannot be empty" }
        val current = getProfile()
        val newPref = BehaviorPreference(
            category = category,
            description = description.trim(),
            confidence = 1.0f,
            isExplicit = true,
            topic = topic
        )
        val updated = current.copy(
            explicitPreferences = current.explicitPreferences + newPref
        )
        saveProfile(updated).getOrThrow()
        newPref
    }

    /**
     * Records or updates an inferred behavioral preference with confidence between 0.0 and 1.0.
     */
    fun recordInferredPreference(
        category: PreferenceCategory,
        description: String,
        confidence: Float,
        topic: String = "general"
    ): Result<BehaviorPreference> = runCatching {
        require(description.isNotBlank()) { "Preference description cannot be empty" }
        val clampedConfidence = confidence.coerceIn(0.1f, 0.95f)
        val current = getProfile()

        // Check if similar inferred preference exists to adjust confidence
        val existingIndex = current.inferredPreferences.indexOfFirst {
            it.category == category && it.description.equals(description.trim(), ignoreCase = true)
        }

        val updatedList = current.inferredPreferences.toMutableList()
        val pref = if (existingIndex >= 0) {
            val existing = updatedList[existingIndex]
            val mergedConfidence = ((existing.confidence + clampedConfidence) / 2.0f).coerceAtMost(0.95f)
            val updated = existing.copy(
                confidence = mergedConfidence,
                topic = topic,
                updatedAt = System.currentTimeMillis()
            )
            updatedList[existingIndex] = updated
            updated
        } else {
            val newPref = BehaviorPreference(
                category = category,
                description = description.trim(),
                confidence = clampedConfidence,
                isExplicit = false,
                topic = topic
            )
            updatedList.add(newPref)
            newPref
        }

        saveProfile(current.copy(inferredPreferences = updatedList)).getOrThrow()
        pref
    }

    /**
     * Records a correction the user made to improve future responses.
     */
    fun recordCorrection(topic: String, correction: String): Result<UserCorrection> = runCatching {
        require(topic.isNotBlank() && correction.isNotBlank()) { "Topic and correction cannot be empty" }
        val current = getProfile()
        val newCorrection = UserCorrection(
            topic = topic.trim(),
            correction = correction.trim()
        )
        val updated = current.copy(
            userCorrections = current.userCorrections + newCorrection
        )
        saveProfile(updated).getOrThrow()
        newCorrection
    }

    /**
     * Updates an existing preference by ID, preserving its explicit/inferred status.
     */
    fun updatePreference(
        id: String,
        newDescription: String? = null,
        newCategory: PreferenceCategory? = null,
        newConfidence: Float? = null,
        newTopic: String? = null
    ): Result<BehaviorPreference> = runCatching {
        val current = getProfile()
        val now = System.currentTimeMillis()

        val explicitIndex = current.explicitPreferences.indexOfFirst { it.id == id }
        val inferredIndex = current.inferredPreferences.indexOfFirst { it.id == id }

        if (explicitIndex < 0 && inferredIndex < 0) {
            throw IllegalArgumentException("Preference not found: $id")
        }

        if (explicitIndex >= 0) {
            val existing = current.explicitPreferences[explicitIndex]
            val updated = existing.copy(
                description = newDescription?.trim() ?: existing.description,
                category = newCategory ?: existing.category,
                confidence = 1.0f, // explicit always 1.0
                topic = newTopic?.trim() ?: existing.topic,
                updatedAt = now
            )
            val list = current.explicitPreferences.toMutableList()
            list[explicitIndex] = updated
            saveProfile(current.copy(explicitPreferences = list)).getOrThrow()
            updated
        } else {
            val existing = current.inferredPreferences[inferredIndex]
            val clampedConfidence = (newConfidence ?: existing.confidence).coerceIn(0.1f, 0.95f)
            val updated = existing.copy(
                description = newDescription?.trim() ?: existing.description,
                category = newCategory ?: existing.category,
                confidence = clampedConfidence,
                topic = newTopic?.trim() ?: existing.topic,
                updatedAt = now
            )
            val list = current.inferredPreferences.toMutableList()
            list[inferredIndex] = updated
            saveProfile(current.copy(inferredPreferences = list)).getOrThrow()
            updated
        }
    }

    /**
     * Deletes a preference by ID from either explicit or inferred lists.
     */
    fun deletePreference(id: String): Result<Unit> = runCatching {
        val current = getProfile()
        val updated = current.copy(
            explicitPreferences = current.explicitPreferences.filter { it.id != id },
            inferredPreferences = current.inferredPreferences.filter { it.id != id }
        )
        saveProfile(updated).getOrThrow()
    }

    /**
     * Deletes a user correction by ID.
     */
    fun deleteCorrection(id: String): Result<Unit> = runCatching {
        val current = getProfile()
        val updated = current.copy(
            userCorrections = current.userCorrections.filter { it.id != id }
        )
        saveProfile(updated).getOrThrow()
    }

    /**
     * Retrieves relevant behavioral directives matching query keywords and meeting confidence threshold.
     *
     * Enforces:
     * - Count limit (maxDirectives)
     * - Token budget (maxTokenBudget <= 150 tokens)
     * - Topic relevance: topic-specific preferences are only injected when the query is relevant
     * - Factual / non-technical query guard: technical learning styles are suppressed on simple factual queries
     * - Contradiction suppression: inferred preferences and dimensions are suppressed when an explicit preference
     *   exists in the same category
     */
    fun getRelevantBehaviorDirectives(
        query: String,
        minConfidence: Float = 0.5f,
        maxDirectives: Int = 3,
        maxTokenBudget: Int = MAX_PROFILE_TOKEN_BUDGET
    ): List<String> {
        val profile = getProfile()
        val candidates = mutableListOf<Pair<String, Float>>()
        val queryLower = query.lowercase()

        val isFactualOrGreeting = isSimpleFactualOrGreeting(queryLower)

        // Collect categories that have explicit preferences (for contradiction suppression)
        val explicitCategories = profile.explicitPreferences.map { it.category }.toSet()
        val explicitDescriptions = profile.explicitPreferences.map { it.description.lowercase() }

        // 1. All explicit preferences are high-confidence candidates (always included regardless of topic)
        for (pref in profile.explicitPreferences) {
            candidates.add("[${pref.category.name}] ${pref.description}" to 1.0f)
        }

        // 2. Inferred preferences: apply confidence threshold, topic relevance, and contradiction suppression
        for (pref in profile.inferredPreferences) {
            if (pref.confidence < minConfidence) continue
            if (pref.category in explicitCategories) continue
            if (!isTopicRelevant(pref.topic, queryLower)) continue
            if (isFactualOrGreeting && pref.category in listOf(PreferenceCategory.PROBLEM_SOLVING, PreferenceCategory.CODING_STYLE)) continue

            candidates.add("[${pref.category.name}] ${pref.description}" to pref.confidence)
        }

        // 3. Continuous Learning Dimensions (surfaced when confidence >= 0.65 and query is not purely factual)
        if (!isFactualOrGreeting) {
            for ((dim, score) in profile.learningDimensions) {
                if (score >= 0.65f) {
                    val directiveText = mapDimensionToDirective(dim)
                    if (directiveText != null) {
                        // Suppress if an explicit preference contradicts this dimension
                        val contradictsExplicit = when (dim) {
                            "examples_first" -> explicitDescriptions.any { it.contains("no example") || it.contains("without example") || it.contains("formal") }
                            "deep_dive_preferred" -> explicitDescriptions.any { it.contains("concise") || it.contains("short") || it.contains("brief") }
                            "code_before_explanation" -> explicitDescriptions.any { it.contains("explain first") || it.contains("explanation before code") }
                            else -> false
                        }
                        if (!contradictsExplicit) {
                            candidates.add("[LEARNING STYLE] $directiveText" to score)
                        }
                    }
                }
            }
        }

        // 4. Relevant corrections matching query words
        for (corr in profile.userCorrections) {
            if (queryLower.contains(corr.topic.lowercase()) || corr.topic.lowercase().contains(queryLower)) {
                candidates.add("[CORRECTION on ${corr.topic}] ${corr.correction}" to 1.0f)
            }
        }

        // Sort by confidence, then apply both count and token budget limits
        val sorted = candidates.sortedByDescending { it.second }
        val result = mutableListOf<String>()
        var accumulatedTokens = 0

        for ((directive, _) in sorted) {
            if (result.size >= maxDirectives) break
            val directiveTokens = estimateTokens(directive)
            if (accumulatedTokens + directiveTokens > maxTokenBudget) break
            result.add(directive)
            accumulatedTokens += directiveTokens
        }

        return result
    }

    private fun isSimpleFactualOrGreeting(queryLower: String): Boolean {
        val factualPrefixes = listOf(
            "what is the capital of", "who wrote", "what year was", "where is",
            "population of", "height of", "who invented", "capital of"
        )
        val greetings = listOf("hi", "hello", "hey", "good morning", "good evening", "how are you", "thanks", "thank you")
        return factualPrefixes.any { queryLower.contains(it) } || greetings.any { queryLower.trim() == it }
    }

    private fun mapDimensionToDirective(dim: String): String? {
        return when (dim.lowercase()) {
            "intuition_first" -> "Prioritize intuitive mental models before formal definitions"
            "examples_first" -> "Show concrete examples before theoretical explanations"
            "step_by_step" -> "Provide step-by-step sequential breakdowns"
            "why_before_how" -> "Explain the architectural and conceptual 'why' before showing 'how'"
            "how_before_why" -> "Show practical implementation mechanics immediately before theoretical context"
            "code_before_explanation" -> "Present clean code snippet first, followed by concise breakdown"
            "explanation_before_code" -> "Explain algorithmic mechanics and trade-offs before providing code"
            "analogy_preferred" -> "Ground abstract concepts in intuitive real-world analogies"
            "visual_reasoning" -> "Use ASCII state transitions, memory layout diagrams, or execution traces"
            "concrete_before_abstract" -> "Start with concrete data values before generalizing to abstract concepts"
            "deep_dive_preferred" -> "Explore low-level internals, OS syscalls, and boundary conditions"
            "real_world_examples" -> "Use production-grade, enterprise-scale real-world scenarios"
            "tradeoffs_preferred" -> "Explicitly contrast engineering trade-offs, constraints, and alternatives"
            "short_iterations" -> "Keep explanations focused in short, interactive feedback increments"
            else -> null
        }
    }

    /**
     * Determines if a preference's topic is relevant to the current query.
     */
    private fun isTopicRelevant(topic: String, queryLower: String): Boolean {
        if (topic == "general") return true

        return when (topic.lowercase()) {
            "dsa" -> TOPIC_KEYWORDS_DSA.any { queryLower.contains(it) }
            "devops" -> TOPIC_KEYWORDS_DEVOPS.any { queryLower.contains(it) }
            else -> true
        }
    }

    companion object {
        private const val TAG = "UserProfileManager"
        private const val PROFILE_DIR = "profile"
        private const val PROFILE_FILE = "user_profile.json"

        /** Maximum tokens the profile directives may consume in the system prompt. */
        const val MAX_PROFILE_TOKEN_BUDGET = 150

        /** Conservative chars-per-token ratio (matches ContextManager). */
        private const val CHARS_PER_TOKEN = 3.8

        /** Keywords that indicate a DSA-related query. */
        private val TOPIC_KEYWORDS_DSA = listOf(
            "algorithm", "data structure", "sorting", "searching", "tree", "graph",
            "array", "linked list", "stack", "queue", "hash", "heap", "dsa",
            "dynamic programming", "recursion", "binary search", "traversal",
            "complexity", "big o", "brute force", "optimal", "leetcode", "coding problem",
            "backtracking", "greedy", "divide and conquer", "two pointer", "sliding window"
        )

        /** Keywords that indicate a DevOps-related query. */
        private val TOPIC_KEYWORDS_DEVOPS = listOf(
            "devops", "docker", "kubernetes", "k8s", "ci/cd", "pipeline", "deploy",
            "container", "infrastructure", "terraform", "ansible", "jenkins",
            "monitoring", "scaling", "load balancer", "nginx", "aws", "gcp", "azure",
            "helm", "prometheus", "grafana", "microservice", "service mesh"
        )
    }

    /**
     * Records or updates a concept the user struggled with.
     */
    fun recordStruggledConcept(
        concept: String,
        domain: String,
        lastExplanationLevel: String = "LEVEL_2_ANALOGY",
        successfulLevel: String? = null
    ): Result<Unit> = runCatching {
        require(concept.isNotBlank()) { "Concept cannot be empty" }
        val current = getProfile()
        val existingIndex = current.struggledConcepts.indexOfFirst {
            it.concept.equals(concept.trim(), ignoreCase = true)
        }

        val updatedList = if (existingIndex >= 0) {
            val existing = current.struggledConcepts[existingIndex]
            val updated = existing.copy(
                failedAttempts = existing.failedAttempts + 1,
                lastExplanationLevel = lastExplanationLevel,
                successfulLevel = successfulLevel ?: existing.successfulLevel,
                lastSeen = System.currentTimeMillis()
            )
            current.struggledConcepts.toMutableList().apply { set(existingIndex, updated) }
        } else {
            current.struggledConcepts + StruggledConcept(
                concept = concept.trim(),
                domain = domain,
                failedAttempts = 1,
                lastExplanationLevel = lastExplanationLevel,
                successfulLevel = successfulLevel,
                lastSeen = System.currentTimeMillis()
            )
        }

        saveProfile(current.copy(struggledConcepts = updatedList)).getOrThrow()
    }

    /**
     * Decays the confidence of inferred preferences that are not reinforced or are contradicted.
     */
    fun decayInferredPreferences(
        decayFactor: Float = 0.95f,
        minConfidenceThreshold: Float = 0.15f
    ): Result<Unit> = runCatching {
        val current = getProfile()
        val decayed = current.inferredPreferences.mapNotNull { pref ->
            val newConfidence = pref.confidence * decayFactor
            if (newConfidence >= minConfidenceThreshold) {
                pref.copy(confidence = newConfidence, updatedAt = System.currentTimeMillis())
            } else {
                null
            }
        }
        // Also decay continuous learning dimensions
        val decayedDimensions = current.learningDimensions.mapValues { (_, score) ->
            (score * decayFactor).coerceAtLeast(0.0f)
        }
        saveProfile(current.copy(inferredPreferences = decayed, learningDimensions = decayedDimensions)).getOrThrow()
    }

    /**
     * Updates or sets a continuous learning dimension score.
     */
    fun updateLearningDimension(
        dimension: String,
        delta: Float
    ): Result<Float> = runCatching {
        require(dimension.isNotBlank()) { "Dimension cannot be empty" }
        val current = getProfile()
        val currentVal = current.learningDimensions.getOrDefault(dimension.trim().lowercase(), 0.5f)
        val updatedVal = (currentVal + delta).coerceIn(0.0f, 1.0f)
        val updatedMap = current.learningDimensions + (dimension.trim().lowercase() to updatedVal)
        saveProfile(current.copy(learningDimensions = updatedMap)).getOrThrow()
        updatedVal
    }

    /**
     * Gets the score for a specific learning dimension, defaulting to 0.5 (neutral).
     */
    fun getLearningDimension(dimension: String): Float {
        return getProfile().learningDimensions.getOrDefault(dimension.trim().lowercase(), 0.5f)
    }

    /**
     * Clears all behavior preferences, learning dimensions, corrections, and struggled concepts.
     */
    fun clearProfile(): Result<Unit> = runCatching {
        saveProfile(UserProfile()).getOrThrow()
    }
}
