package com.privateai.intelligence

import android.content.Context
import com.privateai.benchmark.DeviceBenchmarkProfiler
import com.privateai.context.ContextManager
import com.privateai.context.PromptContextBuilder
import com.privateai.conversation.Conversation
import com.privateai.conversation.ConversationManager
import com.privateai.conversation.ConversationTitleGenerator
import com.privateai.conversation.MessageRole
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import com.privateai.multimodal.AttachmentManager
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

class VivvaStep17ApplicationIntegrationTest {

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
    // 1. COMPLETE REAL CHAT PIPELINE EXECUTION TRACE
    // =========================================================================

    @Test
    fun `Full chat pipeline correctly assembles domain, memory, style, and budget`() {
        val conv = Conversation(
            id = "conv-step17-test",
            title = "Algorithm Design",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = emptyList()
        )

        whenever(memoryManager.getAllMemories()).thenReturn(
            listOf(
                Memory(
                    id = "mem-1",
                    content = "User is building VIVVA on-device AI system in Kotlin",
                    type = MemoryType.PROJECT,
                    tags = listOf("vivva", "kotlin")
                )
            )
        )

        val assembled = contextManager.assembleConversationContext(
            conversation = conv,
            currentUserQuery = "How to optimize memory in VIVVA Kotlin project?",
            persona = AiPersona.CODING,
            relationshipStyle = RelationshipStyle.MENTOR
        )

        // Verifies Domain/Task resolution
        assertNotNull(assembled.resolvedStrategy)
        // Verifies Style injection
        assertTrue(assembled.systemInstruction.contains("[STYLE: MENTOR]"))
        // Verifies Memory injection
        assertTrue(assembled.injectedMemories.isNotEmpty())
        assertTrue(assembled.systemInstruction.contains("VIVVA on-device AI system"))
        // Verifies Premise verification
        assertTrue(assembled.systemInstruction.contains("[CRITICAL EVALUATION & PREMISE VERIFICATION]"))
        // Verifies Token budgeting headroom
        assertTrue(assembled.estimatedTotalTokens < 2048)
    }

    // =========================================================================
    // 2. PERSONALITY / RELATIONSHIP STYLES & STRICT OVERRIDE PRECEDENCE
    // =========================================================================

    @Test
    fun `Explicit user concise instructions override all relationship styles`() {
        val styles = listOf(RelationshipStyle.FRIEND, RelationshipStyle.MENTOR, RelationshipStyle.SUPPORTIVE_GUIDE)
        for (style in styles) {
            val context = promptContextBuilder.buildSystemInstruction(
                userQuery = "Give me two lines only for quicksort implementation",
                relationshipStyle = style
            )
            assertFalse(context.systemInstruction.contains(style.styleDirective))
        }
    }

    @Test
    fun `Friend mode style directive contains safety boundaries and does not claim human biology`() {
        val context = promptContextBuilder.buildSystemInstruction(
            userQuery = "Let's work together today",
            relationshipStyle = RelationshipStyle.FRIEND
        )
        assertTrue(context.systemInstruction.contains("[STYLE: FRIEND]"))
        assertTrue(context.systemInstruction.contains("Do not claim to be a biological human"))
    }

    // =========================================================================
    // 3. CONVERSATION AUTO-TITLE & STORAGE FORENSIC INTEGRATION
    // =========================================================================

    @Test
    fun `ConversationTitleGenerator handles technical and everyday queries safely`() {
        assertEquals("Dijkstra Algorithm", ConversationTitleGenerator.generateTitle("Can you please explain Dijkstra's algorithm?"))
        assertEquals("Docker Compose Setup", ConversationTitleGenerator.generateTitle("Please write a docker compose setup"))
        assertEquals("Binary Search Tree", ConversationTitleGenerator.generateTitle("What is a Binary Search Tree?"))
        assertEquals("New Chat", ConversationTitleGenerator.generateTitle(""))
        assertEquals("New Chat", ConversationTitleGenerator.generateTitle("???"))
    }

    @Test
    fun `Complete conversation deletion scrubs file, index, and attachment folder`() {
        val mockContext: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_step17_scrub_test_${System.currentTimeMillis()}").apply { mkdirs() }
        whenever(mockContext.noBackupFilesDir).thenReturn(tempDir)

        val securityManager = TestSecurityManager(mockContext)
        val convManager = ConversationManager(mockContext, securityManager)
        val attachmentManager = AttachmentManager(mockContext, securityManager)

        val conv = convManager.createConversation("Alpha Session", isCustomTitle = true)
        convManager.addMessage(conv.id, MessageRole.USER, "Sensitive project data").getOrThrow()

        // Attach an encrypted file
        val attRes = attachmentManager.saveAttachment(conv.id, "image/png", byteArrayOf(10, 20, 30))
        assertTrue(attRes.isSuccess)
        val attFile = File(attRes.getOrThrow().filePath)
        assertTrue(attFile.exists())

        // Delete conversation
        convManager.deleteConversation(conv.id).getOrThrow()
        attachmentManager.deleteConversationAttachments(conv.id).getOrThrow()

        // Verify total physical wipe
        assertFalse(File(tempDir, "conversations/${conv.id}.json").exists())
        assertFalse(attFile.exists())
        assertNull(convManager.getConversation(conv.id))

        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 4. DEVICE BENCHMARK PROFILER
    // =========================================================================

    @Test
    fun `DeviceBenchmarkProfiler computes metrics accurately`() {
        DeviceBenchmarkProfiler.startGenerationTimer()
        DeviceBenchmarkProfiler.recordFirstToken()
        for (i in 1..20) {
            DeviceBenchmarkProfiler.recordToken()
        }
        val metrics = DeviceBenchmarkProfiler.finishGeneration(promptTokens = 50)
        assertEquals(20, metrics.generatedTokens)
        assertEquals(50, metrics.promptTokens)
        assertTrue(metrics.totalGenerationMs >= 0)
        assertTrue(metrics.peakRamMb >= 0)
    }

    // =========================================================================
    // 5. WAKE WORD FOREGROUND DETECTION
    // =========================================================================

    @Test
    fun `WakeWordHelper detects Hey Jarvis and triggers callback`() {
        var triggered = false
        val helper = WakeWordHelper { triggered = true }
        assertTrue(helper.processTranscribedText("Hey Jarvis show me my tasks"))
        assertTrue(triggered)
    }
}
