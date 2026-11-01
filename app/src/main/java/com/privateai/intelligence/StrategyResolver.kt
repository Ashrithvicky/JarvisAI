package com.privateai.intelligence

import com.privateai.persona.AiPersona
import com.privateai.profile.UserProfile

/**
 * Resolves the optimal VIVVA Response Strategy given the user message, conversation history,
 * detected user cognitive state, previous explanation attempts, and adaptive explanation level.
 */
class StrategyResolver(
    private val domainDetector: DomainDetector = DomainDetector(),
    private val taskClassifier: TaskClassifier = TaskClassifier(),
    private val userStateDetector: UserStateDetector = UserStateDetector(),
    private val explanationEngine: AdaptiveExplanationEngine = AdaptiveExplanationEngine()
) {

    fun resolveStrategy(
        userQuery: String,
        conversationContext: String? = null,
        fallbackPersona: AiPersona? = null,
        userProfile: UserProfile? = null,
        recentAttempts: List<ExplanationAttempt> = emptyList()
    ): ResponseStrategy {
        val domain = if (fallbackPersona != null && fallbackPersona != AiPersona.GENERAL) {
            when (fallbackPersona) {
                AiPersona.CODING -> Domain.CODING
                AiPersona.DSA -> Domain.DSA
                AiPersona.DEVOPS -> Domain.DEVOPS
                AiPersona.SYSTEM_DESIGN -> Domain.SYSTEM_DESIGN
                AiPersona.TROUBLESHOOTING -> Domain.TROUBLESHOOTING
                AiPersona.GENERAL -> domainDetector.detectDomain(userQuery, conversationContext)
            }
        } else {
            domainDetector.detectDomain(userQuery, conversationContext)
        }
        val task = taskClassifier.classifyTask(userQuery, domain, conversationContext)
        val userState = userStateDetector.detectUserState(userQuery, conversationContext)

        val adaptiveDecision = explanationEngine.evaluateAdaptiveProgression(
            domain = domain,
            taskType = task,
            userState = userState,
            userQuery = userQuery,
            conversationContext = conversationContext,
            userProfile = userProfile,
            recentAttempts = recentAttempts
        )
        val explanationLevel = adaptiveDecision.level

        val promptBuilder = StringBuilder()
        promptBuilder.append(ResponseStrategy.VIVVA_PHILOSOPHY.trim())

        // Pedagogical depth guidance
        promptBuilder.append("\n\n[ADAPTIVE EXPLANATION LEVEL: ${explanationLevel.name}]")
        promptBuilder.append("\n${explanationLevel.guidanceInstruction}")

        // Pedagogical non-repetition & representation adaptation directive
        if (adaptiveDecision.nonRepetitionDirective != null) {
            promptBuilder.append("\n\n[PEDAGOGICAL ADAPTATION]")
            if (adaptiveDecision.targetedConcept != null) {
                promptBuilder.append("\nTarget Concept: ${adaptiveDecision.targetedConcept}")
            }
            if (adaptiveDecision.failedRepresentationToAvoid != null) {
                promptBuilder.append("\nAvoid Failed Representation: ${adaptiveDecision.failedRepresentationToAvoid.name}")
            }
            promptBuilder.append("\nActive Representation: ${adaptiveDecision.representation.name}")
            promptBuilder.append("\n${adaptiveDecision.nonRepetitionDirective}")
        }

        // 1. Check for adaptive simplified teaching (e.g. user was confused by prior explanation)
        if (task == TaskType.LEARNING_REQUEST || userState == UserState.CONFUSED) {
            promptBuilder.append("\n\n[ADAPTIVE SIMPLIFIED TEACHING DIRECTIVE]")
            promptBuilder.append("\nThe user expressed confusion or requested a simpler explanation:")
            promptBuilder.append("\n1. Acknowledge the exact point of confusion with genuine patience.")
            promptBuilder.append("\n2. Use a clear, intuitive real-world analogy to ground the concept.")
            promptBuilder.append("\n3. Avoid dense academic jargon without immediately defining it.")
            promptBuilder.append("\n4. Break down the concept into small, easy-to-follow steps.")
            promptBuilder.append("\n5. Provide a small, concrete example showing the idea in action.")
            promptBuilder.append("\n6. Check understanding warmly at the end.")
            promptBuilder.append("\n7. NON-REPETITION: Do NOT repeat the previous definition or wording. Shift the mental model and use a fresh perspective.")

            return ResponseStrategy(
                domain = domain,
                taskType = task,
                strategyName = "ADAPTIVE_SIMPLIFIED_TEACHING",
                systemPrompt = promptBuilder.toString(),
                isSimplifiedTeaching = true,
                userState = userState,
                explanationLevel = explanationLevel
            )
        }

        // 2. Domain & Task-specific strategy instructions
        when (domain) {
            Domain.DSA -> {
                promptBuilder.append("\n\n").append(AiPersona.DSA.systemPrompt)
                promptBuilder.append("\n\n[DSA & ALGORITHM REASONING DIRECTIVE]")
                promptBuilder.append("\nFollow this systematic algorithmic teaching methodology:")
                promptBuilder.append("\n1. **Core Intuition First:** Explain the foundational mental model before jumping into code.")
                promptBuilder.append("\n2. **Constraints & Invariants:** Identify boundary conditions and properties that dictate time/space bounds.")
                promptBuilder.append("\n3. **Brute Force vs Optimized:** Contrast naive approaches with the optimal algorithm.")
                promptBuilder.append("\n4. **Time & Space Complexity:** State explicit Big-O notation with clear justification.")
                promptBuilder.append("\n5. **Edge Cases & Dry Run:** Walk through key edge cases and trace execution step-by-step.")
                promptBuilder.append("\n6. **Clean Implementation:** Provide production-ready, readable code when appropriate.")

                return ResponseStrategy(
                    domain = Domain.DSA,
                    taskType = task,
                    strategyName = if (task == TaskType.DSA_EXPLANATION) "DSA_EXPLANATION" else "DSA_PROBLEM_SOLVING",
                    systemPrompt = promptBuilder.toString(),
                    userState = userState,
                    explanationLevel = explanationLevel
                )
            }

            Domain.DEVOPS -> {
                promptBuilder.append("\n\n").append(AiPersona.DEVOPS.systemPrompt)
                promptBuilder.append("\n\n[DEVOPS & CLOUD INFRASTRUCTURE DIRECTIVE]")
                if (task == TaskType.DEVOPS_TROUBLESHOOTING) {
                    promptBuilder.append("\nFollow this SRE troubleshooting methodology:")
                    promptBuilder.append("\n1. **Environment & Symptoms:** Identify what failed in container/host/cluster runtime.")
                    promptBuilder.append("\n2. **Root Cause Analysis:** Pinpoint the underlying defect (e.g. exit codes, entrypoint, permissions, networking, OOM, port conflicts).")
                    promptBuilder.append("\n3. **Actionable Fix:** Give exact commands, configuration diffs, or Dockerfile/YAML corrections.")
                    promptBuilder.append("\n4. **Verification & Prevention:** Provide diagnostics to verify the fix and prevent production recurrence.")
                } else {
                    promptBuilder.append("\nFollow this cloud engineering methodology:")
                    promptBuilder.append("\n1. Prioritize idempotency, security (least privilege), and production safety.")
                    promptBuilder.append("\n2. Explain shell flags, permissions, and configuration options clearly.")
                    promptBuilder.append("\n3. Highlight potential risks or destructive operations before presenting commands.")
                }

                return ResponseStrategy(
                    domain = Domain.DEVOPS,
                    taskType = task,
                    strategyName = if (task == TaskType.DEVOPS_TROUBLESHOOTING) "DEVOPS_TROUBLESHOOTING" else "DEVOPS_CONFIGURATION",
                    systemPrompt = promptBuilder.toString(),
                    userState = userState,
                    explanationLevel = explanationLevel
                )
            }

            Domain.SYSTEM_DESIGN -> {
                promptBuilder.append("\n\n").append(AiPersona.SYSTEM_DESIGN.systemPrompt)
                promptBuilder.append("\n\n[DISTRIBUTED SYSTEM DESIGN DIRECTIVE]")
                promptBuilder.append("\nArchitect scalable, resilient systems with structured depth:")
                promptBuilder.append("\n1. **Requirements & Scope:** Clarify functional workflows and non-functional targets (RPS, latency, consistency, availability).")
                promptBuilder.append("\n2. **High-Level Architecture:** Describe services, API gateways, load balancers, and caches.")
                promptBuilder.append("\n3. **Data Strategy:** SQL vs NoSQL, schema design, sharding, replication, and caching patterns.")
                promptBuilder.append("\n4. **Scaling & Bottlenecks:** Address bottlenecks, failure modes, async queues, and CAP trade-offs.")

                return ResponseStrategy(
                    domain = Domain.SYSTEM_DESIGN,
                    taskType = task,
                    strategyName = "SYSTEM_DESIGN_TEACHING",
                    systemPrompt = promptBuilder.toString(),
                    userState = userState,
                    explanationLevel = explanationLevel
                )
            }

            Domain.TROUBLESHOOTING -> {
                promptBuilder.append("\n\n").append(AiPersona.TROUBLESHOOTING.systemPrompt)
                promptBuilder.append("\n\n[DIAGNOSTIC & ROOT-CAUSE DIRECTIVE]")
                promptBuilder.append("\nFollow a structured root-cause methodology:")
                promptBuilder.append("\n1. **Symptom vs Cause Separation:** Distinguish surface errors from underlying bugs.")
                promptBuilder.append("\n2. **Hypotheses & Isolation:** Formulate clear testable failure modes.")
                promptBuilder.append("\n3. **Targeted Fix:** Deliver minimal, surgical corrections.")
                promptBuilder.append("\n4. **Regression Prevention:** Recommend tests or assertions to prevent recurrence.")

                return ResponseStrategy(
                    domain = Domain.TROUBLESHOOTING,
                    taskType = task,
                    strategyName = "SYSTEMS_TROUBLESHOOTING",
                    systemPrompt = promptBuilder.toString(),
                    userState = userState,
                    explanationLevel = explanationLevel
                )
            }

            Domain.CODING -> {
                promptBuilder.append("\n\n").append(AiPersona.CODING.systemPrompt)
                promptBuilder.append("\n\n[SOFTWARE ENGINEERING & CODE QUALITY DIRECTIVE]")
                if (task == TaskType.DEBUGGING) {
                    promptBuilder.append("\nWhen debugging code:")
                    promptBuilder.append("\n1. Identify the exact root cause of the error or unexpected behavior.")
                    promptBuilder.append("\n2. Explain why the issue happens in plain terms.")
                    promptBuilder.append("\n3. Provide the corrected code with explicit language tags.")
                    promptBuilder.append("\n4. Highlight relevant edge cases and defensive checks.")
                } else {
                    promptBuilder.append("\nWhen writing or reviewing code:")
                    promptBuilder.append("\n1. Write clean, idiomatic, production-ready code with language tags on all code blocks.")
                    promptBuilder.append("\n2. Focus on maintainability, error handling, and performance.")
                    promptBuilder.append("\n3. Explain key design decisions and practical trade-offs.")
                }

                return ResponseStrategy(
                    domain = Domain.CODING,
                    taskType = task,
                    strategyName = if (task == TaskType.DEBUGGING) "CODE_DEBUGGING" else "SOFTWARE_ENGINEERING",
                    systemPrompt = promptBuilder.toString(),
                    userState = userState,
                    explanationLevel = explanationLevel
                )
            }

            Domain.GENERAL -> {
                promptBuilder.append("\n\n").append(AiPersona.GENERAL.systemPrompt)
                promptBuilder.append("\n\n[DIRECT & APPROACHABLE ASSISTANT DIRECTIVE]")
                promptBuilder.append("\nProvide thoughtful, concise, well-structured, and accurate responses. Use markdown formatting with clear headings and bullet points where helpful.")

                return ResponseStrategy(
                    domain = Domain.GENERAL,
                    taskType = task,
                    strategyName = if (task == TaskType.FACTUAL_QUESTION) "DIRECT_FACTUAL_ANSWER" else "GENERAL_CONVERSATION",
                    systemPrompt = promptBuilder.toString(),
                    userState = userState,
                    explanationLevel = explanationLevel
                )
            }
        }
    }
}
