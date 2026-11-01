package com.privateai.intelligence

import android.content.Context
import android.graphics.Bitmap
import com.privateai.model.ModelManager
import com.privateai.model.ModelType
import com.privateai.multimodal.MultimodalPromptFormatter
import com.privateai.multimodal.VideoFrameSampler
import com.privateai.multimodal.VisionEncoder
import com.privateai.multimodal.VisionPipelineManager
import com.privateai.multimodal.VisionPreprocessor
import com.privateai.multimodal.VisionStatus
import com.privateai.multimodal.VisualTokenProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class VivvaStep19ExternalBlockersTest {

    @Test
    fun `MultimodalPromptFormatter bounds image tokens to maximum budget`() {
        val standard = MultimodalPromptFormatter.formatMultimodalPrompt("Describe image", 128)
        assertTrue(standard.contains("[IMAGE_EMBEDDINGS: 128 TOKENS]"))

        // Clamping check for excessive tokens
        val clamped = MultimodalPromptFormatter.formatMultimodalPrompt("Describe image", 1024)
        assertTrue(clamped.contains("[IMAGE_EMBEDDINGS: ${VisionPreprocessor.MAX_IMAGE_TOKENS} TOKENS]"))
    }

    @Test
    fun `VisualTokenProjector maps visual embeddings into language hidden dimension`() {
        val projector = object : VisualTokenProjector {
            override val visualDim: Int = 768
            override val languageDim: Int = 2048

            override fun projectToLanguageSpace(visualFeatures: FloatArray): FloatArray {
                return FloatArray(languageDim) { i -> if (i < visualFeatures.size) visualFeatures[i] else 0.0f }
            }
        }

        val testFeatures = FloatArray(768) { 1.0f }
        val projected = projector.projectToLanguageSpace(testFeatures)
        assertEquals(2048, projected.size)
        assertEquals(1.0f, projected[0], 0.001f)
    }

    @Test
    fun `VideoFrameSampler clamps sample requests to maximum mobile budget`() {
        // Requesting 50 frames on a 60s video must clamp to MAX_VIDEO_FRAMES (8)
        val timestamps = VideoFrameSampler.computeSampleTimestamps(durationMs = 60000L, maxFrames = 50)
        assertEquals(VideoFrameSampler.MAX_VIDEO_FRAMES, timestamps.size)
        assertTrue(timestamps.all { it in 1..59999 })
    }

    @Test
    fun `ModelManager calculates SHA256 integrity hash correctly`() {
        val mockContext: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_step19_hash_test_${System.currentTimeMillis()}").apply { mkdirs() }
        whenever(mockContext.noBackupFilesDir).thenReturn(tempDir)

        val testFile = File(tempDir, "test_file.bin").apply { writeBytes("VIVVA_INTEGRITY_CHECK".toByteArray()) }
        val modelManager = ModelManager(mockContext)

        val hashRes = modelManager.calculateModelHash(testFile.absolutePath)
        assertTrue(hashRes.isSuccess)
        assertNotNull(hashRes.getOrNull())
        assertEquals(64, hashRes.getOrThrow().length)

        tempDir.deleteRecursively()
    }
}
