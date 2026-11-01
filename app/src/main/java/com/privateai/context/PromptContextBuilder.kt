package com.privateai.context

import com.privateai.intelligence.ResponseStrategy
import com.privateai.intelligence.StrategyResolver
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import com.privateai.memory.isFactualContext
import com.privateai.persona.AiPersona
import com.privateai.persona.RelationshipStyle
import com.privateai.profile.UserProfileManager

/**
 * Orchestrates prompt assembly, premise verification directives, response strategy selection,
 * noise-suppressed memory retrieval, relationship styles, and user behavioral profile preferences.
 *
 * STRICT SEPARATION OF RESPONSIBILITIES:
 * - UserProfileManager provides HOW the user learns (learning dimensions, presentation directives).
 * - MemoryManager provides WHAT Jarvis knows about the user (projects, tech stack, goals, user facts).
 * - RelationshipStyle directs tone (Friend, Mentor, Supportive Guide, Direct) subject to explicit in-turn overrides.
 */
class PromptContextBuilder(
    private val memoryManager: MemoryManager,
    private val userProfileManager: UserProfileManager? = null,
    private val strategyResolver: StrategyResolver = StrategyResolver()
) {

    companion object {
        const val PREMISE_VERIFICATION_DIRECTIVE = """
[CRITICAL EVALUATION & PREMISE VERIFICATION]
- Independently verify the user's factual, mathematical, programming, or algorithmic premises before building on them.
- If the user relies on a demonstrably false premise (e.g. invalid bounds, incorrect algorithm complexity, impossible constraints, flawed logic), clearly state: "That premise is incorrect. Here's why..." and briefly explain the flaw before solving the actual problem.
- If the premise is valid, do NOT introduce unnecessary skepticism or contrarian pushback."""

        /** Maximum tokens the memory section may consume in the system prompt. */
        const val MAX_MEMORY_TOKENS = 250

        private const val MAX_GOALS = 3
        /** Minimum relevance score required to inject a memory (filters out weak single-word overlaps). */
        const val MIN_MEMORY_RELEVANCE_SCORE = 4

        private const val CHARS_PER_TOKEN = 3.8

        private val STOP_WORDS = setOf(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for", "with",
            "about", "against", "between", "into", "through", "during", "before", "after",
            "above", "below", "from", "up", "down", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "having", "do", "does", "did", "doing", "can",
            "could", "should", "would", "shall", "will", "may", "might", "must", "it", "its",
            "this", "that", "these", "those", "i", "you", "he", "she", "we", "they", "me", "my",
            "how", "what", "why", "where", "when", "which", "who", "whom", "tell", "explain",
            "give", "show", "help", "work", "make", "just", "please", "project"
        )

        private val SIMPLE_FACTUAL_PATTERNS = listOf(
            "what is the capital of", "who wrote", "what year was", "where is",
            "population of", "height of", "who invented", "capital of",
            "history of", "roman empire", "who discovered", "distance to",
            "speed of light", "formula for", "when did"
        )
    }

    /** Estimates token count from string length. */
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return kotlin.math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    /**
     * Builds the complete system instruction for the LLM based on resolved strategy,
     * premise verification rules, relationship styles, behavioral profile preferences, and relevant factual memories.
     */
    fun buildSystemInstruction(
        userQuery: String,
        persona: AiPersona = AiPersona.GENERAL,
        relationshipStyle: RelationshipStyle = RelationshipStyle.DIRECT,
        conversationContext: String? = null,
        maxMemories: Int = 4,
        maxMemoryTokens: Int = MAX_MEMORY_TOKENS
    ): AssembledContext {
        val promptBuilder = StringBuilder()

        // 1. Resolve Intelligence Response Strategy
        val userProfile = userProfileManager?.getProfile()
        val strategy = strategyResolver.resolveStrategy(
            userQuery = userQuery,
            conversationContext = conversationContext,
            fallbackPersona = persona,
            userProfile = userProfile
        )
        promptBuilder.append(strategy.systemPrompt)

        // 2. Critical evaluation and premise verification directive
        promptBuilder.append("\n\n").append(PREMISE_VERIFICATION_DIRECTIVE)

        // 3. Relationship Style Directive (unless user explicitly requests direct/concise in-turn override)
        val queryLower = userQuery.lowercase()
        val hasExplicitConciseOverride = queryLower.contains("code only") ||
                queryLower.contains("concise") ||
                queryLower.contains("two lines only") ||
                queryLower.contains("one line only") ||
                queryLower.contains("no explanation")

        if (!hasExplicitConciseOverride && relationshipStyle != RelationshipStyle.DIRECT) {
            promptBuilder.append("\n\n").append(relationshipStyle.styleDirective)
        }

        // 4. User Behavior Profile Directives (HOW the user learns)
        val behaviorDirectives = userProfileManager?.getRelevantBehaviorDirectives(userQuery) ?: emptyList()
        if (behaviorDirectives.isNotEmpty()) {
            promptBuilder.append("\n\n[USER PREFERRED ASSISTANT BEHAVIOR]")
            promptBuilder.append("\nFollow these user behavioral and presentation preferences:")
            for (directive in behaviorDirectives) {
                promptBuilder.append("\n- ").append(directive)
            }
        }

        // 5. Relevant Factual Memories (WHAT Jarvis knows about the user/projects)
        val relevantMemories = retrieveRelevantMemories(
            query = userQuery,
            limit = maxMemories,
            maxTokenBudget = maxMemoryTokens
        )
        if (relevantMemories.isNotEmpty()) {
            promptBuilder.append("\n\n[USER KNOWLEDGE & RELEVANT PROJECT CONTEXT]")
            promptBuilder.append("\nUse the following remembered user facts and project context where relevant:")
            for (memory in relevantMemories) {
                val typeLabel = when (memory.type) {
                    MemoryType.PROJECT -> "Project"
                    MemoryType.TECH_STACK -> "Tech Stack"
                    MemoryType.GOAL -> "Goal"
                    MemoryType.USER_FACT, MemoryType.FACT -> "Fact"
                    MemoryType.SUMMARY -> "Context Summary"
                    MemoryType.CONTEXT, MemoryType.PREFERENCE -> "Context"
                }
                promptBuilder.append("\n- [$typeLabel]: ${memory.content}")
            }
        }

        // 6. Standing goals (always injected for personal styles so Jarvis can flag drift)
        val personalStyle = relationshipStyle == RelationshipStyle.COMPANION ||
                relationshipStyle == RelationshipStyle.MENTOR ||
                relationshipStyle == RelationshipStyle.FRIEND ||
                relationshipStyle == RelationshipStyle.PARENT
        if (personalStyle && !hasExplicitConciseOverride) {
            val goals = memoryManager.getAllMemories()
                .filter { it.type == MemoryType.GOAL }
                .sortedByDescending { it.updatedAt }
                .take(MAX_GOALS)
            if (goals.isNotEmpty()) {
                promptBuilder.append("\n\n[USER GOALS]")
                promptBuilder.append("\nIf the current request clearly drifts from these goals, briefly and kindly say so:")
                for (goal in goals) promptBuilder.append("\n- ").append(goal.content)
            }
        }

        return AssembledContext(
            systemInstruction = promptBuilder.toString(),
            injectedMemories = relevantMemories,
            injectedBehaviorDirectives = behaviorDirectives,
            resolvedStrategy = strategy
        )
    }

    /**
     * Retrieves up to [limit] most relevant factual memories based on multi-signal scoring,
     * query relevance, and strict noise suppression under [maxTokenBudget].
     */
    fun retrieveRelevantMemories(
        query: String,
        limit: Int = 4,
        maxTokenBudget: Int = MAX_MEMORY_TOKENS
    ): List<Memory> {
        val allMemories = memoryManager.getAllMemories()
        if (allMemories.isEmpty() || query.isBlank()) return emptyList()

        val queryLower = query.lowercase().trim()

        // Noise suppression: completely suppress memory injection on simple factual or trivia queries
        if (isSimpleFactualOrTriviaQuery(queryLower)) {
            return emptyList()
        }

        // Extract meaningful search terms
        val queryKeywords = extractKeywords(queryLower)
        if (queryKeywords.isEmpty()) {
            return emptyList()
        }

        // Filter for factual memories and score relevance
        val scored = allMemories
            .filter { it.type.isFactualContext && it.type != MemoryType.PREFERENCE }
            .mapNotNull { memory ->
                val score = calculateMemoryScore(memory, queryLower, queryKeywords)
                if (score >= MIN_MEMORY_RELEVANCE_SCORE) memory to score else null
            }

        val sorted = scored
            .sortedWith(compareByDescending<Pair<Memory, Int>> { it.second }.thenByDescending { it.first.updatedAt })

        val result = mutableListOf<Memory>()
        var accumulatedTokens = 0

        for ((memory, _) in sorted) {
            if (result.size >= limit) break
            val memoryFormatted = "- [${memory.type.name}]: ${memory.content}"
            val memTokens = estimateTokens(memoryFormatted)
            if (accumulatedTokens + memTokens > maxTokenBudget) break
            result.add(memory)
            accumulatedTokens += memTokens
        }

        return result
    }

    private fun isSimpleFactualOrTriviaQuery(queryLower: String): Boolean {
        return SIMPLE_FACTUAL_PATTERNS.any { queryLower.contains(it) }
    }

    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9_\\-]"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()
    }

    /**
     * Multi-signal relevance scoring:
     * - Exact project or tech stack name matches in query: +5 points
     * - Explicit tag matches: +4 points per tag match
     * - Exact phrase matches: +6 points
     * - Content keyword matches: +2 points per keyword match
     */
    private fun calculateMemoryScore(memory: Memory, queryLower: String, keywords: Set<String>): Int {
        var score = 0
        val contentLower = memory.content.lowercase()

        // 1. Tag overlap (high signal)
        for (tag in memory.tags) {
            val tagLower = tag.lowercase()
            if (queryLower.contains(tagLower) || keywords.contains(tagLower)) {
                score += 4
            }
        }

        // 2. Project / Tech Stack specialized bonus
        if (memory.type == MemoryType.PROJECT || memory.type == MemoryType.TECH_STACK) {
            for (kw in keywords) {
                if (contentLower.contains(kw)) {
                    score += 3
                }
            }
        }

        // 3. Keyword matches in content
        for (kw in keywords) {
            if (contentLower.contains(kw)) {
                score += 2
            }
        }

        // 4. Strong full phrase overlap
        for (tag in memory.tags) {
            val tagLower = tag.lowercase()
            if (tagLower.length > 4 && queryLower.contains(tagLower)) {
                score += 3
            }
        }

        return score
    }
}

/**
 * Result of prompt assembly.
 */
data class AssembledContext(
    val systemInstruction: String,
    val injectedMemories: List<Memory>,
    val injectedBehaviorDirectives: List<String> = emptyList(),
    val resolvedStrategy: ResponseStrategy? = null
)
