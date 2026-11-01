package com.privateai.conversation

/**
 * Deterministic local conversation title generator.
 *
 * Extracts concise, meaningful topic titles (<=30 characters) from initial user messages
 * completely offline without network calls.
 */
object ConversationTitleGenerator {

    private val FILLER_PREFIXES = listOf(
        "can you please explain to me",
        "can you please explain",
        "can you please help me with",
        "can you please help me to",
        "can you please show me",
        "can you please write",
        "can you please tell me about",
        "can you please tell me",
        "can you please",
        "can you explain to me",
        "can you explain",
        "can you help me with",
        "can you help me to",
        "can you show me how to",
        "can you show me",
        "can you write a",
        "can you write",
        "can you",
        "could you please",
        "could you",
        "please explain to me",
        "please explain",
        "please help me to",
        "please help me with",
        "please show me how to",
        "please show me",
        "please write a",
        "please write",
        "please tell me about",
        "please tell me",
        "please",
        "help me to write a",
        "help me to",
        "help me with",
        "help me",
        "how do i configure a",
        "how do i configure",
        "how do i implement a",
        "how do i implement",
        "how do i write a",
        "how do i write",
        "how do i",
        "how do we",
        "how to configure a",
        "how to configure",
        "how to implement a",
        "how to implement",
        "how to write a",
        "how to write",
        "how to use a",
        "how to use",
        "how to",
        "how can i",
        "how can we",
        "what is the difference between",
        "what is the",
        "what is a",
        "what is",
        "what are the",
        "what are",
        "explain to me",
        "explain the",
        "explain",
        "tell me about the",
        "tell me about",
        "tell me",
        "show me how to",
        "show me",
        "write a",
        "write some",
        "write",
        "i want to",
        "i need to"
    )

    private val PUNCTUATION_REGEX = Regex("[?!.,;:\\\"'`]+")

    /**
     * Generates a concise title from the user's initial message.
     */
    fun generateTitle(userMessage: String): String {
        val trimmed = userMessage.trim()
        if (trimmed.isBlank() || !trimmed.any { it.isLetterOrDigit() }) return "New Chat"

        var cleaned = trimmed
        val lower = cleaned.lowercase()

        for (prefix in FILLER_PREFIXES) {
            if (lower.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }

        // Clean possessives ('s / ’s) so "Dijkstra's" becomes "Dijkstra"
        cleaned = cleaned.replace(Regex("['’]s\\b", RegexOption.IGNORE_CASE), "")
        cleaned = PUNCTUATION_REGEX.replace(cleaned, " ").trim()

        if (cleaned.isBlank() || !cleaned.any { it.isLetterOrDigit() }) {
            return "New Chat"
        }

        val words = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
        if (words.isEmpty()) return "New Chat"

        val titleBuilder = StringBuilder()
        for (word in words) {
            val formattedWord = word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (titleBuilder.isEmpty()) {
                titleBuilder.append(formattedWord)
            } else if (titleBuilder.length + 1 + formattedWord.length <= 28) {
                titleBuilder.append(" ").append(formattedWord)
            } else {
                break
            }
        }

        val result = titleBuilder.toString().trim()
        return if (result.isBlank()) "New Chat" else result
    }
}
