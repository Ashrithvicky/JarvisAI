package com.privateai.memory

/** Pulls durable facts and goals out of what the user says, using offline pattern matching. */
object MemoryExtractor {

    class Candidate(val content: String, val type: MemoryType)

    private val rules: List<Pair<Regex, MemoryType>> = listOf(
        Regex("""\b(?:my goal is|my aim is|i want to|i wanna|i'm trying to|i am trying to|i plan to|i'm planning to|i am planning to|i'd like to become|i want to become)\s+(.{6,120}?)(?:[.!?\n]|$)""", RegexOption.IGNORE_CASE) to MemoryType.GOAL,
        Regex("""\b(?:i'm building|i am building|i'm working on|i am working on|i'm developing|i am developing)\s+(.{4,100}?)(?:[.!?\n]|$)""", RegexOption.IGNORE_CASE) to MemoryType.PROJECT,
        Regex("""\b(?:my name is|call me|i work (?:at|for|as)|i study|i'm a|i am a|i live in)\s+(.{2,80}?)(?:[.!?\n,]|$)""", RegexOption.IGNORE_CASE) to MemoryType.USER_FACT
    )

    private val prefixes = mapOf(
        MemoryType.GOAL to "User's goal: ",
        MemoryType.PROJECT to "User is working on: ",
        MemoryType.USER_FACT to "About the user: "
    )

    fun extract(message: String): List<Candidate> {
        if (message.length < 10) return emptyList()
        val found = mutableListOf<Candidate>()
        for ((regex, type) in rules) {
            regex.find(message)?.let { match ->
                val text = if (type == MemoryType.USER_FACT) match.value.trim().trimEnd('.', ',', '!', '?')
                else match.groupValues[1].trim()
                if (text.isNotBlank()) found += Candidate(prefixes.getValue(type) + text, type)
            }
        }
        return found
    }

    /** Stores new candidates, skipping ones already known. Returns how many were saved. */
    fun remember(message: String, manager: MemoryManager): Int {
        val known = manager.getAllMemories().map { it.content.lowercase() }.toSet()
        var saved = 0
        for (candidate in extract(message)) {
            if (candidate.content.lowercase() in known) continue
            if (manager.addMemory(candidate.content, candidate.type).isSuccess) saved++
        }
        return saved
    }
}
