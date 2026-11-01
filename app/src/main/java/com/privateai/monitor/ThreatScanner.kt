package com.privateai.monitor

/** Offline heuristics that flag risky on-screen content. Never stores or logs the scanned text. */
object ThreatScanner {

    private val URL = Regex("(?i)\\b(?:https?://)?([a-z0-9@._-]+\\.[a-z]{2,}|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:/\\S*)?")
    private val RISKY_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "top", "click", "zip", "mov")
    private val PHISHING_PHRASES = listOf(
        "verify your account", "your account has been suspended", "enter your otp",
        "claim your prize", "update your payment", "your device is infected",
        "confirm your password", "you have won"
    )

    /** Returns short human-readable reasons; empty when nothing suspicious is found. */
    fun scan(text: String): List<String> {
        val findings = linkedSetOf<String>()
        val lower = text.lowercase()
        PHISHING_PHRASES.firstOrNull { lower.contains(it) }?.let { findings += "phishing-style wording" }
        for (match in URL.findAll(text)) {
            val host = match.groupValues[1].lowercase()
            val full = match.value
            when {
                host.contains('@') -> findings += "link hides its real destination"
                host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) && full.contains("/") -> findings += "link points to a raw IP address"
                host.contains("xn--") -> findings += "link uses look-alike characters"
                host.substringAfterLast('.') in RISKY_TLDS && full.contains("/") -> findings += "link uses a high-risk domain"
            }
        }
        return findings.toList()
    }
}
