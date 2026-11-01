package com.privateai.dataset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DatasetTurn(
    val role: String,
    val content: String
)

@Serializable
data class FineTuningExample(
    val id: String,
    val category: String,
    val domain: String,
    val task: String,
    @SerialName("user_state")
    val userState: String,
    @SerialName("explanation_level")
    val explanationLevel: Int,
    @SerialName("conversation")
    val conversation: List<DatasetTurn> = emptyList(),
    @SerialName("user_message")
    val userMessage: String,
    @SerialName("expected_reasoning_approach")
    val expectedReasoningApproach: String,
    @SerialName("ideal_response")
    val idealResponse: String,
    @SerialName("adaptation_reason")
    val adaptationReason: String,
    @SerialName("anti_patterns")
    val antiPatterns: List<String> = emptyList()
)

data class ValidationReport(
    val totalExamples: Int,
    val validExamples: Int,
    val invalidExamples: Int,
    val multiTurnCount: Int,
    val singleTurnCount: Int,
    val categoryCounts: Map<String, Int>,
    val domainCounts: Map<String, Int>,
    val userStateCounts: Map<String, Int>,
    val explanationLevelCounts: Map<Int, Int>,
    val errors: List<String>
)
