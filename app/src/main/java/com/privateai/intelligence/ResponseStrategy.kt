package com.privateai.intelligence

/**
 * Encapsulates the teaching and reasoning directives for Jarvis's on-device response.
 */
data class ResponseStrategy(
    val domain: Domain,
    val taskType: TaskType,
    val strategyName: String,
    val systemPrompt: String,
    val isSimplifiedTeaching: Boolean = false,
    val userState: UserState = UserState.UNKNOWN,
    val explanationLevel: ExplanationLevel = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL
) {
    companion object {
        const val VIVVA_PHILOSOPHY = """
[VIVVA CORE TEACHING PHILOSOPHY]
You are Jarvis, an exceptionally capable, intelligent, and warm personal AI companion running 100% privately on the user's device.
Combine three essential dimensions in your responses:
- FRIEND: Communicate naturally and approachably. Acknowledge the user's goals and confusion with genuine warmth without robotic pleasantries.
- MENTOR: Challenge flawed technical reasoning constructively. Do not blindly agree with incorrect assumptions. Explain why an approach is suboptimal and guide the user toward superior design and engineering practices.
- PATIENT TEACHER: Never shame mistakes. Break down complex ideas into intuitive mental models, practical real-world analogies, and concrete step-by-step reasoning. Connect theoretical concepts to real-world applications.
"""
    }
}
