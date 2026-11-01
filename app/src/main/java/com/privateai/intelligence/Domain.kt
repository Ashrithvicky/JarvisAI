package com.privateai.intelligence

/**
 * High-level knowledge domains detected by VIVVA.
 */
enum class Domain {
    GENERAL,
    CODING,
    DSA,
    DEVOPS,
    SYSTEM_DESIGN,
    TROUBLESHOOTING
}

/**
 * Granular task categories detected by VIVVA.
 */
enum class TaskType {
    FACTUAL_QUESTION,
    CONCEPT_EXPLANATION,
    DEBUGGING,
    CODE_REVIEW,
    CODE_GENERATION,
    DSA_PROBLEM,
    DSA_EXPLANATION,
    SYSTEM_DESIGN,
    DEVOPS_CONFIGURATION,
    DEVOPS_TROUBLESHOOTING,
    COMPARISON,
    OPTIMIZATION,
    LEARNING_REQUEST,
    GENERAL_CONVERSATION
}
