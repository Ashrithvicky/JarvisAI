package com.privateai.intelligence

import android.content.Context
import com.privateai.context.ContextManager
import com.privateai.context.PromptContextBuilder
import com.privateai.conversation.Conversation
import com.privateai.conversation.ConversationManager
import com.privateai.conversation.ConversationTitleGenerator
import com.privateai.conversation.MessageRole
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import com.privateai.model.ModelManager
import com.privateai.model.ModelType
import com.privateai.multimodal.AttachmentManager
import com.privateai.multimodal.VideoFrameSampler
import com.privateai.multimodal.VideoPipelineManager
import com.privateai.multimodal.VideoStatus
import com.privateai.multimodal.VisionPipelineManager
import com.privateai.multimodal.VisionStatus
import com.privateai.multimodal.WakeWordHelper
import com.privateai.persona.AiPersona
import com.privateai.persona.RelationshipStyle
import com.privateai.profile.UserProfile
import com.privateai.profile.UserProfileManager
import com.privateai.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class VivvaStep18ProductHardeningTest {

    private lateinit var engine: AdaptiveExplanationEngine
    private lateinit var userStateDetector: UserStateDetector
    private lateinit var domainDetector: DomainDetector
    private lateinit var taskClassifier: TaskClassifier
    private lateinit var strategyResolver: StrategyResolver
    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder
    private lateinit var contextManager: ContextManager

    private class TestSecurityManager(context: Context) : SecurityManager(context) {
        override fun encryptToFile(data: ByteArray, destFile: File): Result<Unit> = runCatching {
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(data)
        }

        override fun decryptFile(encryptedFile: File): Result<ByteArray> = runCatching {
            if (!encryptedFile.exists()) throw IllegalArgumentException("File not found: ${encryptedFile.absolutePath}")
            encryptedFile.readBytes()
        }
    }

    @Before
    fun setup() {
        engine = AdaptiveExplanationEngine()
        userStateDetector = UserStateDetector()
        domainDetector = DomainDetector()
        taskClassifier = TaskClassifier()
        strategyResolver = StrategyResolver(
            domainDetector = domainDetector,
            taskClassifier = taskClassifier,
            userStateDetector = userStateDetector,
            explanationEngine = engine
        )
        memoryManager = mock()
        userProfileManager = mock()
        whenever(userProfileManager.getProfile()).thenReturn(UserProfile())
        whenever(userProfileManager.getRelevantBehaviorDirectives(any(), any(), any(), any())).thenReturn(emptyList())
        promptContextBuilder = PromptContextBuilder(memoryManager, userProfileManager, strategyResolver)
        contextManager = ContextManager(memoryManager, userProfileManager, promptContextBuilder)
    }

    // =========================================================================
    // 1. FORENSIC 8-STEP CHAT STORAGE & UNRECOVERABLE DELETION AUDIT
    // =========================================================================

    @Test
    fun `8-Step Forensic Audit verifies create, persist, reload, delete, and permanent unretrievability`() {
        val mockContext: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_step18_8step_audit_${System.currentTimeMillis()}").apply { mkdirs() }
        whenever(mockContext.noBackupFilesDir).thenReturn(tempDir)

        val securityManager = TestSecurityManager(mockContext)
        val convManager = ConversationManager(mockContext, securityManager)
        val attachmentManager = AttachmentManager(mockContext, securityManager)

        // Step 1: Create conversation
        val conv = convManager.createConversation("Confidential Financial Audit", isCustomTitle = true)
        val convId = conv.id
        assertEquals("Confidential Financial Audit", conv.title)

        // Step 2: Persist messages & encrypted attachment
        convManager.addMessage(convId, MessageRole.USER, "Sensitive balance sheet data").getOrThrow()
        convManager.addMessage(convId, MessageRole.ASSISTANT, "Analysis of Q3 revenue and expenses").getOrThrow()
        val attRes = attachmentManager.saveAttachment(convId, "image/png", byteArrayOf(10, 20, 30, 40))
        assertTrue(attRes.isSuccess)
        val attFile = File(attRes.getOrThrow().filePath)

        // Step 3: Reload from disk & verify data integrity
        val reloadedConv = convManager.getConversation(convId)
        assertNotNull(reloadedConv)
        assertEquals(2, reloadedConv!!.messages.size)
        assertTrue(attFile.exists())
        val convFile = File(tempDir, "conversations/$convId.json")
        assertTrue(convFile.exists())

        // Step 4: Delete conversation and attachments
        convManager.deleteConversation(convId).getOrThrow()
        attachmentManager.deleteConversationAttachments(convId).getOrThrow()

        // Step 5: Verify conversation encrypted JSON file absence on disk
        assertFalse("Conversation JSON file must be physically removed", convFile.exists())

        // Step 6: Verify attachment file absence on disk
        assertFalse("Attachment binary file must be physically removed", attFile.exists())

        // Step 7: Verify index absence
        val summaries = convManager.getAllConversationSummaries()
        assertFalse("Deleted conversation must be removed from encrypted index", summaries.any { it.id == convId })

        // Step 8: Verify conversation cannot be reloaded or resurrected
        val reloadedAfterDelete = convManager.getConversation(convId)
        assertNull("Deleted conversation must return null upon retrieval", reloadedAfterDelete)

        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 2. CROSS-TURN DOMAIN ISOLATION (DSA -> CODING -> DEVOPS -> TROUBLESHOOTING)
    // =========================================================================

    @Test
    fun `Domain context isolates cleanly across sequential multi-turn domain transitions`() {
        val conv = Conversation(
            id = "conv-domain-transition",
            title = "Multi-Domain Session",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = emptyList()
        )

        // Turn 1: DSA (Dijkstra algorithm)
        val dsaContext = contextManager.assembleConversationContext(
            conversation = conv,
            currentUserQuery = "How to implement Dijkstra shortest path with priority queue?",
            persona = AiPersona.GENERAL
        )
        assertEquals(Domain.DSA, dsaContext.resolvedStrategy?.domain)
        assertTrue(dsaContext.systemInstruction.contains("10-step methodology"))

        // Turn 2: DevOps (Kubernetes Ingress)
        val devOpsContext = contextManager.assembleConversationContext(
            conversation = conv,
            currentUserQuery = "How to configure an NGINX ingress controller with cert-manager on Kubernetes?",
            persona = AiPersona.GENERAL
        )
        assertEquals(Domain.DEVOPS, devOpsContext.resolvedStrategy?.domain)
        assertTrue(devOpsContext.systemInstruction.contains("Site Reliability Engineer"))
        // Stale DSA methodology must not pollute DevOps prompt
        assertFalse(devOpsContext.systemInstruction.contains("10-step methodology"))

        // Turn 3: Troubleshooting (Android Memory Leak)
        val troubleContext = contextManager.assembleConversationContext(
            conversation = conv,
            currentUserQuery = "My Android app crashes with OutOfMemoryError after rotating the screen 5 times",
            persona = AiPersona.GENERAL
        )
        assertEquals(Domain.TROUBLESHOOTING, troubleContext.resolvedStrategy?.domain)
        assertTrue(troubleContext.systemInstruction.contains("ROOT-CAUSE", ignoreCase = true))
        assertFalse(troubleContext.systemInstruction.contains("Site Reliability Engineer"))
    }

    // =========================================================================
    // 3. MULTIMODAL PIPELINES TRUTHFULNESS (IMAGE & VIDEO)
    // =========================================================================

    @Test
    fun `VisionPipeline truthfully reports REQUIRES_MULTIMODAL_MODEL in text-only mode`() {
        val visionManager = VisionPipelineManager(activeVisionEncoder = null)
        assertEquals(VisionStatus.REQUIRES_MULTIMODAL_MODEL, visionManager.getVisionStatus())

        val result = visionManager.processImageBytes(byteArrayOf(1, 2, 3))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("REQUIRES_MULTIMODAL_MODEL") == true)
    }

    @Test
    fun `VideoPipeline correctly computes uniform sample intervals and reports status`() {
        val visionManager = VisionPipelineManager(activeVisionEncoder = null)
        val videoManager = VideoPipelineManager(visionManager)

        assertEquals(VideoStatus.REQUIRES_MULTIMODAL_MODEL, videoManager.getVideoStatus())

        // Uniform timestamps for 10000ms video with 4 frames: interval = 10000/(4+1) = 2000ms
        val timestamps = VideoFrameSampler.computeSampleTimestamps(durationMs = 10000L, maxFrames = 4)
        assertEquals(4, timestamps.size)
        assertEquals(2000L, timestamps[0])
        assertEquals(4000L, timestamps[1])
        assertEquals(6000L, timestamps[2])
        assertEquals(8000L, timestamps[3])
    }

    // =========================================================================
    // 4. MODEL DISCOVERY, FALLBACK & INTEGRITY
    // =========================================================================

    @Test
    fun `ModelManager correctly resolves fallback when VIVVA fine-tuned model is not yet compiled`() {
        val mockContext: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_step18_model_test_${System.currentTimeMillis()}").apply { mkdirs() }
        whenever(mockContext.noBackupFilesDir).thenReturn(tempDir)

        val modelManager = ModelManager(mockContext)

        // No model present
        assertEquals(ModelType.FALLBACK_MODEL, modelManager.getActiveModelType())
        assertNull(modelManager.getEffectiveModelPath())

        // Base model installed
        val baseModelFile = File(tempDir, "models/gemma-4-E2B-it.litertlm").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(1024))
        }
        assertEquals(ModelType.BASE_MODEL, modelManager.getActiveModelType())
        assertEquals(baseModelFile.absolutePath, modelManager.getEffectiveModelPath())

        tempDir.deleteRecursively()
    }
}
