package com.privateai.intelligence

/**
 * Deterministic, multi-signal detector for identifying the user's current cognitive and situational state.
 *
 * Implements conservative confidence thresholds for emotional states to avoid false-positive classifications.
 */
class UserStateDetector {

    /**
     * Detects the user's cognitive state from their current query and recent conversational context.
     */
    fun detectUserState(
        userQuery: String,
        conversationContext: String? = null
    ): UserState {
        val queryLower = userQuery.lowercase().trim()
        val contextLower = conversationContext?.lowercase() ?: ""

        // 1. Explicit Frustration (Requires strong markers indicating repeated failure/exhaustion)
        if (isExplicitlyFrustrated(queryLower)) {
            return UserState.FRUSTRATED
        }

        // 2. Explicit Confusion & Misunderstanding
        if (isConfusedOrMisunderstood(queryLower)) {
            return UserState.CONFUSED
        }

        // 3. Verifying & Validating Understanding
        if (isVerifying(queryLower)) {
            return UserState.VERIFYING
        }

        // 4. Deep-Dive & Advanced Technical Detail
        if (isDeepDive(queryLower)) {
            return UserState.DEEP_DIVE
        }

        // 5. Comparing & Trade-off Evaluation
        if (isComparing(queryLower)) {
            return UserState.COMPARING
        }

        // 6. Planning & Roadmap Strategy
        if (isPlanning(queryLower)) {
            return UserState.PLANNING
        }

        // 7. Implementation & Code Construction
        if (isImplementing(queryLower)) {
            return UserState.IMPLEMENTING
        }

        // 8. Debugging & Error Diagnosis (Diagnostic questions fall here, not in FRUSTRATED)
        if (isDebugging(queryLower)) {
            return UserState.DEBUGGING
        }

        // 9. Exploring Alternatives
        if (isExploring(queryLower)) {
            return UserState.EXPLORING
        }

        // 10. Greetings & General Chit-Chat
        if (isGeneralConversation(queryLower)) {
            return UserState.GENERAL_CONVERSATION
        }

        // 11. New Concept Introduction
        if (isNewConcept(queryLower, contextLower)) {
            return UserState.NEW_CONCEPT
        }

        // 12. Active Learning
        if (queryLower.contains("explain") || queryLower.contains("teach me") || queryLower.contains("how does") || queryLower.contains("why does")) {
            return UserState.LEARNING
        }

        return UserState.UNKNOWN
    }

    private fun isExplicitlyFrustrated(q: String): Boolean {
        val frustratedKeywords = listOf(
            "driving me crazy", "so frustrated", "i give up",
            "nothing works", "tired of this", "waste of time",
            "still failing after multiple tries", "this is impossible",
            "still doesn't work after everything", "still doesnt work after everything",
            "tried everything and still"
        )
        return frustratedKeywords.any { q.contains(it) }
    }

    private fun isConfusedOrMisunderstood(q: String): Boolean {
        val confusionKeywords = listOf(
            "don't understand", "dont understand", "didn't understand", "didnt understand",
            "you lost me", "what does that mean", "makes no sense", "confused", "i'm confused",
            "i am confused", "what do you mean", "still don't get", "still dont get",
            "no, that's not what i meant", "that is not what i meant", "i didn't ask for that",
            "not what i asked", "simpler please", "explain simpler", "too complicated",
            "too dense", "speak plain english"
        )
        return confusionKeywords.any { q.contains(it) }
    }

    private fun isVerifying(q: String): Boolean {
        val verifyingKeywords = listOf(
            "is my approach right", "is this correct", "did i understand", "did i get this right",
            "so basically", "does that mean", "does this mean", "am i right in thinking",
            "is it true that", "verify my", "check my logic", "is my understanding correct"
        )
        return verifyingKeywords.any { q.contains(it) }
    }

    private fun isDeepDive(q: String): Boolean {
        val deepDiveKeywords = listOf(
            "go deeper", "under the hood", "internals", "how does it work internally",
            "memory layout", "low level", "low-level", "bytecode", "assembly", "in-depth",
            "deep dive", "kernel", "i already understand the basics", "skip the basics",
            "advanced mechanics", "behind the scenes", "hardware level"
        )
        return deepDiveKeywords.any { q.contains(it) }
    }

    private fun isComparing(q: String): Boolean {
        val compareKeywords = listOf(
            " vs ", " versus ", "compare ", "difference between", "pros and cons",
            "which is better", "advantages of", "tradeoffs between", "trade-offs between"
        )
        return compareKeywords.any { q.contains(it) }
    }

    private fun isPlanning(q: String): Boolean {
        val planningKeywords = listOf(
            "study plan", "roadmap", "how should i organize", "steps to architect",
            "learning path", "curriculum", "step-by-step plan for", "how do i prepare for",
            "let's design", "lets design"
        )
        return planningKeywords.any { q.contains(it) }
    }

    private fun isImplementing(q: String): Boolean {
        val implementingKeywords = listOf(
            "write the code", "show me how to implement", "show kotlin code", "give me the code",
            "code this", "how to write a function", "implement this", "let's build",
            "create a class", "scaffold a", "write a script", "how to fix it in my"
        )
        return implementingKeywords.any { q.contains(it) }
    }

    private fun isDebugging(q: String): Boolean {
        val debuggingKeywords = listOf(
            "why is this crashing", "why isn't this working", "why is this failing",
            "why doesn't this", "why doesnt this", "why can't the", "why cant the",
            "nullpointerexception", "illegalstateexception", "error:", "exception",
            "crash", "stacktrace", "fix this bug", "what's wrong with this code",
            "syntax error", "build failed", "crashloopbackoff", "out of memory"
        )
        return debuggingKeywords.any { q.contains(it) }
    }

    private fun isExploring(q: String): Boolean {
        val exploringKeywords = listOf(
            "what are some alternatives", "what other options", "what else is out there",
            "alternatives to", "other libraries", "different approaches"
        )
        return exploringKeywords.any { q.contains(it) }
    }

    private fun isGeneralConversation(q: String): Boolean {
        val cleanQ = q.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        val generalKeywords = listOf(
            "hello", "hi", "hey", "how are you", "good morning", "good evening",
            "thank you", "thanks", "who are you", "what can you do"
        )
        return generalKeywords.any { cleanQ == it || cleanQ.startsWith("$it ") || cleanQ.contains(it) }
    }

    private fun isNewConcept(q: String, context: String): Boolean {
        if (context.isBlank()) {
            if (q.startsWith("what is ") || q.startsWith("what are ") || q.startsWith("introduce me to ")) {
                return true
            }
        }
        return q.contains("never heard of") || q.contains("what is meant by")
    }
}
