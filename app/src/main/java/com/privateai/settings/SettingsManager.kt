package com.privateai.settings

import android.content.Context
import android.content.SharedPreferences
import com.privateai.persona.AiPersona
import com.privateai.persona.RelationshipStyle
import com.privateai.security.SecurityManager
import kotlinx.serialization.json.Json

/**
 * Manages local user settings, active session persistence, and inference preferences.
 */
class SettingsManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val PREFS_NAME = "private_ai_user_settings"
        private const val KEY_SETTINGS_JSON = "inference_settings"
        private const val KEY_LAST_CONVERSATION_ID = "last_active_conv_id"
        private const val KEY_ACTIVE_PERSONA = "active_ai_persona"
        private const val KEY_ACTIVE_STYLE = "active_relationship_style"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getInferenceSettings(): InferenceSettings {
        val raw = prefs.getString(KEY_SETTINGS_JSON, null) ?: return InferenceSettings()
        return try {
            json.decodeFromString<InferenceSettings>(raw)
        } catch (e: Exception) {
            InferenceSettings()
        }
    }

    fun saveInferenceSettings(settings: InferenceSettings) {
        val raw = json.encodeToString(InferenceSettings.serializer(), settings)
        prefs.edit().putString(KEY_SETTINGS_JSON, raw).apply()
    }

    fun getLastActiveConversationId(): String? {
        return prefs.getString(KEY_LAST_CONVERSATION_ID, null)
    }

    fun setLastActiveConversationId(id: String?) {
        prefs.edit().putString(KEY_LAST_CONVERSATION_ID, id).apply()
    }

    fun getActivePersona(): AiPersona {
        val name = prefs.getString(KEY_ACTIVE_PERSONA, AiPersona.GENERAL.name) ?: AiPersona.GENERAL.name
        return try {
            AiPersona.valueOf(name)
        } catch (e: Exception) {
            AiPersona.GENERAL
        }
    }

    fun setActivePersona(persona: AiPersona) {
        prefs.edit().putString(KEY_ACTIVE_PERSONA, persona.name).apply()
    }

    fun getActiveRelationshipStyle(): RelationshipStyle {
        // Single blended personality; older saved choices are ignored.
        return RelationshipStyle.COMPANION
    }

    fun setActiveRelationshipStyle(style: RelationshipStyle) {
        prefs.edit().putString(KEY_ACTIVE_STYLE, style.name).apply()
    }
}
