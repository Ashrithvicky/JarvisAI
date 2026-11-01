package com.privateai.intelligence

/**
 * Classifies the specific task intent from the user query and detected domain.
 */
class TaskClassifier {

    companion object {
        private val DEBUG_PATTERNS = Regex("\\b(nullpointerexception|exception|error|stack\\s*trace|crash|failing|bug|fix\\s*this|why\\s*is\\s*it\\s*(not\\s*working|throwing|failing)|cannot\\s*find|undefined\\s*is\\s*not|segfault|debug)\\b", RegexOption.IGNORE_CASE)
        private val CODE_GEN_PATTERNS = Regex("\\b(write\\s*(a|an|the)?\\s*(code|script|program|function|class|method|implementation)|implement|create\\s*(a|an)?\\s*(function|class|component)|code\\s*for|show\\s*me\\s*how\\s*to\\s*code)\\b", RegexOption.IGNORE_CASE)
        private val REVIEW_PATTERNS = Regex("\\b(review\\s*(this|my)?\\s*code|improve\\s*this\\s*code|clean\\s*up\\s*this|refactor\\b|best\\s*practices\\s*for\\s*this\\s*code)\\b", RegexOption.IGNORE_CASE)
        private val EXPLANATION_PATTERNS = Regex("\\b(why\\s*does|how\\s*does|what\\s*is|explain|intuition|how\\s*it\\s*works|meaning\\s*of|concept\\s*of|understanding)\\b", RegexOption.IGNORE_CASE)
        private val COMPARISON_PATTERNS = Regex("\\b(compare|difference\\s*between|vs|versus|which\\s*is\\s*better|pros\\s*and\\s*cons)\\b", RegexOption.IGNORE_CASE)
        private val OPTIMIZATION_PATTERNS = Regex("\\b(optimize|speed\\s*up|reduce\\s*memory|make\\s*it\\s*faster|efficiency|better\\s*time\\s*complexity)\\b", RegexOption.IGNORE_CASE)
        private val LEARNING_CONFUSION_PATTERNS = Regex("\\b(i\\s*(still\\s*)?(don't|dont|do\\s*not)\\s*(understand|get|follow)|what\\s*does\\s*.*\\s*mean|explain\\s*(it\\s*)?simpler|make\\s*it\\s*easier|teach\\s*me|break\\s*it\\s*down|step\\s*by\\s*step|still\\s*confused|analogy)\\b", RegexOption.IGNORE_CASE)
        private val FACTUAL_PATTERNS = Regex("\\b(capital\\s*of|who\\s*(is|was|created|wrote|invented)|when\\s*was|what\\s*year|where\\s*is|population\\s*of|height\\s*of)\\b", RegexOption.IGNORE_CASE)
        private val GREETING_PATTERNS = Regex("^(hi|hello|hey|greetings|good\\s*(morning|afternoon|evening)|how\\s*are\\s*you|who\\s*are\\s*you)\\b", RegexOption.IGNORE_CASE)
    }

    /**
     * Determines task type given user query, domain, and conversation context.
     */
    fun classifyTask(userQuery: String, domain: Domain, conversationContext: String? = null): TaskType {
        val text = userQuery.trim()

        // 1. Check for conversational learning / confusion requests (takes high precedence across domains)
        if (LEARNING_CONFUSION_PATTERNS.containsMatchIn(text)) {
            return TaskType.LEARNING_REQUEST
        }

        // 2. Domain-specific task classifications
        when (domain) {
            Domain.DSA -> {
                if (EXPLANATION_PATTERNS.containsMatchIn(text) && !text.contains(Regex("\\b(solve|implement|code)\\b", RegexOption.IGNORE_CASE))) {
                    return TaskType.DSA_EXPLANATION
                }
                if (COMPARISON_PATTERNS.containsMatchIn(text)) {
                    return TaskType.COMPARISON
                }
                if (OPTIMIZATION_PATTERNS.containsMatchIn(text)) {
                    return TaskType.OPTIMIZATION
                }
                return TaskType.DSA_PROBLEM
            }

            Domain.DEVOPS -> {
                if (DEBUG_PATTERNS.containsMatchIn(text) || text.contains(Regex("\\b(exiting|crashed|failed|error|stopped|exit\\s*code|crashloopbackoff)\\b", RegexOption.IGNORE_CASE))) {
                    return TaskType.DEVOPS_TROUBLESHOOTING
                }
                if (CODE_GEN_PATTERNS.containsMatchIn(text) || text.contains(Regex("\\b(dockerfile|compose|yaml|manifest|terraform|script)\\b", RegexOption.IGNORE_CASE))) {
                    return TaskType.DEVOPS_CONFIGURATION
                }
                if (EXPLANATION_PATTERNS.containsMatchIn(text)) {
                    return TaskType.CONCEPT_EXPLANATION
                }
                return TaskType.DEVOPS_CONFIGURATION
            }

            Domain.SYSTEM_DESIGN -> {
                return TaskType.SYSTEM_DESIGN
            }

            Domain.TROUBLESHOOTING -> {
                return TaskType.DEBUGGING
            }

            Domain.CODING -> {
                if (DEBUG_PATTERNS.containsMatchIn(text)) {
                    return TaskType.DEBUGGING
                }
                if (REVIEW_PATTERNS.containsMatchIn(text)) {
                    return TaskType.CODE_REVIEW
                }
                if (OPTIMIZATION_PATTERNS.containsMatchIn(text)) {
                    return TaskType.OPTIMIZATION
                }
                if (COMPARISON_PATTERNS.containsMatchIn(text)) {
                    return TaskType.COMPARISON
                }
                if (CODE_GEN_PATTERNS.containsMatchIn(text)) {
                    return TaskType.CODE_GENERATION
                }
                if (EXPLANATION_PATTERNS.containsMatchIn(text)) {
                    return TaskType.CONCEPT_EXPLANATION
                }
                return TaskType.CODE_GENERATION
            }

            Domain.GENERAL -> {
                if (GREETING_PATTERNS.containsMatchIn(text)) {
                    return TaskType.GENERAL_CONVERSATION
                }
                if (FACTUAL_PATTERNS.containsMatchIn(text)) {
                    return TaskType.FACTUAL_QUESTION
                }
                if (EXPLANATION_PATTERNS.containsMatchIn(text)) {
                    return TaskType.CONCEPT_EXPLANATION
                }
                if (COMPARISON_PATTERNS.containsMatchIn(text)) {
                    return TaskType.COMPARISON
                }
                return TaskType.FACTUAL_QUESTION
            }
        }
    }
}
