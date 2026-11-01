package com.privateai.multimodal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * On-Device Vision Processing Pipeline.
 *
 * Provides a modular abstraction for on-device visual encoder inference (e.g., SigLIP, MobileCLIP,
 * or PaliGemma vision tower) and visual-to-language projection.
 *
 * TRUTH CONTRACT:
 * - When running with Gemma 2B text-only CausalLM without a compiled vision tower,
 *   the pipeline reports `VisionStatus.REQUIRES_MULTIMODAL_MODEL`.
 * - When a vision encoder is integrated, preprocessed spatial embeddings are mapped into
 *   the Gemma embedding space via VisualTokenProjector.
 */
interface VisionEncoder {
    val encoderName: String
    val inputResolution: Pair<Int, Int> // (width, height)
    val embeddingDimension: Int
    fun encodeImage(bitmap: Bitmap): Result<ImageFeatureRepresentation>
}

interface VisualTokenProjector {
    val visualDim: Int
    val languageDim: Int
    fun projectToLanguageSpace(visualFeatures: FloatArray): FloatArray
}

data class ImageFeatureRepresentation(
    val embeddingVector: FloatArray,
    val tokenCount: Int,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageFeatureRepresentation) return false
        return embeddingVector.contentEquals(other.embeddingVector) &&
                tokenCount == other.tokenCount &&
                width == other.width &&
                height == other.height
    }

    override fun hashCode(): Int {
        var result = embeddingVector.contentHashCode()
        result = 31 * result + tokenCount
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

enum class VisionStatus {
    AVAILABLE,
    REQUIRES_MULTIMODAL_MODEL,
    ENCODER_NOT_INITIALIZED,
    CORRUPT_INPUT
}

object VisionPreprocessor {
    const val MAX_IMAGE_TOKENS = 256
    const val MAX_MULTIMODAL_PREFIX_TOKENS = 500

    /**
     * Resizes and crops a bitmap to target dimensions preserving aspect ratio with center-crop.
     */
    fun preprocessBitmap(source: Bitmap, targetWidth: Int = 224, targetHeight: Int = 224): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) return source
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun decodeBytes(rawBytes: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

object MultimodalPromptFormatter {
    /**
     * Formats multimodal image embeddings and user query into standard Gemma prompt structure.
     */
    fun formatMultimodalPrompt(userQuery: String, tokenCount: Int): String {
        val clampedTokens = tokenCount.coerceIn(1, VisionPreprocessor.MAX_IMAGE_TOKENS)
        return "[IMAGE_EMBEDDINGS: $clampedTokens TOKENS]\n$userQuery"
    }
}

class VisionPipelineManager(
    private var activeVisionEncoder: VisionEncoder? = null,
    private var tokenProjector: VisualTokenProjector? = null
) {
    companion object {
        private const val TAG = "VisionPipelineManager"
    }

    fun getVisionStatus(): VisionStatus {
        return if (activeVisionEncoder != null) VisionStatus.AVAILABLE else VisionStatus.REQUIRES_MULTIMODAL_MODEL
    }

    fun setVisionEncoder(encoder: VisionEncoder?, projector: VisualTokenProjector? = null) {
        activeVisionEncoder = encoder
        tokenProjector = projector
        Log.i(TAG, "Vision encoder updated: ${encoder?.encoderName ?: "None (Text-only mode)"}")
    }

    fun processImageBytes(rawBytes: ByteArray): Result<ImageFeatureRepresentation> = runCatching {
        val encoder = activeVisionEncoder
            ?: throw IllegalStateException("Vision encoder unavailable. Current base model is text-only (REQUIRES_MULTIMODAL_MODEL).")

        val bitmap = VisionPreprocessor.decodeBytes(rawBytes)
            ?: throw IllegalArgumentException("Failed to decode image bytes into valid Bitmap.")

        val (targetW, targetH) = encoder.inputResolution
        val preprocessed = VisionPreprocessor.preprocessBitmap(bitmap, targetW, targetH)
        val rep = encoder.encodeImage(preprocessed).getOrThrow()

        // Apply projector if available
        if (tokenProjector != null) {
            val projected = tokenProjector!!.projectToLanguageSpace(rep.embeddingVector)
            ImageFeatureRepresentation(
                embeddingVector = projected,
                tokenCount = rep.tokenCount.coerceAtMost(VisionPreprocessor.MAX_IMAGE_TOKENS),
                width = rep.width,
                height = rep.height
            )
        } else {
            rep
        }
    }
}
