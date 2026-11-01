package com.privateai.intelligence

/**
 * Dynamic cognitive and situational state of the user during a conversation.
 *
 * Represents the user's CURRENT turn interaction state, NOT a permanent personality trait:
 * - UserProfile = persistent, long-term learning preferences (e.g. prefers analogies, step-by-step).
 * - UserState = real-time conversational state in the active turn (e.g. confused, debugging, exploring).
 */
enum class UserState {
    NEW_CONCEPT,
    EXPLORING,
    LEARNING,
    CONFUSED,
    FRUSTRATED,
    IMPLEMENTING,
    DEBUGGING,
    VERIFYING,
    DEEP_DIVE,
    COMPARING,
    PLANNING,
    GENERAL_CONVERSATION,
    UNKNOWN
}
