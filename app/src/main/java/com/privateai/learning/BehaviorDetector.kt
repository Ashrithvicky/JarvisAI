package com.privateai.learning

import com.privateai.profile.PreferenceCategory

/**
 * Deterministic, multi-signal detector for behavioral signals in user messages.
 *
 * Uses multi-signal pattern matching to detect HOW the user wants the assistant to behave.
 * Does NOT use ML, external services, or network access.
 * Does NOT treat arbitrary factual statements or temporary cognitive confusion as permanent preferences.
 *
 * Supports the 14 first-class VIVVA learning dimensions.
 */
class BehaviorDetector {

    companion object {
        private const val TAG = "BehaviorDetector"
    }

    /**
     * A detection rule mapping trigger phrases to a behavioral signal.
     */
    private data class DetectionRule(
        val triggerPhrases: List<String>,
        val category: PreferenceCategory,
        val description: String,
        val topic: String = "general",
        val source: SignalSource = SignalSource.KEYWORD_PATTERN
    )

    /**
     * All detection rules across the 14 VIVVA learning dimensions and presentation preferences.
     */
    private val rules: List<DetectionRule> = listOf(
        // --- DETAIL_LEVEL ---
        DetectionRule(
            triggerPhrases = listOf(
                "more detail", "in detail", "explain more", "elaborate", "in depth",
                "go deeper", "can you expand", "tell me more about",
                "more thorough", "detailed explanation", "detailed reasoning",
                "i want detail", "need more detail", "thorough explanation", "deep dive"
            ),
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "keep it short", "be concise", "please be concise", "very concise",
                "brief", "tldr", "tl;dr",
                "summarize", "short answer", "don't over-explain",
                "just the answer", "less detail", "keep it simple", "two lines"
            ),
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide concise, brief answers"
        ),

