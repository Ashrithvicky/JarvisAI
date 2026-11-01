package com.privateai.monitor

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Offline daily device check. Reports only problems; an empty result means all clear. */
object SecurityAudit {
    private const val MAX_PATCH_AGE_DAYS = 120L

    private val RISKY_PERMISSIONS = setOf(
        "android.permission.SEND_SMS", "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.REQUEST_INSTALL_PACKAGES"
    )

    data class Snapshot(
        val screenLockSet: Boolean,
        val adbEnabled: Boolean,
        val patchAgeDays: Long?,
        val sideloadedRiskyApps: List<String>,
        val otherAccessibilityServices: List<String>
    )

    fun findings(s: Snapshot): List<String> = buildList {
        if (!s.screenLockSet) add("no screen lock is set")
        if (s.adbEnabled) add("USB debugging is on")
        if (s.patchAgeDays != null && s.patchAgeDays > MAX_PATCH_AGE_DAYS) {
            add("your security update is ${s.patchAgeDays} days old")
        }
        if (s.sideloadedRiskyApps.isNotEmpty()) {
            add("apps outside the Play Store with sensitive access: ${s.sideloadedRiskyApps.take(3).joinToString()}")
        }
        if (s.otherAccessibilityServices.isNotEmpty()) {
            add("other accessibility services are enabled: ${s.otherAccessibilityServices.take(3).joinToString()}")
        }
    }

    /** Null when nothing is wrong. */
    fun spokenSummary(findings: List<String>): String? = when {
        findings.isEmpty() -> null
        else -> "Boss, I found ${findings.size} security ${if (findings.size == 1) "issue" else "issues"}: " +
            findings.joinToString(". ") + "."
    }

    fun collect(context: Context): Snapshot {
        val pm = context.packageManager
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val patchAge = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(Build.VERSION.SECURITY_PATCH), LocalDate.now())
        }.getOrNull()

        val risky = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { it.packageName != context.packageName && isUserApp(it.applicationInfo) }
            .filter { pkg -> pkg.requestedPermissions?.any { it in RISKY_PERMISSIONS } == true }
            .filter { installer(pm, it.packageName) == null }
            .map { it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName }

        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').filter { it.isNotBlank() }
            .map { it.substringBefore('/') }
            .filter { it != context.packageName }
            .map { pkg -> runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg) }

        return Snapshot(keyguard.isDeviceSecure, adb, patchAge, risky, enabled)
    }

    private fun isUserApp(info: ApplicationInfo?) = info != null && info.flags and ApplicationInfo.FLAG_SYSTEM == 0

    private fun installer(pm: PackageManager, pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
    }.getOrNull()
}
