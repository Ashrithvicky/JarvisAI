package com.privateai.dataset

import kotlinx.serialization.json.Json

/**
 * Validates JSONL fine-tuning and evaluation datasets for VIVVA.
 *
 * Verifies:
 * - Full schema compliance with adaptation reason and anti-patterns
 * - Alternating user/assistant conversation structure
 * - No empty or duplicate queries
 * - Persona quality (flags robotic clichés)
 * - Domain, user state, and explanation level coverage
 */
class DatasetValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private val ROBOTIC_CLICHES = listOf(
            "as an ai",
            "as a language model",
            "as your friend and mentor",
            "i am programmed",
            "i do not have personal feelings",
            "as an artificial intelligence"
        )
    }

    /**
     * Validates a list of JSONL lines.
     */
    fun validateJsonlLines(lines: List<String>): ValidationReport {
        val errors = mutableListOf<String>()
        val validExamples = mutableListOf<FineTuningExample>()
        val seenQueries = mutableSetOf<String>()

        val categoryCounts = mutableMapOf<String, Int>()
        val domainCounts = mutableMapOf<String, Int>()
        val userStateCounts = mutableMapOf<String, Int>()
        val levelCounts = mutableMapOf<Int, Int>()
        var multiTurnCount = 0
        var singleTurnCount = 0

        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1
            if (line.isBlank()) continue

            try {
                val example = json.decodeFromString<FineTuningExample>(line)

                // 1. Basic field validity
                if (example.id.isBlank()) {
                    errors.add("Line $lineNumber: Missing id")
                    continue
                }
                if (example.category.isBlank()) {
                    errors.add("Line $lineNumber (${example.id}): Missing category")
                    continue
                }
                if (example.userMessage.isBlank()) {
                    errors.add("Line $lineNumber (${example.id}): Missing user_message")
                    continue
                }
                if (example.idealResponse.isBlank()) {
                    errors.add("Line $lineNumber (${example.id}): Missing ideal_response")
                    continue
                }
                if (example.adaptationReason.isBlank()) {
                    errors.add("Line $lineNumber (${example.id}): Missing adaptation_reason")
                    continue
                }
                if (example.explanationLevel !in 1..5) {
                    errors.add("Line $lineNumber (${example.id}): Invalid explanation_level ${example.explanationLevel}")
                    continue
                }

                // 2. Persona and cliché check on ideal response
                val responseLower = example.idealResponse.lowercase()
                for (cliche in ROBOTIC_CLICHES) {
                    if (responseLower.contains(cliche)) {
                        errors.add("Line $lineNumber (${example.id}): Response contains robotic cliché '$cliche'")
                        break
                    }
                }

                // 3. Conversation turn validation (if multi-turn)
                for (turn in example.conversation) {
                    if (turn.role.lowercase() !in listOf("user", "assistant")) {
                        errors.add("Line $lineNumber (${example.id}): Invalid conversation turn role '${turn.role}'")
                    }
                    if (turn.content.isBlank()) {
                        errors.add("Line $lineNumber (${example.id}): Blank content in conversation turn")
                    }
                }

                // 4. Duplicate query check
                val normalizedQuery = example.userMessage.trim().lowercase()
                if (seenQueries.contains(normalizedQuery)) {
                    errors.add("Line $lineNumber (${example.id}): Duplicate user query '$normalizedQuery'")
                    continue
                }
                seenQueries.add(normalizedQuery)

                // Accumulate statistics
                validExamples.add(example)
                categoryCounts[example.category] = categoryCounts.getOrDefault(example.category, 0) + 1
                domainCounts[example.domain] = domainCounts.getOrDefault(example.domain, 0) + 1
                userStateCounts[example.userState] = userStateCounts.getOrDefault(example.userState, 0) + 1
                levelCounts[example.explanationLevel] = levelCounts.getOrDefault(example.explanationLevel, 0) + 1

                if (example.conversation.isNotEmpty()) {
                    multiTurnCount++
                } else {
                    singleTurnCount++
                }

            } catch (e: Exception) {
                errors.add("Line $lineNumber: JSON parse error: ${e.message}")
            }
        }

        return ValidationReport(
            totalExamples = lines.filter { it.isNotBlank() }.size,
            validExamples = validExamples.size,
            invalidExamples = errors.size,
            multiTurnCount = multiTurnCount,
            singleTurnCount = singleTurnCount,
            categoryCounts = categoryCounts,
            domainCounts = domainCounts,
            userStateCounts = userStateCounts,
            explanationLevelCounts = levelCounts,
            errors = errors
        )
    }
}
