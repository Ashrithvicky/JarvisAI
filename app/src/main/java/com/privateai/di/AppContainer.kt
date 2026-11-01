package com.privateai.di

import android.content.Context
import com.privateai.conversation.ConversationManager
import com.privateai.llm.LLMEngine
import com.privateai.llm.LLMEngineFactory
import com.privateai.llm.LiteRTLMEngine
import com.privateai.llm.LiteRTLMEngineFactory
import com.privateai.memory.MemoryManager
import com.privateai.model.ModelManager
import com.privateai.security.SecurityManager

/**
 * Manual dependency injection container.
 *
 * This avoids adding Hilt/Dagger as a dependency, keeping the project lean.
 * For testing, replace instances with mocks.
 *
 * Architecture:
 * - SecurityManager: Provides encryption for all sensitive data
 * - ModelManager: Manages model files on disk
 * - ConversationManager: Manages encrypted conversation history
 * - MemoryManager: Manages encrypted long-term memory
 * - LLMEngineFactory: Creates LLM inference engines
 */
class AppContainer(private val context: Context) {

    val securityManager: SecurityManager by lazy {
        SecurityManager(context).also { it.initialize() }
    }

    val modelManager: ModelManager by lazy {
        ModelManager(context)
    }

    val conversationManager: ConversationManager by lazy {
        ConversationManager(context, securityManager).also { it.initialize() }
    }

    val memoryManager: MemoryManager by lazy {
        MemoryManager(context, securityManager).also { it.initialize() }
    }

    val llmEngineFactory: LLMEngineFactory by lazy {
        LiteRTLMEngineFactory(context)
    }

    val settingsManager: com.privateai.settings.SettingsManager by lazy {
        com.privateai.settings.SettingsManager(context, securityManager)
    }

    val userProfileManager: com.privateai.profile.UserProfileManager by lazy {
        com.privateai.profile.UserProfileManager(context, securityManager)
    }

    val promptContextBuilder: com.privateai.context.PromptContextBuilder by lazy {
        com.privateai.context.PromptContextBuilder(memoryManager, userProfileManager)
    }

    val contextManager: com.privateai.context.ContextManager by lazy {
        com.privateai.context.ContextManager(memoryManager, userProfileManager, promptContextBuilder)
    }

    val behaviorDetector: com.privateai.learning.BehaviorDetector by lazy {
        com.privateai.learning.BehaviorDetector()
    }

    val behaviorAccumulator: com.privateai.learning.BehaviorAccumulator by lazy {
        com.privateai.learning.BehaviorAccumulator(userProfileManager)
    }

    /**
     * Delete all user data. User-controlled data deletion.
     */
    fun deleteAllUserData() {
        modelManager.deleteAllData()
        conversationManager.deleteAllConversations()
        memoryManager.deleteAllMemories()
        userProfileManager.clearProfile()
        securityManager.deleteAll()
    }
}
