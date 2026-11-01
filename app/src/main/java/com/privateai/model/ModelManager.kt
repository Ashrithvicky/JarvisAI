package com.privateai.model

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Handles model file discovery, validation, and management.
 *
 * SECURITY: Models are loaded from local device storage only.
 * No automatic downloads from the Internet.
 * The developer must manually supply model files via ADB or file transfer.
 *
 * Expected model location: /data/data/com.privateai/files/models/
 *
 * Supported formats:
 * - .litertlm (LiteRT-LM format)
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "models"
        private const val SUPPORTED_EXTENSION = ".litertlm"
    }

    private val modelsDir: File
    	get() = File(
        	context.noBackupFilesDir,
        	MODELS_DIR
    	).also { it.mkdirs() }

    /**
     * List all available models in the models directory.
     */
    fun listAvailableModels(): List<ModelInfo> {
        return modelsDir.listFiles()
            ?.filter { it.extension == "litertlm" }
            ?.map { file ->
                ModelInfo(
                    name = file.nameWithoutExtension,
                    fileName = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    /**
     * Get a specific model by name.
     * @param modelName The name (without extension) of the model
     * @return The model file path, or null if not found
     */
    fun getModelPath(modelName: String): String? {
        val file = File(modelsDir, "$modelName$SUPPORTED_EXTENSION")
        return if (file.exists() && file.length() > 0) {
            file.absolutePath
        } else {
            null
        }
    }

    /**
     * Check if a model is available.
     */
    fun isModelAvailable(modelName: String): Boolean {
        return getModelPath(modelName) != null
    }

    /**
     * Get the default base model name (Gemma 4 E2B).
     */
    fun getDefaultModelName(): String = "gemma-4-E2B-it"

    /**
     * Get the default base model path if available.
     */
    fun getDefaultModelPath(): String? = getModelPath(getDefaultModelName())

    /**
     * Get the VIVVA fine-tuned behavioral model name.
     */
    fun getVivvaModelName(): String = "vivva-gemma-2b"

    /**
     * Get the VIVVA fine-tuned behavioral model path if available.
     */
    fun getVivvaModelPath(): String? = getModelPath(getVivvaModelName())

    /**
     * Resolves the effective model path: prefers VIVVA behavioral model,
     * seamlessly falls back to base model if not found.
     */
    fun getEffectiveModelPath(): String? =
        getVivvaModelPath() ?: getDefaultModelPath() ?: listAvailableModels().firstOrNull { it.sizeBytes > 0 }?.path

    /**
     * Returns the active model type currently available on-device.
     */
    fun getActiveModelType(): ModelType {
        return when {
            isModelAvailable(getVivvaModelName()) -> ModelType.VIVVA_MODEL
            isModelAvailable(getDefaultModelName()) -> ModelType.BASE_MODEL
            else -> ModelType.FALLBACK_MODEL
        }
    }

    /**
     * Calculate SHA-256 hash of a model file for integrity verification.
     */
    fun calculateModelHash(modelPath: String): Result<String> = runCatching {
        val file = File(modelPath)
        if (!file.exists()) throw IllegalArgumentException("Model file not found: $modelPath")

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)

        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Copy a model file from external storage to internal storage with atomic replacement.
     * Writes to a temporary file, verifies non-empty size and optional expected checksum,
     * then atomically replaces the target model file.
     */
    fun atomicInstallModel(
        sourceFile: File,
        modelName: String,
        expectedSha256: String? = null
    ): Result<String> = runCatching {
        require(sourceFile.exists() && sourceFile.length() > 0) {
            "Source file does not exist or is empty: ${sourceFile.absolutePath}"
        }

        if (!hasEnoughStorageSpace(sourceFile.length())) {
            throw IllegalStateException("Insufficient storage space to install model $modelName")
        }

        val tempFile = File(modelsDir, "$modelName.tmp")
        sourceFile.copyTo(tempFile, overwrite = true)

        if (expectedSha256 != null) {
            val computedHash = calculateModelHash(tempFile.absolutePath).getOrThrow()
            if (!computedHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                tempFile.delete()
                throw SecurityException("Checksum mismatch during model installation for $modelName")
            }
        }

        val destFile = File(modelsDir, "$modelName$SUPPORTED_EXTENSION")
        if (destFile.exists()) {
            destFile.delete()
        }

        val renamed = tempFile.renameTo(destFile)
        if (!renamed) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }

        destFile.absolutePath
    }

    /**
     * Safely quarantines a corrupt or invalid model file to prevent crash loops.
     */
    fun quarantineCorruptModel(modelName: String, reason: String): Result<String> = runCatching {
        val modelFile = File(modelsDir, "$modelName$SUPPORTED_EXTENSION")
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file $modelName does not exist to quarantine")
        }

        val quarantineDir = File(context.noBackupFilesDir, "quarantine").also { it.mkdirs() }
        val quarantinedFile = File(quarantineDir, "${modelName}_${System.currentTimeMillis()}.corrupt")
        modelFile.renameTo(quarantinedFile)
        quarantinedFile.absolutePath
    }

    /**
     * Verifies that the internal storage has enough usable space for the model file.
     */
    fun hasEnoughStorageSpace(requiredBytes: Long): Boolean {
        val usable = context.noBackupFilesDir.usableSpace
        return usable > (requiredBytes + 50 * 1024 * 1024L) // 50MB safety margin
    }

    /**
     * Copy a model file from external storage to internal storage.
     * This is used for ADB-pushed models.
     */
    fun copyModelFromExternal(externalPath: String, modelName: String): Result<String> =
        runCatching {
            val source = File(externalPath)
            if (!source.exists()) {
                throw IllegalArgumentException("Source file not found: $externalPath")
            }

            atomicInstallModel(source, modelName).getOrThrow()
        }

    /**
     * Delete a model file.
     */
    fun deleteModel(modelName: String): Result<Unit> = runCatching {
        val file = File(modelsDir, "$modelName$SUPPORTED_EXTENSION")
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Model deleted: $modelName")
        }
    }

    /**
     * Delete ALL local AI data (models, conversations, memories).
     * This is a user-controlled data deletion feature.
     */
    fun deleteAllData(): Result<Unit> = runCatching {
        // Delete models
        modelsDir.listFiles()?.forEach { it.delete() }

        // Delete conversations
        File(context.noBackupFilesDir, "conversations").listFiles()?.forEach { it.delete() }

        // Delete memories
        File(context.noBackupFilesDir, "memories").listFiles()?.forEach { it.delete() }

        // Delete security keys
        File(context.noBackupFilesDir, "security").listFiles()?.forEach { it.delete() }

        // Delete shared prefs
        context.getSharedPreferences("private_ai_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()

        Log.d(TAG, "All local AI data deleted")
    }

    /**
     * Get total size of all model files.
     */
    fun getTotalModelSize(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}

/**
 * Information about a model file.
 */
data class ModelInfo(
    val name: String,
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
) {
    val sizeMB: Double
        get() = sizeBytes / (1024.0 * 1024.0)

    val sizeGB: Double
        get() = sizeBytes / (1024.0 * 1024.0 * 1024.0)
}

/**
 * Distinguishes base models, fine-tuned VIVVA behavioral models, and fallbacks.
 */
enum class ModelType {
    BASE_MODEL,
    VIVVA_MODEL,
    FALLBACK_MODEL
}

/**
 * Runtime lifecycle status of a model file.
 */
enum class ModelStatus {
    AVAILABLE,
    ACTIVE,
    CORRUPT,
    QUARANTINED,
    NOT_FOUND
}

/**
 * Model metadata and integrity manifest.
 */
data class ModelManifest(
    val name: String,
    val version: String,
    val sha256: String,
    val sizeBytes: Long,
    val type: ModelType
)
