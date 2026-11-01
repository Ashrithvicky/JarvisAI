package com.privateai.memory

import android.content.Context
import android.util.Log
import com.privateai.security.SecurityManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Manages long-term factual memory for the AI companion.
 *
 * STRICT SEPARATION OF RESPONSIBILITIES:
 * - MemoryManager stores WHAT VIVVA knows about the user (facts, projects, tech stack, goals).
 * - UserProfileManager stores HOW the user learns (preferences, learning dimensions, pedagogical style).
 *
 * SECURITY:
 * - Memories are encrypted at rest using Android Keystore-backed AES-GCM keys.
 * - Memories are stored in noBackupFilesDir and excluded from Android backups.
 */
class MemoryManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    companion object {
        private const val TAG = "MemoryManager"
        private const val MEMORIES_DIR = "memories"
        private const val MEMORY_FILE = "memories.json"
    }

    private val memoriesDir: File
        get() = File(
            context.noBackupFilesDir,
            MEMORIES_DIR
        ).also { it.mkdirs() }

    private val memoryFile: File
        get() = File(memoriesDir, MEMORY_FILE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * Initialize the memory manager.
     */
    fun initialize(): Result<Unit> = runCatching {
        memoriesDir.mkdirs()
        Log.d(TAG, "Memory manager initialized")
    }

    /**
     * Add a new factual memory.
     */
    fun addMemory(
        content: String,
        type: MemoryType = MemoryType.USER_FACT,
        tags: List<String> = emptyList()
    ): Result<Memory> = runCatching {

        require(content.isNotBlank()) {
            "Memory content cannot be empty"
        }

        val now = System.currentTimeMillis()

        val memory = Memory(
            id = UUID.randomUUID().toString(),
            content = content.trim(),
            type = type,
            tags = tags.map { it.trim() }.filter { it.isNotBlank() },
            createdAt = now,
            updatedAt = now
        )

        val memories = loadMemories().toMutableList()
        memories.add(memory)

        saveMemories(memories)

        Log.d(TAG, "Memory added: ${memory.id} (${memory.type})")

        memory
    }

    /**
     * Get all memories.
     */
    fun getAllMemories(): List<Memory> {
        return loadMemories()
    }

    /**
     * Search memories by simple text matching across content and tags.
     */
    fun searchMemories(query: String): List<Memory> {
        if (query.isBlank()) {
            return emptyList()
        }

        val lowerQuery = query.lowercase().trim()

        return loadMemories().filter { memory ->
            memory.content.lowercase().contains(lowerQuery) ||
                    memory.tags.any {
                        it.lowercase().contains(lowerQuery)
                    }
        }
    }

    /**
     * Update an existing memory.
     */
    fun updateMemory(memory: Memory): Result<Unit> = runCatching {
        val memories = loadMemories().toMutableList()

        val index = memories.indexOfFirst {
            it.id == memory.id
        }

        if (index < 0) {
            throw IllegalArgumentException(
                "Memory not found: ${memory.id}"
            )
        }

        memories[index] = memory.copy(
            updatedAt = System.currentTimeMillis()
        )

        saveMemories(memories)

        Log.d(TAG, "Memory updated: ${memory.id}")
    }

    /**
     * Delete a specific memory.
     */
    fun deleteMemory(memoryId: String): Result<Unit> = runCatching {
        val memories = loadMemories().toMutableList()

        val removed = memories.removeAll {
            it.id == memoryId
        }

        if (!removed) {
            throw IllegalArgumentException(
                "Memory not found: $memoryId"
            )
        }

        saveMemories(memories)

        Log.d(TAG, "Memory deleted: $memoryId")
    }

    /**
     * Delete all memories.
     */
    fun deleteAllMemories(): Result<Unit> = runCatching {
        saveMemories(emptyList())
        Log.d(TAG, "All memories deleted")
    }

    /**
     * Get total number of stored memories.
     */
    fun getMemoryCount(): Int {
        return loadMemories().size
    }

    /**
     * Load and decrypt memories from disk.
     */
    private fun loadMemories(): List<Memory> {
        if (!memoryFile.exists()) {
            return emptyList()
        }

        return try {
            val encryptedData =
                securityManager
                    .decryptFile(memoryFile)
                    .getOrThrow()

            val jsonString =
                String(
                    encryptedData,
                    Charsets.UTF_8
                )

            json.decodeFromString<List<Memory>>(
                jsonString
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to load memories",
                e
            )
            emptyList()
        }
    }

    /**
     * Serialize, encrypt and persist memories.
     */
    private fun saveMemories(
        memories: List<Memory>
    ) {
        try {
            val jsonString =
                json.encodeToString(
                    ListSerializer(Memory.serializer()),
                    memories
                )

            securityManager.encryptToFile(
                data = jsonString.toByteArray(Charsets.UTF_8),
                destFile = memoryFile
            ).getOrThrow()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to save memories",
                e
            )
            throw e
        }
    }
}

/**
 * Memory types representing WHAT VIVVA knows about the user.
 */
@Serializable
enum class MemoryType {
    /** General user facts (e.g. background, organization, experience level). */
    USER_FACT,

    /** Active or past software projects (e.g. VIVVA, Project Alpha). */
    PROJECT,

    /** Technology stack, frameworks, environments (e.g. Kotlin, Docker, LiteRT-LM). */
    TECH_STACK,

    /** Long-lived goals and milestones. */
    GOAL,

    // Backward compatibility aliases for existing code and persisted databases
    FACT,
    PREFERENCE,
    CONTEXT,
    SUMMARY
}

/**
 * Helper to determine if a memory type is factual and eligible for memory prompt injection.
 */
val MemoryType.isFactualContext: Boolean
    get() = this in listOf(
        MemoryType.USER_FACT,
        MemoryType.PROJECT,
        MemoryType.TECH_STACK,
        MemoryType.GOAL,
        MemoryType.FACT,
        MemoryType.CONTEXT,
        MemoryType.SUMMARY
    )

/**
 * Represents a single factual memory entry.
 */
@Serializable
data class Memory(
    val id: String,
    val content: String,
    val type: MemoryType = MemoryType.USER_FACT,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)