        // --- EXPLANATION_DEPTH & LEARNING DIMENSIONS ---
        DetectionRule(
            triggerPhrases = listOf(
                "step by step", "step-by-step", "walk me through",
                "explain each step", "show your reasoning", "break it down",
                "explain the reasoning", "show the thought process"
            ),
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Provide step-by-step explanations with reasoning"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "give me an example", "show me an example", "for example",
                "with examples", "practical example", "real-world example",
                "concrete example", "real world example"
            ),
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Include practical examples in explanations"
        ),

        // --- CODING_STYLE ---
        DetectionRule(
            triggerPhrases = listOf(
                "code block", "copyable code", "formatted code",
                "show the code", "give me the code", "write the code",
                "complete code", "full code"
            ),
            category = PreferenceCategory.CODING_STYLE,
            description = "Present code in clean, copyable fenced code blocks"
        ),

        // --- PROBLEM_SOLVING (DSA-specific) ---
        DetectionRule(
            triggerPhrases = listOf(
                "dry run", "dry-run", "trace through", "walk through the example",
                "trace the execution", "step through the algorithm"
            ),
            category = PreferenceCategory.PROBLEM_SOLVING,
            description = "Include dry-run traces for algorithms",
            topic = "DSA"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "time complexity", "space complexity", "big o", "big-o",
                "complexity analysis", "analyze the complexity",
                "what is the complexity", "runtime analysis"
            ),
            category = PreferenceCategory.PROBLEM_SOLVING,
            description = "Always include time and space complexity analysis",
            topic = "DSA"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "compare brute force", "brute force vs", "optimal vs brute",
                "brute force approach", "naive approach vs"
            ),
            category = PreferenceCategory.PROBLEM_SOLVING,
            description = "Compare brute-force and optimal approaches",
            topic = "DSA"
        ),

        // --- PROBLEM_SOLVING (DevOps-specific) ---
        DetectionRule(
            triggerPhrases = listOf(
                "production example", "real-world scenario", "company-level",
                "enterprise", "at scale", "in production", "practical scenario",
                "real company", "industry example"
            ),
            category = PreferenceCategory.PROBLEM_SOLVING,
            description = "Use practical, production-level examples",
            topic = "DevOps"
        ),

        // --- TEACHING_APPROACH ---
        DetectionRule(
            triggerPhrases = listOf(
                "explain why", "tell me why", "show why", "the reasoning behind",
                "not just the answer", "don't just give me the answer",
                "help me understand", "teach me", "i want to learn",
                "explain the reason"
            ),
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Explain the reasoning, not just the final answer"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "verify", "double check", "check my work", "debug this",
                "find the bug", "what's wrong with", "is this correct",
                "validate this"
            ),
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Verify and debug code/solutions before presenting",
            topic = "general"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "structured answer", "organized answer", "use headers",
                "use bullet points", "format the answer", "numbered list",
                "well-structured", "structured format"
            ),
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Provide well-structured, organized answers"
        ),

        // --- FIRST-CLASS LEARNING_STYLE DIMENSIONS ---
        DetectionRule(
            triggerPhrases = listOf(
                "analogy", "give me an analogy", "analogies", "real world analogy",
                "like what in real life", "analogy please"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Explain concepts using intuitive real-world analogies"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "intuition first", "explain intuition", "mental model first",
                "intuitive explanation", "intuition before"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Explain intuitive mental models before formal definitions"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "example first", "examples first", "show example first",
                "concrete example before theory", "examples before"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Show concrete examples before theoretical explanations"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "why before how", "explain why before showing", "why it works before how",
                "explain why this works before"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Explain the architectural and conceptual 'why' before showing 'how'"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "how before why", "show how before why", "show practical mechanics first"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Show practical implementation mechanics immediately before theoretical context"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "code before explanation", "show code first", "code first then explain",
                "give me the code first", "code first"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Present clean code snippet first, followed by concise breakdown"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "explain before code", "explanation before code", "explain the logic before writing code",
                "don't just dump code", "explain first then code"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Explain algorithmic mechanics and trade-offs before providing code"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "visual reasoning", "draw a diagram", "ascii diagram", "trace diagram",
                "visualize this", "state transition diagram", "visual representation"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Use ASCII state transitions, memory layout diagrams, or execution traces"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "concrete before abstract", "start with concrete numbers", "use actual values first"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Start with concrete data values before generalizing to abstract concepts"
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "trade-offs", "tradeoffs", "pros and cons", "compare alternatives",
                "contrast trade-offs", "tradeoffs between"
            ),
            category = PreferenceCategory.LEARNING_STYLE,
            description = "Explicitly contrast engineering trade-offs, constraints, and alternatives"
        ),

        // --- COMMUNICATION_TONE ---
        DetectionRule(
            triggerPhrases = listOf(
                "mentor style", "challenge my thinking", "be honest", "tell me if i'm wrong"
            ),
            category = PreferenceCategory.COMMUNICATION_TONE,
            description = "Actively challenge assumptions and offer mentoring guidance"
        )
    )

    /**
     * Correction-detection rules for explicit user corrections.
     * These produce EXPLICIT_CORRECTION signals with higher weight.
     */
    private val correctionRules: List<DetectionRule> = listOf(
        DetectionRule(
            triggerPhrases = listOf(
                "don't just agree", "tell me if i'm wrong", "correct me",
                "challenge my assumptions", "push back", "be critical"
            ),
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Challenge incorrect assumptions and provide corrections",
            source = SignalSource.EXPLICIT_CORRECTION
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "don't give me a short answer", "that was too brief",
                "not enough detail", "you were too concise", "expand on that"
            ),
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide detailed, thorough explanations",
            source = SignalSource.EXPLICIT_CORRECTION
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "too much detail", "that was too long", "shorter",
                "you're overexplaining", "too verbose"
            ),
            category = PreferenceCategory.DETAIL_LEVEL,
            description = "Provide concise, brief answers",
            source = SignalSource.EXPLICIT_CORRECTION
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "explain the reasoning", "show me why", "i want to understand why",
                "don't just show the code", "explain what the code does"
            ),
            category = PreferenceCategory.TEACHING_APPROACH,
            description = "Explain the reasoning, not just the final answer",
            source = SignalSource.EXPLICIT_CORRECTION
        ),
        DetectionRule(
            triggerPhrases = listOf(
                "practical example", "real company example", "production example",
                "not a toy example", "realistic example"
            ),
            category = PreferenceCategory.PROBLEM_SOLVING,
            description = "Use practical, production-level examples",
            source = SignalSource.EXPLICIT_CORRECTION
        )
    )

    /**
     * Identifies if a query is asking for the definition/meaning of a concept
     * or expressing temporary confusion, rather than requesting a permanent behavior.
     */
    private fun isDefinitionalOrTemporaryConfusion(messageLower: String): Boolean {
        val trimmed = messageLower.trim().trimEnd('?', '.', '!')

        // Asking for definition or meaning of a term (e.g. "What does detailed reasoning mean?")
        if (trimmed.endsWith(" mean") || trimmed.contains("meaning of") ||
            trimmed.contains("definition of") || trimmed.startsWith("define ")) {
            return true
        }

        // Asking why a style/practice is preferred/used (e.g. "Why is concise code preferred?")
        if ((trimmed.startsWith("why is ") || trimmed.startsWith("why are ")) &&
            (trimmed.contains("preferred") || trimmed.contains("better") || trimmed.contains("recommended"))) {
            return true
        }

        // Temporary confusion statements (these belong in UserStateDetector, not in long-term profile)
        if (trimmed == "i don't understand" || trimmed == "i dont understand" ||
            trimmed == "i'm confused" || trimmed == "i am confused" ||
            trimmed == "still don't understand" || trimmed == "still dont understand" ||
            trimmed.startsWith("i don't understand why") || trimmed.startsWith("i dont understand why")) {
            return true
        }

        // Questions asking about the definition of specific behavioral concepts (e.g. "What is step-by-step execution?")
        val conceptualQuestions = listOf(
            "what is step-by-step",
            "what is step by step",
            "what is detailed reasoning",
            "what is concise code",
            "what is a dry run",
            "what is complexity analysis",
            "what is an analogy",
            "what is intuition"
        )
        if (conceptualQuestions.any { trimmed.startsWith(it) && !trimmed.contains(" of this") && !trimmed.contains(" of my") && !trimmed.contains(" for this") }) {
            return true
        }

        return false
    }

    /**
     * Detect behavioral signals from a user message.
     * Returns all matching signals. May return zero, one, or multiple signals.
     */
    fun detect(userMessage: String): List<BehaviorSignal> {
        if (userMessage.isBlank()) return emptyList()

        val messageLower = userMessage.lowercase()

        // Guard: skip detection for definitional/conceptual questions and temporary confusion
        if (isDefinitionalOrTemporaryConfusion(messageLower)) return emptyList()

        val signals = mutableListOf<BehaviorSignal>()

        // Check correction rules first (higher priority)
        for (rule in correctionRules) {
            if (rule.triggerPhrases.any { messageLower.contains(it) }) {
                signals.add(
                    BehaviorSignal(
                        category = rule.category,
                        description = rule.description,
                        source = rule.source,
                        topic = rule.topic
                    )
                )
            }
        }

        // Check standard keyword pattern rules
        for (rule in rules) {
            if (rule.triggerPhrases.any { messageLower.contains(it) }) {
                // Avoid duplicating a signal already covered by a correction rule
                val alreadyCovered = signals.any {
                    it.category == rule.category && it.description == rule.description
                }
                if (!alreadyCovered) {
                    signals.add(
                        BehaviorSignal(
                            category = rule.category,
                            description = rule.description,
                            source = rule.source,
                            topic = rule.topic
                        )
                    )
                }
            }
        }

        return signals
    }
}
