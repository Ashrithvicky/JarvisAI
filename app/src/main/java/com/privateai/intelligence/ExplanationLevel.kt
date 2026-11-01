package com.privateai.intelligence

/**
 * Discrete pedagogical explanation depth levels for adaptive teaching.
 */
enum class ExplanationLevel(
    val levelNumber: Int,
    val description: String,
    val guidanceInstruction: String
) {
    LEVEL_1_DIRECT(
        levelNumber = 1,
        description = "Simple direct explanation",
        guidanceInstruction = "Give a concise, direct, and crisp answer without unnecessary preamble or over-explanation."
    ),

    LEVEL_2_ANALOGY(
        levelNumber = 2,
        description = "Intuitive real-world analogy",
        guidanceInstruction = "Ground the concept in an intuitive, relatable real-world analogy before introducing formal notation or mechanics."
    ),

    LEVEL_3_MICRO_STEP(
        levelNumber = 3,
        description = "Tiny concrete step-by-step trace",
        guidanceInstruction = "Break the concept down into small sequential micro-steps. Trace a single minimal concrete example step-by-step with state transitions."
    ),

    LEVEL_4_FORMAL_TECHNICAL(
        levelNumber = 4,
        description = "Comprehensive technical explanation",
        guidanceInstruction = "Provide a structured technical explanation covering core mechanics, invariants, algorithmic steps, and theoretical foundations."
    ),

    LEVEL_5_ADVANCED(
        levelNumber = 5,
        description = "In-depth internals, edge cases, and trade-offs",
        guidanceInstruction = "Deliver deep architectural analysis covering systems internals, performance trade-offs, boundary edge cases, and production reliability."
    )
}
