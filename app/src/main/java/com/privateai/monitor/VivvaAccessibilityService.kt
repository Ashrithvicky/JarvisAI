package com.privateai.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.privateai.control.PhoneAction
import com.privateai.multimodal.TextToSpeechHelper

/**
 * User-enabled accessibility service: warns about risky on-screen content (offline heuristics)
 * and performs voice-requested phone actions. Screen text is scanned in memory and never stored.
 */
class VivvaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: VivvaAccessibilityService? = null
            private set

        private const val SCAN_INTERVAL_MS = 3_000L
        private const val WARN_COOLDOWN_MS = 10 * 60_000L
        private const val MAX_NODES = 400
    }

    private var tts: TextToSpeechHelper? = null
    private var lastScan = 0L
    private val lastWarned = HashMap<String, Long>()

    override fun onServiceConnected() {
        instance = this
        tts = TextToSpeechHelper(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        tts?.shutdown()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        val now = System.currentTimeMillis()
        if (now - lastScan < SCAN_INTERVAL_MS) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text, intArrayOf(0))
        val findings = ThreatScanner.scan(text.toString())
        if (findings.isEmpty()) return

        val key = packageName + findings.first()
        if (now - (lastWarned[key] ?: 0L) < WARN_COOLDOWN_MS) return
        lastWarned[key] = now
        tts?.speak("Careful, boss. This screen looks risky: ${findings.first()}. I wouldn't trust it.")
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, count: IntArray) {
        if (count[0]++ >= MAX_NODES || node.isPassword) return
        node.text?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, out, count) }
        }
    }

    /** Executes [action]; returns false when the target is unavailable. */
    fun perform(action: PhoneAction): Boolean = when (action) {
        PhoneAction.GoBack -> performGlobalAction(GLOBAL_ACTION_BACK)
        PhoneAction.GoHome -> performGlobalAction(GLOBAL_ACTION_HOME)
        PhoneAction.ShowRecents -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        is PhoneAction.OpenApp -> openApp(action.name)
        else -> false
    }

    /** Taps the video result whose description best matches [query]; retries while results load. */
    fun clickBestVideo(query: String, attempt: Int = 0) {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        Handler(Looper.getMainLooper()).postDelayed({
            val candidates = ArrayList<AccessibilityNodeInfo>()
            rootInActiveWindow?.let { collectVideos(it, candidates) }
            val best = candidates.maxByOrNull { node ->
                val d = node.contentDescription.toString().lowercase()
                words.count { it in d }
            }
            var target = best
            while (target != null && !target.isClickable) target = target.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true && attempt < 6) {
                clickBestVideo(query, attempt + 1)
            }
        }, if (attempt == 0) 3_000L else 1_500L)
    }

    private fun collectVideos(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.contentDescription?.contains("play video", ignoreCase = true) == true) out.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectVideos(it, out) }
    }

    /** Taps the first on-screen control labelled [label], retrying while the target screen loads. */
    fun clickLabel(label: String, attempt: Int = 0) {
        Handler(Looper.getMainLooper()).postDelayed({
            val node = rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
                ?.firstOrNull { it.isClickable || it.parent?.isClickable == true }
            val target = node?.takeIf { it.isClickable } ?: node?.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true && attempt < 4) {
                clickLabel(label, attempt + 1)
            }
        }, 1_200L)
    }

    private fun openApp(name: String): Boolean {
        val pm = packageManager
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL
        )
        val wanted = name.lowercase()
        val match = launchers.firstOrNull { it.loadLabel(pm).toString().lowercase() == wanted }
            ?: launchers.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(wanted) }
            ?: return false
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
