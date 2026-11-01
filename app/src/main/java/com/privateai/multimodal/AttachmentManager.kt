package com.privateai.multimodal

import android.content.Context
import com.privateai.security.SecurityManager
import java.io.File
import java.util.UUID

/**
 * Manages local, encrypted multimodal attachments (images/media).
 * All attachments are stored inside noBackupFilesDir/attachments/<conversationId>/
 * and are completely erased upon conversation deletion.
 */
class AttachmentManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val ATTACHMENTS_DIR = "attachments"
    }

    private val baseAttachmentsDir: File
        get() = File(context.noBackupFilesDir, ATTACHMENTS_DIR).also { it.mkdirs() }

    /**
     * Encrypts and stores an attachment for a specific conversation.
     */
    fun saveAttachment(conversationId: String, mimeType: String, rawBytes: ByteArray): Result<AttachmentMeta> = runCatching {
        val convDir = File(baseAttachmentsDir, conversationId).also { it.mkdirs() }
        val attachmentId = UUID.randomUUID().toString()
        val destFile = File(convDir, "$attachmentId.bin")

        securityManager.encryptToFile(rawBytes, destFile).getOrThrow()

        AttachmentMeta(
            id = attachmentId,
            conversationId = conversationId,
            mimeType = mimeType,
            sizeBytes = rawBytes.size.toLong(),
            filePath = destFile.absolutePath,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Decrypts and reads an attachment.
     */
    fun readById(conversationId: String, attachmentId: String): Result<ByteArray> = runCatching {
        val file = File(File(baseAttachmentsDir, conversationId), "$attachmentId.bin")
        require(file.exists()) { "Attachment not found" }
        securityManager.decryptFile(file).getOrThrow()
    }

    fun readAttachment(attachmentMeta: AttachmentMeta): Result<ByteArray> = runCatching {
        val file = File(attachmentMeta.filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Attachment file not found: ${attachmentMeta.filePath}")
        }
        securityManager.decryptFile(file).getOrThrow()
    }

    /**
     * Completely deletes all attachments associated with a conversation.
     */
    fun deleteConversationAttachments(conversationId: String): Result<Unit> = runCatching {
        val convDir = File(baseAttachmentsDir, conversationId)
        if (convDir.exists()) {
            convDir.listFiles()?.forEach { it.delete() }
            convDir.delete()
        }
    }

    /**
     * Deletes all attachments across all conversations.
     */
    fun deleteAllAttachments(): Result<Unit> = runCatching {
        baseAttachmentsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { it.delete() }
            }
            dir.delete()
        }
    }
}

/**
 * Metadata for a local attachment.
 */
data class AttachmentMeta(
    val id: String,
    val conversationId: String,
    val mimeType: String,
    val sizeBytes: Long,
    val filePath: String,
    val timestamp: Long
)
