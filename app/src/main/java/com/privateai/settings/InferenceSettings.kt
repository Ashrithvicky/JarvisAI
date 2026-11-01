package com.privateai.settings

import kotlinx.serialization.Serializable

/**
 * User-configurable inference settings.
 */
@Serializable
data class InferenceSettings(
    val backendPreference: BackendPreference = BackendPreference.AUTO,
    val temperature: Double = 0.7,
    val topK: Int = 40,
    val topP: Double = 0.9,
    val maxTokens: Int = 2048,
    val seed: Long = 0L
)

/**
 * Backend preference mode.
 */
@Serializable
enum class BackendPreference {
    /** Attempt GPU first; if initialization fails, gracefully fall back to CPU */
    AUTO,
    /** Force GPU backend */
    GPU,
    /** Force CPU backend */
    CPU
}
