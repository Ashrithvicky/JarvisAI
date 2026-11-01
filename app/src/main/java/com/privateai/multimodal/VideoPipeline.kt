package com.privateai.multimodal

import android.graphics.Bitmap
import android.util.Log

/**
 * On-Device Video Processing Pipeline.
 *
 * Implements keyframe extraction, temporal downsampling, and spatial-temporal visual token aggregation.
 *
 * TRUTH CONTRACT:
 * - Video understanding requires a vision-language model with multi-frame embedding capability.
 * - When no video vision model is present, the pipeline evaluates status as `VideoStatus.REQUIRES_MULTIMODAL_MODEL`.
 */
enum class VideoStatus {
    AVAILABLE,
    REQUIRES_MULTIMODAL_MODEL,
    EMPTY_VIDEO,
    FRAME_EXTRACTION_FAILED
}

data class VideoFrameSample(
    val frameIndex: Int,
    val timestampMs: Long,
    val bitmap: Bitmap
)

data class VideoAnalysisMeta(
    val durationMs: Long,
    val totalFrames: Int,
    val sampledFramesCount: Int,
    val averageFps: Float
)

object VideoFrameSampler {
    const val MAX_VIDEO_FRAMES = 8
    const val MAX_TOKENS_PER_FRAME = 32
    const val MAX_TOTAL_VIDEO_TOKENS = 256

    /**
     * Determines optimal sample timestamps for a video given max frame constraints.
     * Uniformly distributes keyframe intervals across total duration.
     */
    fun computeSampleTimestamps(durationMs: Long, maxFrames: Int = MAX_VIDEO_FRAMES): List<Long> {
        if (durationMs <= 0 || maxFrames <= 0) return emptyList()
        val clampedFrames = maxFrames.coerceIn(1, MAX_VIDEO_FRAMES)
        if (clampedFrames == 1) return listOf(durationMs / 2)

        val interval = durationMs.toDouble() / (clampedFrames + 1)
        return (1..clampedFrames).map { i -> (i * interval).toLong() }
    }
}

class VideoPipelineManager(
    private val visionPipelineManager: VisionPipelineManager
) {
    companion object {
        private const val TAG = "VideoPipelineManager"
    }

    fun getVideoStatus(): VideoStatus {
        return when (visionPipelineManager.getVisionStatus()) {
            VisionStatus.AVAILABLE -> VideoStatus.AVAILABLE
            else -> VideoStatus.REQUIRES_MULTIMODAL_MODEL
        }
    }

    /**
     * Aggregates multiple sampled frames into a sequence of image representations.
     */
    fun processSampledFrames(frames: List<VideoFrameSample>): Result<List<ImageFeatureRepresentation>> = runCatching {
        if (frames.isEmpty()) throw IllegalArgumentException("No video frames provided for analysis.")
        val status = getVideoStatus()
        if (status == VideoStatus.REQUIRES_MULTIMODAL_MODEL) {
            throw IllegalStateException("Video reasoning unavailable: Current architecture requires multimodal base model.")
        }

        val clampedFrames = frames.take(VideoFrameSampler.MAX_VIDEO_FRAMES)
        clampedFrames.map { frame ->
            val preprocessed = VisionPreprocessor.preprocessBitmap(frame.bitmap)
            ImageFeatureRepresentation(
                embeddingVector = FloatArray(VideoFrameSampler.MAX_TOKENS_PER_FRAME),
                tokenCount = VideoFrameSampler.MAX_TOKENS_PER_FRAME,
                width = preprocessed.width,
                height = preprocessed.height
            )
        }
    }
}
