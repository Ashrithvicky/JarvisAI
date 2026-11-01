package com.privateai.conversation

import android.content.Context
import android.util.Log
import com.privateai.security.SecurityManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Manages conversation history.
 *
 * SECURITY: Conversations are encrypted at rest with AES-GCM and excluded from cloud backups.
 * All data stays strictly on-device. Deletion physically removes encrypted files and attachments.
 */
class ConversationManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    companion object {
        private const val TAG = "ConversationManager"
        private const val CONVERSATIONS_DIR = "conversations"
        private const val ATTACHMENTS_DIR = "attachments"
        private const val CONVERSATIONS_INDEX = "index.json"
    }

    private val conversationsDir: File
        get() = File(context.noBackupFilesDir, CONVERSATIONS_DIR).also { it.mkdirs() }

    private val attachmentsDir: File
        get() = File(context.noBackupFilesDir, ATTACHMENTS_DIR).also { it.mkdirs() }

    private val indexFile: File
        get() = File(conversationsDir, CONVERSATIONS_INDEX)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Initialize the conversation manager.
     */
    fun initialize(): Result<Unit> = runCatching {
        conversationsDir.mkdirs()
        attachmentsDir.mkdirs()
        Log.d(TAG, "Conversation manager initialized")
    }

    /**
     * Create a new conversation.
     */
    fun createConversation(title: String = "New Chat", isCustomTitle: Boolean = false): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            isCustomTitle = isCustomTitle,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = emptyList()
        )
        saveConversation(conversation)
        Log.d(TAG, "Conversation created: ${conversation.id}")
        return conversation
    }

    /**
     * Get all conversations.
     */
    fun getAllConversations(): List<Conversation> {
        val summaries = loadConversationSummaries()
        return summaries.mapNotNull { summary ->
            loadConversation(summary.id)
        }
    }

    /**
     * Get a specific conversation.
     */
    fun getConversation(conversationId: String): Conversation? {
        return loadConversation(conversationId)
    }

    /**
     * Add a message to a conversation and auto-update title if appropriate.
     */
    fun addMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        autoTitle: Boolean = true
    ): Result<Message> = runCatching {
        val conversation = loadConversation(conversationId)
            ?: throw IllegalArgumentException("Conversation not found: $conversationId")

        val message = Message(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        val updatedMessages = conversation.messages + message
        var newTitle = conversation.title

        // Auto-generate title on first user message if user hasn't explicitly renamed it
        if (autoTitle && !conversation.isCustomTitle && (conversation.title == "New Chat" || conversation.title.isBlank()) && role == MessageRole.USER) {
            newTitle = ConversationTitleGenerator.generateTitle(content)
        }

        val updatedConversation = conversation.copy(
            title = newTitle,
            messages = updatedMessages,
            updatedAt = System.currentTimeMillis()
        )

        saveConversation(updatedConversation)
        Log.d(TAG, "Message added to conversation $conversationId (Title: $newTitle)")
        message
    }

    /** Removes the last user message and every message after it. */
    fun removeLastTurn(conversationId: String): Result<Unit> = runCatching {
        val conversation = loadConversation(conversationId) ?: return@runCatching
        val lastUser = conversation.messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUser < 0) return@runCatching
        saveConversation(
            conversation.copy(messages = conversation.messages.take(lastUser), updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * Get all conversation summaries from index (fast lookup).
     */
    fun getAllConversationSummaries(): List<ConversationSummary> {
        return loadConversationSummaries().sortedByDescending { it.updatedAt }
    }

    /** Sets a model-written title without marking it as a user rename. */
    fun setGeneratedTitle(conversationId: String, title: String): Result<Unit> = runCatching {
        val conversation = loadConversation(conversationId)
            ?: throw IllegalArgumentException("Conversation not found: $conversationId")
        if (conversation.isCustomTitle) return@runCatching
        saveConversation(conversation.copy(title = title.trim()))
    }

    /**
     * Explicitly rename a conversation (marks isCustomTitle = true).
     */
    fun updateConversationTitle(conversationId: String, newTitle: String): Result<Unit> = runCatching {
        val conversation = loadConversation(conversationId)
            ?: throw IllegalArgumentException("Conversation not found: $conversationId")

        val updated = conversation.copy(
            title = newTitle.trim(),
            isCustomTitle = true,
            updatedAt = System.currentTimeMillis()
        )
        saveConversation(updated)
        Log.d(TAG, "Conversation $conversationId title explicitly updated to: $newTitle")
    }

    /**
     * Delete a conversation, its encrypted record, and all associated media attachments.
     */
    fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        // 1. Physically delete encrypted conversation file
        val file = File(conversationsDir, "$conversationId.json")
        if (file.exists()) file.delete()

        // 2. Physically delete any attachments folder for this conversation
        val convAttachmentsDir = File(attachmentsDir, conversationId)
        if (convAttachmentsDir.exists()) {
            convAttachmentsDir.listFiles()?.forEach { it.delete() }
            convAttachmentsDir.delete()
        }

        // 3. Update encrypted index
        val conversations = loadConversationSummaries().toMutableList()
        conversations.removeAll { it.id == conversationId }
        saveConversationSummaries(conversations)

        Log.d(TAG, "Conversation and attachments securely deleted: $conversationId")
    }

    /**
     * Delete all conversations and attachments.
     */
    fun deleteAllConversations(): Result<Unit> = runCatching {
        conversationsDir.listFiles()?.forEach { it.delete() }
        attachmentsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { it.delete() }
            }
            dir.delete()
        }
        saveConversationSummaries(emptyList())
        Log.d(TAG, "All conversations and attachments deleted")
    }

    /**
     * Get total number of conversations.
     */
    fun getConversationCount(): Int = loadConversationSummaries().size

    private fun loadConversation(conversationId: String): Conversation? {
        val file = File(conversationsDir, "$conversationId.json")
        if (!file.exists()) return null

        return try {
            val encryptedData = securityManager.decryptFile(file).getOrThrow()
            val jsonString = String(encryptedData, Charsets.UTF_8)
            json.decodeFromString(Conversation.serializer(), jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Failed to load conversation: $conversationId", e)
            null
        }
    }

    private fun saveConversation(conversation: Conversation) {
        try {
            val jsonString = json.encodeToString(Conversation.serializer(), conversation)
            securityManager.encryptToFile(
                data = jsonString.toByteArray(Charsets.UTF_8),
                destFile = File(conversationsDir, "${conversation.id}.json")
            ).getOrThrow()

            // Update index
            val summaries = loadConversationSummaries().toMutableList()
            summaries.removeAll { it.id == conversation.id }
            summaries.add(
                ConversationSummary(
                    id = conversation.id,
                    title = conversation.title,
                    isCustomTitle = conversation.isCustomTitle,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    messageCount = conversation.messages.size
                )
            )
            saveConversationSummaries(summaries)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation", e)
        }
    }

    private fun loadConversationSummaries(): List<ConversationSummary> {
        if (!indexFile.exists()) return emptyList()

        return try {
            val encryptedData = securityManager.decryptFile(indexFile).getOrThrow()
            val jsonString = String(encryptedData, Charsets.UTF_8)
            json.decodeFromString(ListSerializer(ConversationSummary.serializer()), jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveConversationSummaries(summaries: List<ConversationSummary>) {
        try {
            val jsonString = json.encodeToString(
                ListSerializer(ConversationSummary.serializer()),
                summaries
            )
            securityManager.encryptToFile(
                data = jsonString.toByteArray(Charsets.UTF_8),
                destFile = indexFile
            ).getOrThrow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation index", e)
        }
    }
}

/**
 * Represents a conversation.
 */
@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val isCustomTitle: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<Message>
)

/**
 * Lightweight summary for the conversation index.
 */
@Serializable
data class ConversationSummary(
    val id: String,
    val title: String,
    val isCustomTitle: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

/**
 * A single message in a conversation.
 */
@Serializable
data class Message(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long
)

/**
 * Message role in the conversation.
 */
@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
