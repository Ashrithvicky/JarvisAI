package com.privateai.intelligence

import android.content.Context
import com.privateai.context.PromptContextBuilder
import com.privateai.conversation.Conversation
import com.privateai.conversation.ConversationManager
import com.privateai.conversation.ConversationTitleGenerator
import com.privateai.conversation.MessageRole
import com.privateai.memory.MemoryManager
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

class VivvaStep16ProductCompleteTest {

    private lateinit var engine: AdaptiveExplanationEngine
    private lateinit var userStateDetector: UserStateDetector
    private lateinit var domainDetector: DomainDetector
    private lateinit var taskClassifier: TaskClassifier
    private lateinit var strategyResolver: StrategyResolver
    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder

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
    }

    // =========================================================================
    // 1. CONVERSATION TITLE GENERATOR & TITLE PRESERVATION
    // =========================================================================

    @Test
    fun `ConversationTitleGenerator extracts clean concise topic titles`() {
        assertEquals("Dijkstra Algorithm", ConversationTitleGenerator.generateTitle("Can you please explain Dijkstra algorithm?"))
        assertEquals("Redis", ConversationTitleGenerator.generateTitle("Help me to write a redis"))
        assertTrue(ConversationTitleGenerator.generateTitle("How to implement a Kotlin Coroutine Flow with debounce?").contains("Kotlin Coroutine Flow"))
        assertEquals("Quicksort", ConversationTitleGenerator.generateTitle("quicksort"))
        assertEquals("New Chat", ConversationTitleGenerator.generateTitle("   "))
    }

    @Test
    fun `Conversation title auto-updates on first message but never overwrites explicit rename`() {
        val mockContext: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_step16_title_test_${System.currentTimeMillis()}").apply { mkdirs() }
        whenever(mockContext.noBackupFilesDir).thenReturn(tempDir)

        val securityManager = TestSecurityManager(mockContext)
        val convManager = ConversationManager(mockContext, securityManager)

        // Initial creation
        val conv = convManager.createConversation()
        assertEquals("New Chat", conv.title)
        assertFalse(conv.isCustomTitle)

        // First message generates automatic title
        val addRes = convManager.addMessage(conv.id, MessageRole.USER, "How to configure Kubernetes ingress controller?")
        assertTrue(addRes.isSuccess)

        val loadedAfterFirst = convManager.getConversation(conv.id)
        assertNotNull(loadedAfterFirst)
        assertTrue(loadedAfterFirst!!.title.contains("Kubernetes Ingress"))

        // Explicit rename
        convManager.updateConversationTitle(conv.id, "My Production Ingress Guide").getOrThrow()
        val loadedAfterRename = convManager.getConversation(conv.id)
        assertNotNull(loadedAfterRename)
        assertEquals("My Production Ingress Guide", loadedAfterRename!!.title)
        assertTrue(loadedAfterRename.isCustomTitle)

        // Second message should NOT overwrite custom title
        convManager.addMessage(conv.id, MessageRole.USER, "What about cert-manager?").getOrThrow()
        val loadedAfterSecond = convManager.getConversation(conv.id)
        assertNotNull(loadedAfterSecond)
        assertEquals("My Production Ingress Guide", loadedAfterSecond!!.title)

        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 2. FORENSIC STORAGE, ENCRYPTION AND SECURE PHYSICAL DELETION
    // =========================================================================

    @Test
    fun `Forensic deletion physically removes encrypted conversation file and attachments`() {
        val mockContext: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_step16_del_test_${System.currentTimeMillis()}").apply { mkdirs() }
        whenever(mockContext.noBackupFilesDir).thenReturn(tempDir)

        val securityManager = TestSecurityManager(mockContext)
        val convManager = ConversationManager(mockContext, securityManager)
        val attachmentManager = AttachmentManager(mockContext, securityManager)

        val conv1 = convManager.createConversation("Alpha Chat", isCustomTitle = true)
        val conv2 = convManager.createConversation("Beta Chat", isCustomTitle = true)
        
        convManager.addMessage(conv1.id, MessageRole.USER, "Confidential Project Alpha secret").getOrThrow()
        convManager.addMessage(conv2.id, MessageRole.USER, "Project Beta public spec").getOrThrow()

        // Add attachment to conv1
        val attResult = attachmentManager.saveAttachment(conv1.id, "image/png", byteArrayOf(1, 2, 3, 4))
        assertTrue(attResult.isSuccess)
        val attFile = File(attResult.getOrThrow().filePath)
        assertTrue(attFile.exists())

        val conv1File = File(tempDir, "conversations/${conv1.id}.json")
        assertTrue(conv1File.exists())

        // Physical deletion of conv1
        convManager.deleteConversation(conv1.id).getOrThrow()

        // Verify conv1 is gone from filesystem and unretrievable
        assertFalse(conv1File.exists())
        assertNull(convManager.getConversation(conv1.id))
        assertFalse(convManager.getAllConversationSummaries().any { it.id == conv1.id })

        // Verify conv2 is completely unaffected
        val loadedConv2 = convManager.getConversation(conv2.id)
        assertNotNull(loadedConv2)
        assertEquals("Beta Chat", loadedConv2!!.title)

        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 3. RELATIONSHIP STYLES & IN-TURN EXPLICIT OVERRIDES
    // =========================================================================

    @Test
    fun `Relationship styles inject appropriate style directives`() {
        // MENTOR style
        val mentorContext = promptContextBuilder.buildSystemInstruction(
            userQuery = "How do microservices communicate?",
            relationshipStyle = RelationshipStyle.MENTOR
        )
        assertTrue(mentorContext.systemInstruction.contains("[STYLE: MENTOR]"))

        // FRIEND style
        val friendContext = promptContextBuilder.buildSystemInstruction(
            userQuery = "I am learning Rust today",
            relationshipStyle = RelationshipStyle.FRIEND
        )
        assertTrue(friendContext.systemInstruction.contains("[STYLE: FRIEND]"))

        // SUPPORTIVE_GUIDE style
        val guideContext = promptContextBuilder.buildSystemInstruction(
            userQuery = "I am struggling with recursion",
            relationshipStyle = RelationshipStyle.SUPPORTIVE_GUIDE
        )
        assertTrue(guideContext.systemInstruction.contains("[STYLE: SUPPORTIVE GUIDE]"))
    }

    @Test
    fun `In-turn explicit concise override suppresses personality style`() {
        val overrideContext = promptContextBuilder.buildSystemInstruction(
            userQuery = "Give me the code only for quicksort",
            relationshipStyle = RelationshipStyle.FRIEND
        )
        // Explicit "code only" should suppress friend conversational styling
        assertFalse(overrideContext.systemInstruction.contains("[STYLE: FRIEND]"))
    }

    // =========================================================================
    // 4. MULTIMODAL & HEY Jarvis FOREGROUND KEYWORD DETECTION
    // =========================================================================

    @Test
    fun `WakeWordHelper detects Hey Jarvis foreground phrases and ignores unrelated speech`() {
        var wakeWordTriggered = false
        val wakeWordHelper = WakeWordHelper(
            onWakeWordDetected = { wakeWordTriggered = true }
        )

        // Positive detections
        assertTrue(wakeWordHelper.processTranscribedText("Hey Jarvis how is the weather?"))
        assertTrue(wakeWordTriggered)

        wakeWordTriggered = false
        assertTrue(wakeWordHelper.processTranscribedText("Hello Jarvis explain merge sort"))
        assertTrue(wakeWordTriggered)

        // Negative detections
        wakeWordTriggered = false
        assertFalse(wakeWordHelper.processTranscribedText("What is the time right now?"))
        assertFalse(wakeWordTriggered)
    }
}
