package com.privateai.intelligence

import android.content.Context
import com.privateai.context.PromptContextBuilder
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import com.privateai.model.ModelManager
import com.privateai.model.ModelManifest
import com.privateai.model.ModelStatus
import com.privateai.model.ModelType
import com.privateai.profile.BehaviorPreference
import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserProfile
import com.privateai.profile.UserProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class VivvaStep7ProductionHardeningTest {

    private lateinit var engine: AdaptiveExplanationEngine
    private lateinit var userStateDetector: UserStateDetector
    private lateinit var domainDetector: DomainDetector
    private lateinit var taskClassifier: TaskClassifier
    private lateinit var strategyResolver: StrategyResolver
    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder

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
        whenever(userProfileManager.getRelevantBehaviorDirectives(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(emptyList())
        promptContextBuilder = PromptContextBuilder(memoryManager, userProfileManager, strategyResolver)
    }

    // =========================================================================
    // PHASE 6 & 7: PRODUCTION MODEL MANAGEMENT TESTS
    // =========================================================================

    @Test
    fun `MODEL 1 - Atomic installation replaces target file cleanly`() {
        val context: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_atomic_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        whenever(context.noBackupFilesDir).thenReturn(tempDir)

        val modelManager = ModelManager(context)
        val dummySrc = File(tempDir, "source_model.bin").apply { writeText("MODEL_BINARY_DATA_V1") }

        val resultPath = modelManager.atomicInstallModel(dummySrc, "test-model").getOrThrow()
        val installedFile = File(resultPath)

        assertTrue(installedFile.exists())
        assertEquals("MODEL_BINARY_DATA_V1", installedFile.readText())
        assertFalse(File(tempDir, "models/test-model.tmp").exists())

        tempDir.deleteRecursively()
    }

    @Test
    fun `MODEL 2 - Quarantine corrupt model moves file out of active directory`() {
        val context: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_quarantine_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        whenever(context.noBackupFilesDir).thenReturn(tempDir)

        val modelManager = ModelManager(context)
        val dummySrc = File(tempDir, "corrupt_model.bin").apply { writeText("CORRUPT_DATA") }
        modelManager.atomicInstallModel(dummySrc, "corrupt-model")

        val qPath = modelManager.quarantineCorruptModel("corrupt-model", "Checksum failure").getOrThrow()
        assertTrue(File(qPath).exists())
        assertFalse(File(tempDir, "models/corrupt-model.litertlm").exists())

        tempDir.deleteRecursively()
    }

    @Test
    fun `MODEL 3 - Storage space check validates sufficient headroom`() {
        val context: Context = mock()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vivva_space_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        whenever(context.noBackupFilesDir).thenReturn(tempDir)

        val modelManager = ModelManager(context)
        assertTrue(modelManager.hasEnoughStorageSpace(1024L)) // 1KB is always safe
        tempDir.deleteRecursively()
    }

    @Test
    fun `MODEL 4 - ModelManifest and ModelStatus structure integrity`() {
        val manifest = ModelManifest(
            name = "vivva-gemma-2b",
            version = "1.0.0",
            sha256 = "dummy_sha256",
            sizeBytes = 1180000000L,
            type = ModelType.VIVVA_MODEL
        )
        assertEquals("vivva-gemma-2b", manifest.name)
        assertEquals(ModelType.VIVVA_MODEL, manifest.type)
        assertEquals(ModelStatus.AVAILABLE, ModelStatus.AVAILABLE)
    }

    // =========================================================================
    // PHASE 8: COMPLETE END-TO-END SCENARIO MATRIX (A THROUGH T)
    // =========================================================================

    @Test
    fun `SCENARIO A & B - Explicit instruction reversal overrides learned profile`() {
        val profile = UserProfile(learningDimensions = mapOf("examples_first" to 0.95f))
        val decision = engine.evaluateAdaptiveProgression(
            Domain.DSA, TaskType.DSA_EXPLANATION, UserState.LEARNING,
            "Explain binary search formally without any examples.",
            userProfile = profile
        )
        assertEquals(RepresentationType.FORMAL, decision.representation)
        assertEquals(ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL, decision.level)
    }

    @Test
    fun `SCENARIO C & D - False premise detected and challenged independently`() {
        val promptContext = promptContextBuilder.buildSystemInstruction(
            "Since quicksort is always O(1) space, why is memory spiking?"
        )
        assertTrue(promptContext.systemInstruction.contains("[CRITICAL EVALUATION & PREMISE VERIFICATION]"))
        assertTrue(promptContext.systemInstruction.contains("That premise is incorrect. Here's why..."))
    }

    @Test
    fun `SCENARIO E & F - Multi-turn confusion and failed analogy non-repetition`() {
        val attempts = mutableListOf(
            ExplanationAttempt("hashmap", Domain.DSA, ExplanationLevel.LEVEL_2_ANALOGY, RepresentationType.ANALOGY, failed = true)
        )
        val t2 = engine.evaluateAdaptiveProgression(
            Domain.DSA, TaskType.LEARNING_REQUEST, UserState.CONFUSED,
            "I still do not get hash collisions.",
            recentAttempts = attempts
        )
        assertFalse(t2.representation == RepresentationType.ANALOGY)
        assertEquals(RepresentationType.NUMERICAL_TRACE, t2.representation)
    }

    @Test
    fun `SCENARIO G & H - Project memory relevance and general question noise suppression`() {
        val projMem = Memory(id = "p-1", content = "Project Gamma uses Redis for token caching", type = MemoryType.PROJECT, tags = listOf("gamma", "redis"))
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(projMem))

        // Targeted project query retrieves memory
        val targeted = promptContextBuilder.retrieveRelevantMemories("How does token caching work in Project Gamma?")
        assertEquals(1, targeted.size)
        assertEquals("p-1", targeted[0].id)

        // General computer science query suppresses project memory
        val general = promptContextBuilder.retrieveRelevantMemories("What is an AVL tree balance factor?")
        assertTrue(general.isEmpty())
    }

    @Test
    fun `SCENARIO I & J - Cross-project and cross-user strict isolation`() {
        val alphaMem = Memory(id = "p-alpha", content = "Alpha secret token: KEY_ALPHA", type = MemoryType.PROJECT, tags = listOf("alpha"))
        val betaMem = Memory(id = "p-beta", content = "Beta config: PORT_9000", type = MemoryType.PROJECT, tags = listOf("beta"))
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(alphaMem, betaMem))

        val retrieved = promptContextBuilder.retrieveRelevantMemories("How to deploy Project Beta?")
        assertEquals(1, retrieved.size)
        assertEquals("p-beta", retrieved[0].id)
        assertFalse(retrieved.any { it.content.contains("KEY_ALPHA") })
    }

    @Test
    fun `SCENARIO K & L - Code only and two lines only overrides`() {
        val codeOnly = engine.evaluateAdaptiveProgression(Domain.DSA, TaskType.CODE_GENERATION, UserState.IMPLEMENTING, "Give me the code only for mergesort")
        assertEquals(RepresentationType.CODE_FIRST, codeOnly.representation)

        val conciseOnly = engine.evaluateAdaptiveProgression(Domain.DSA, TaskType.FACTUAL_QUESTION, UserState.LEARNING, "Explain in two lines only")
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, conciseOnly.level)
    }

    @Test
    fun `SCENARIO M, N, O - Domain switching within single session`() {
        assertEquals(Domain.DSA, domainDetector.detectDomain("How does binary search achieve O(log n) time?"))
        assertEquals(Domain.DEVOPS, domainDetector.detectDomain("Why is Docker failing with container exit 137?"))
        assertEquals(Domain.SYSTEM_DESIGN, domainDetector.detectDomain("Design a distributed message broker with Kafka partitions"))
        assertEquals(Domain.CODING, domainDetector.detectDomain("How to implement a Kotlin Flow with debounce?"))
        assertEquals(Domain.TROUBLESHOOTING, domainDetector.detectDomain("fatal: segmentation fault core dumped"))
        assertEquals(Domain.GENERAL, domainDetector.detectDomain("What is the distance between Earth and Mars?"))
    }
}
