package com.privateai.intelligence

/**
 * Lightweight, deterministic domain detection layer.
 *
 * Employs multi-signal keyword and pattern scoring to classify user queries
 * into appropriate technical or general domains without external ML overhead.
 */
class DomainDetector {

    companion object {
        private val DSA_PATTERNS = listOf(
            Regex("(\\b(binary\\s*search|two\\s*pointer|sliding\\s*window|linked\\s*list|binary\\s*tree|bst|heap|priority\\s*queue|graph|dijkstra|bfs|dfs|dynamic\\s*programming|dp|backtrack|trie|topological|recursion|memoization|time\\s*complexity|space\\s*complexity|big\\s*o|leetcode|hackerrank|codeforces|sorting\\s*algorithm|quick\\s*sort|merge\\s*sort|two\\s*sum|2-sum|hash\\s*table|hashmap|hashset)\\b|\\bo\\([0-9a-z_\\s\\*]+\\))", RegexOption.IGNORE_CASE),
            Regex("\\b(array|string|stack|queue|tree|matrix|node|hashmap|hashset|hash\\s*table|traversal)\\b.*\\b(complexity|algorithm|traverse|invert|rotate|sorted|search|solve|dp)\\b", RegexOption.IGNORE_CASE)
        )

        private val DEVOPS_PATTERNS = listOf(
            Regex("\\b(docker|container|containerized|kubernetes|k8s|pod|deployment|kubectl|helm|ingress|ci/cd|cicd|github\\s*actions|gitlab|jenkins|terraform|ansible|nginx|reverse\\s*proxy|systemd|cron|iptables|bash|powershell|linux|ssh|chown|chmod|stdout|stderr|daemon|sre|devops|aws|gcp|azure|s3|ec2|vpc|load\\s*balancer)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(container|service|pipeline|cluster|node)\\b.*\\b(deploy|failing|exit|crash|yaml|restart|helm|port|log)\\b", RegexOption.IGNORE_CASE)
        )

        private val SYSTEM_DESIGN_PATTERNS = listOf(
            Regex("\\b(system\\s*design|(?:design|architect)\\s*(a|an|the)?\\s*(system|architecture|service|platform|app|pipeline)|distributed\\s*(system|rate\\s*limit(?:er)?)|microservices|high\\s*availability|scalability|scale\\s*to|sharding|partitioning|replication|cap\\s*theorem|read\\s*heavy|write\\s*heavy|rate\\s*limit(?:er)?|circuit\\s*breaker|api\\s*gateway|message\\s*broker|kafka|rabbitmq|cdn|caching\\s*strategy|cache\\s*aside|write\\s*through)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(design|architecture|architect)\\b.*\\b(system|delivery|uber|netflix|whatsapp|url\\s*shortener|chat|rate\\s*limit(?:er)?|twitter|scalable|distributed)\\b", RegexOption.IGNORE_CASE)
        )

        private val TROUBLESHOOTING_PATTERNS = listOf(
            Regex("\\b(nullpointerexception|exception|stacktrace|stack\\s*trace|crash|crashes|crashing|segfault|segmentation\\s*fault|oom|out\\s*of\\s*memory|deadlock|race\\s*condition|memory\\s*leak|failing\\s*with|error\\s*code|error|fatal|why\\s*is\\s*it\\s*throwing|debug\\s*this|fix\\s*this\\s*(error|bug|issue)|why\\s*(is|are|does)\\s*my.*(not\\s*working|exiting|failing|broken))\\b", RegexOption.IGNORE_CASE)
        )

        private val CODING_PATTERNS = listOf(
            Regex("\\b(kotlin|java|python|javascript|typescript|c\\+\\+|golang|rust|swift|c#|sql|react|jetpack\\s*compose|viewmodel|activity|fragment|function|class|method|interface|constructor|async|await|coroutine|promise|thread|lambda|regex|syntax|refactor|unit\\s*test|code\\s*review|implement|write\\s*code|snippet)\\b|\\.(kt|java|py|js|ts|cpp|rs|go|swift|cs)\\b", RegexOption.IGNORE_CASE),
            Regex("```[a-zA-Z]*\\n[\\s\\S]*?```")
        )
    }

    /**
     * Detects the primary domain from the user's message and recent context.
     */
    fun detectDomain(userQuery: String, conversationContext: String? = null): Domain {
        val currentText = userQuery.trim()
        val contextText = conversationContext?.trim() ?: ""

        // 1. Scoring: current user query receives 5x weight; context receives 1x weight
        var dsaScore = 0
        var devopsScore = 0
        var sysDesignScore = 0
        var troubleshootingScore = 0
        var codingScore = 0

        for (pattern in DSA_PATTERNS) {
            dsaScore += pattern.findAll(currentText).count() * 5
            if (contextText.isNotBlank()) dsaScore += pattern.findAll(contextText).count() * 1
        }

        for (pattern in DEVOPS_PATTERNS) {
            devopsScore += pattern.findAll(currentText).count() * 5
            if (contextText.isNotBlank()) devopsScore += pattern.findAll(contextText).count() * 1
        }

        for (pattern in SYSTEM_DESIGN_PATTERNS) {
            sysDesignScore += pattern.findAll(currentText).count() * 5
            if (contextText.isNotBlank()) sysDesignScore += pattern.findAll(contextText).count() * 1
        }

        for (pattern in TROUBLESHOOTING_PATTERNS) {
            troubleshootingScore += pattern.findAll(currentText).count() * 5
            if (contextText.isNotBlank()) troubleshootingScore += pattern.findAll(contextText).count() * 1
        }

        for (pattern in CODING_PATTERNS) {
            codingScore += pattern.findAll(currentText).count() * 4
            if (contextText.isNotBlank()) codingScore += pattern.findAll(contextText).count() * 1
        }

        // 2. Specific disambiguation rules
        // If query asks for architecture/design of a system -> SYSTEM_DESIGN takes precedence
        if (sysDesignScore >= 3) {
            return Domain.SYSTEM_DESIGN
        }

        // If query mentions DevOps specific tools (Docker, K8s, CI/CD) and has a bug/exit -> DEVOPS
        if (devopsScore >= 3 && troubleshootingScore > 0) {
            return Domain.DEVOPS
        }

        // If query is specifically about algorithms / LeetCode / complexity -> DSA
        if (dsaScore >= 3 && dsaScore >= codingScore) {
            return Domain.DSA
        }

        // If DevOps tools mentioned without error -> DEVOPS
        if (devopsScore >= 3) {
            return Domain.DEVOPS
        }

        // If strong error / stacktrace / debugging signal -> TROUBLESHOOTING (or CODING if language specific)
        if (troubleshootingScore >= 3) {
            return if (codingScore > 0) Domain.CODING else Domain.TROUBLESHOOTING
        }

        // General programming query -> CODING
        if (codingScore >= 2) {
            return Domain.CODING
        }

        // Fallback for general questions / facts / casual chat
        return Domain.GENERAL
    }
}
