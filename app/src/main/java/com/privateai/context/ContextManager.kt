package com.privateai.context

import com.privateai.conversation.Conversation
import com.privateai.conversation.Message
import com.privateai.conversation.MessageRole
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.persona.AiPersona
import com.privateai.persona.RelationshipStyle
import com.privateai.profile.UserProfileManager

/**
 * Manages token budgeting, conversation context assembly, and rolling summarization.
 *
 * Prevents prompt overflow by allocating budget across:
 * - Persona, Relationship Style & System Instructions
 * - Relevant Memories & User Behavior Directives
 * - Rolling Conversation Summary
 * - Verbatim Recent Turns
 * - Current User Query
 */
class ContextManager(
    private val memoryManager: MemoryManager,
    private val userProfileManager: UserProfileManager? = null,
    private val promptContextBuilder: PromptContextBuilder = PromptContextBuilder(memoryManager, userProfileManager)
) {

    companion object {
        /**
         * Rough character-to-token ratio for conservative estimation (1 token ≈ 3.5 - 4 chars in English/code).
         */
        const val CHARS_PER_TOKEN = 3.8

        /**
         * Standard context budget defaults (targeting 2048 - 4096 token windows).
         */
        const val DEFAULT_MAX_CONTEXT_TOKENS = 2048
        const val MIN_GENERATION_HEADROOM_TOKENS = 512
        const val MAX_MEMORY_TOKENS = 250
        const val MAX_SUMMARY_TOKENS = 300
    }

    /**
     * Estimates token count from string length.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return kotlin.math.ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    /**
     * Assembles a budgeted, optimized prompt package for LiteRT-LM.
     */
    fun assembleConversationContext(
        conversation: Conversation,
        currentUserQuery: String,
        persona: AiPersona,
        relationshipStyle: RelationshipStyle = RelationshipStyle.DIRECT,
        maxContextTokens: Int = DEFAULT_MAX_CONTEXT_TOKENS
    ): BudgetedPromptContext {
        // 1. Build System Instruction with relevant memories, relationship style, and resolved strategy
        val recentContextText = conversation.messages.takeLast(3).joinToString("\n") { "${it.role}: ${it.content}" }
        val baseAssembled = promptContextBuilder.buildSystemInstruction(
            userQuery = currentUserQuery,
            persona = persona,
            relationshipStyle = relationshipStyle,
            conversationContext = recentContextText
        )

        val systemTokens = estimateTokens(baseAssembled.systemInstruction)
        val queryTokens = estimateTokens(currentUserQuery)

        // Usable budget for history and summary
        val availableHistoryTokens = (maxContextTokens - MIN_GENERATION_HEADROOM_TOKENS - systemTokens - queryTokens)
            .coerceAtLeast(200)

        // 2. Select recent messages and generate rolling summary if needed
        val (recentMessages, summaryText) = selectRecentMessagesAndSummary(
            allMessages = conversation.messages,
            tokenBudget = availableHistoryTokens
        )

        // 3. Combine summary into system instructions if present
        val finalSystemInstruction = if (summaryText.isNotBlank()) {
            "${baseAssembled.systemInstruction}\n\n[CONVERSATION SUMMARY OF EARLIER TURNS]\n$summaryText"
        } else {
            baseAssembled.systemInstruction
        }

        val totalEstimatedTokens = estimateTokens(finalSystemInstruction) +
                queryTokens +
                recentMessages.sumOf { estimateTokens(it.content) }

        return BudgetedPromptContext(
            systemInstruction = finalSystemInstruction,
            recentMessages = recentMessages,
            currentUserQuery = currentUserQuery,
            injectedMemories = baseAssembled.injectedMemories,
            summaryUsed = summaryText.ifBlank { null },
            estimatedTotalTokens = totalEstimatedTokens,
            resolvedStrategy = baseAssembled.resolvedStrategy
        )
    }

    /**
     * Splits conversation into verbatim recent turns within the token budget
     * and a compressed summary of older turns.
     */
    fun selectRecentMessagesAndSummary(
        allMessages: List<Message>,
        tokenBudget: Int
    ): Pair<List<Message>, String> {
        if (allMessages.isEmpty()) {
            return emptyList<Message>() to ""
        }

        val recentList = mutableListOf<Message>()
        var accumulatedTokens = 0
        var splitIndex = allMessages.size

        // Iterate backwards from most recent message
        for (i in allMessages.indices.reversed()) {
            val msg = allMessages[i]
            val msgTokens = estimateTokens(msg.content) + 6 // + role overhead

            if (accumulatedTokens + msgTokens <= tokenBudget) {
                recentList.add(0, msg)
                accumulatedTokens += msgTokens
                splitIndex = i
            } else {
                break
            }
        }

        // Older messages that didn't fit into recent window
        val olderMessages = if (splitIndex > 0) allMessages.subList(0, splitIndex) else emptyList()
        val summary = if (olderMessages.isNotEmpty()) {
            generateRollingSummary(olderMessages)
        } else {
            ""
        }

        return recentList to summary
    }

    /**
     * Generates a structured compressed summary of older turns.
     */
    fun generateRollingSummary(messages: List<Message>): String {
        if (messages.isEmpty()) return ""

        val keyPoints = mutableListOf<String>()
        for (msg in messages) {
            val prefix = if (msg.role == MessageRole.USER) "User asked" else "Assistant explained"
            val snippet = msg.content
                .lines()
                .firstOrNull { it.isNotBlank() }
                ?.take(80)
                ?.trim() ?: ""
            if (snippet.isNotBlank()) {
                keyPoints.add("- $prefix: \"$snippet...\"")
            }
        }

        // Limit summary lines to keep under summary token budget
        return keyPoints.takeLast(6).joinToString("\n")
    }
}

/**
 * Result of context budgeting and prompt assembly.
 */
data class BudgetedPromptContext(
    val systemInstruction: String,
    val recentMessages: List<Message>,
    val currentUserQuery: String,
    val injectedMemories: List<Memory>,
    val summaryUsed: String?,
    val estimatedTotalTokens: Int,
    val resolvedStrategy: com.privateai.intelligence.ResponseStrategy? = null
)
