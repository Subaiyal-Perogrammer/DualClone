package com.dualclone.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An installed app the user could clone, read live from the system. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val apkSizeBytes: Long,
    val splitNames: List<String>,
    val isSystem: Boolean
) {
    val isSplit: Boolean get() = splitNames.isNotEmpty()
}

/** Loads the device's launchable apps for the Add App screen. */
object InstalledApps {

    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchables = try {
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
        val apps = try { pm.getInstalledApplications(PackageManager.GET_META_DATA) } catch (e: Exception) { emptyList() }
        apps.asSequence()
            .filter { it.packageName != context.packageName }
            .filter { it.packageName in launchables }
            .map { ai ->
                val splits = try {
                    pm.getPackageInfo(ai.packageName, 0)?.splitNames?.toList() ?: emptyList()
                } catch (e: Exception) { emptyList() }
                InstalledApp(
                    packageName = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    apkSizeBytes = ai.sourceDir?.let { File(it).length() } ?: 0L,
                    splitNames = splits,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            .toList()
    }

    fun icon(context: Context, packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) { null }

    /** Apps that must never be cloned: payment/banking (their own security
     *  rules refuse virtual environments) and the Google service packages
     *  themselves (they are hosted for clones, not cloned). */
    fun isBlocked(packageName: String): String? {
        val p = packageName.lowercase()
        return when {
            p.contains("bank") || p.contains("pay") && p.contains("wallet") || p.contains(".wallet") ->
                "Not supported — banking/payment apps refuse virtual environments by their own security design."
            p == "com.google.android.gms" || p == "com.google.android.gsf" || p == "com.android.vending" ->
                "Not supported as a clone target — Google services are hosted inside other clones instead (clone detail > Install Google Services)."
            else -> null
        }
    }
}
