package com.dualclone.app.virtual

import android.content.Context
import com.dualclone.app.data.CloneRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File

/**
 * DualClone's bridge to the BlackBox virtualization engine (vendored
 * `:Bcore` module, Apache-2.0 — see blackbox/UPSTREAM.md).
 *
 * Rules enforced here:
 *  - Other apps are ONLY ever installed/launched through the engine, inside
 *    its virtual environment. Nothing in DualClone starts a real app.
 *  - If the engine refuses an install or a launch, the caller surfaces the
 *    real reason. There is NO fallback to the real app — ever.
 *  - Each clone record owns exactly one engine virtual user; that user's
 *    virtual data directory is the clone's own.
 */
class CloneEngine(private val context: Context) {

    /** Install the clone's package into the virtual environment for its user. */
    suspend fun install(clone: CloneRecord): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            EngineLog.log(TAG, "installPackageAsUser(${clone.packageName}, user=${clone.engineUserId}) — engineInitialized=${EngineLog.engineInitialized}")
            val result = BlackBoxCore.get().installPackageAsUser(clone.packageName, clone.engineUserId)
            if (result.success) {
                EngineLog.log(TAG, "Installed ${clone.packageName} for virtual user ${clone.engineUserId}")
                logSplitInventory(clone)
                Result.success(Unit)
            } else {
                EngineLog.error(TAG, "Engine REFUSED install of ${clone.packageName} for user ${clone.engineUserId}: ${result.msg}")
                Result.failure(IllegalStateException(result.msg ?: "The virtual engine refused to install ${clone.packageName}."))
            }
        } catch (e: Exception) {
            EngineLog.error(TAG, "Install threw for ${clone.packageName} (user ${clone.engineUserId})", e)
            Result.failure(e)
        }
    }

    private fun logSplitInventory(clone: CloneRecord) {
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(clone.packageName, 0)
            val splits = ai.splitSourceDirs?.toList() ?: emptyList()
            if (splits.isEmpty()) {
                EngineLog.log(TAG, "Split check for ${clone.packageName}: single APK (no splits). Base: ${ai.sourceDir}")
            } else {
                val names = pm.getPackageInfo(clone.packageName, 0)?.splitNames?.toList() ?: splits.map { File(it).name }
                EngineLog.log(TAG, "Split check for ${clone.packageName}: base + ${splits.size} splits passed to the guest: ${names.joinToString()}")
                splits.forEach { EngineLog.log(TAG, "  split file: $it (${File(it).length()} bytes, exists=${File(it).exists()})") }
            }
        } catch (e: Exception) {
            EngineLog.error(TAG, "Split check failed for ${clone.packageName}", e)
        }
    }

    suspend fun isInstalled(clone: CloneRecord): Boolean = withContext(Dispatchers.IO) {
        try { BlackBoxCore.get().isInstalled(clone.packageName, clone.engineUserId) } catch (e: Exception) { false }
    }

    /** Install if needed; success only if the package ends up installed for this clone's user. */
    suspend fun ensureInstalled(clone: CloneRecord): Result<Unit> {
        if (isInstalled(clone)) return Result.success(Unit)
        return install(clone)
    }

    /** Launch the clone inside the virtual environment. True = engine accepted. Call on the main thread. */
    fun launch(clone: CloneRecord): Boolean = launchDetailed(clone) == null

    /**
     * Like [launch], but returns the real error detail on failure (null on
     * success) so the UI can show it verbatim — never a silent failure.
     */
    fun launchDetailed(clone: CloneRecord): String? = try {
        EngineLog.log(TAG, "launchApk(${clone.packageName}, user=${clone.engineUserId}) — engineInitialized=${EngineLog.engineInitialized}")
        val splits = guestSplits(clone)
        EngineLog.log(TAG, "Guest ApplicationInfo for ${clone.packageName}: splits served by engine = ${splits?.size ?: "unknown"} ${splits ?: ""}")
        EngineLog.log(TAG, "Google services inside virtual env for user ${clone.engineUserId}: ${isGmsInstalled(clone)}")
        val ok = BlackBoxCore.get().launchApk(clone.packageName, clone.engineUserId)
        if (ok) {
            EngineLog.log(TAG, "launchApk returned true for ${clone.packageName} (user ${clone.engineUserId})")
            null
        } else {
            val installed = try { BlackBoxCore.get().isInstalled(clone.packageName, clone.engineUserId) } catch (e: Exception) { "unknown (${e.message})" }
            val detail = "The engine's launchApk returned false for ${clone.packageName} (virtual user ${clone.engineUserId}). " +
                "Engine initialized: ${EngineLog.engineInitialized} (${EngineLog.engineInitDetail}) Installed for this user: $installed. " +
                "Full detail: Settings > Diagnostics > Engine log."
            EngineLog.error(TAG, "launchApk returned FALSE for ${clone.packageName} (user ${clone.engineUserId})")
            detail
        }
    } catch (e: Exception) {
        EngineLog.error(TAG, "Launch threw for ${clone.packageName} (user ${clone.engineUserId})", e)
        "${e.javaClass.simpleName}: ${e.message}"
    }

    /** The split APK paths the ENGINE actually serves to this clone's guest (live answer, may be null). */
    fun guestSplits(clone: CloneRecord): List<String>? = try {
        BlackBoxCore.getBPackageManager().getPackageInfo(clone.packageName, 0, clone.engineUserId)
            ?.applicationInfo?.splitSourceDirs?.toList()
    } catch (e: Exception) { null }

    /** Is Google Play services installed inside the virtual environment for this clone's user? */
    fun isGmsInstalled(clone: CloneRecord): Boolean? = try {
        BlackBoxCore.get().isInstallGms(clone.engineUserId)
    } catch (e: Exception) { null }

    /**
     * Install the device's real Google service packages (the user's own
     * installed copies of GMS/GSF/Play Store/Google app) into the virtual
     * environment for this clone's user, using the engine's GmsCore. This
     * hosts the user's real packages; it does NOT bypass any app's security
     * checks — apps requiring Play Integrity attestation will still refuse.
     */
    suspend fun installGms(clone: CloneRecord): Result<String> = withContext(Dispatchers.IO) {
        try {
            val supported = try { BlackBoxCore.get().isSupportGms() } catch (e: Exception) { false }
            EngineLog.log(TAG, "installGms(user=${clone.engineUserId}) — device supports GMS hosting: $supported; already installed: ${isGmsInstalled(clone)}")
            if (isGmsInstalled(clone) == true) return@withContext Result.success("Google services are already installed for this clone's virtual user.")
            val result = BlackBoxCore.get().installGms(clone.engineUserId)
            EngineLog.log(TAG, "installGms(user=${clone.engineUserId}) result: success=${result.success} msg=${result.msg}; installed now: ${isGmsInstalled(clone)}")
            if (result.success) Result.success("Google services installed into the virtual environment for this clone's user. Re-open the clone and try again.")
            else Result.failure(IllegalStateException(result.msg ?: "The engine could not install Google services."))
        } catch (e: Exception) {
            EngineLog.error(TAG, "installGms threw for user ${clone.engineUserId}", e)
            Result.failure(e)
        }
    }

    suspend fun uninstall(clone: CloneRecord) = withContext(Dispatchers.IO) {
        try {
            BlackBoxCore.get().uninstallPackageAsUser(clone.packageName, clone.engineUserId)
            EngineLog.log(TAG, "uninstallPackageAsUser(${clone.packageName}, user=${clone.engineUserId}) done")
        } catch (e: Exception) { EngineLog.error(TAG, "Uninstall failed for ${clone.packageName}", e) }
    }

    suspend fun clearData(clone: CloneRecord) = withContext(Dispatchers.IO) {
        try {
            BlackBoxCore.get().clearPackage(clone.packageName, clone.engineUserId)
            EngineLog.log(TAG, "clearPackage(${clone.packageName}, user=${clone.engineUserId}) done")
        } catch (e: Exception) { EngineLog.error(TAG, "Clear data failed for ${clone.packageName}", e) }
    }

    /** The clone's real virtual data directory, from the engine's own path rules. */
    fun dataDir(clone: CloneRecord): File = try {
        BEnvironment.getDataDir(clone.packageName, clone.engineUserId)
    } catch (e: Exception) {
        File(context.cacheDir.parentFile, "blackbox/data/user/${clone.engineUserId}/${clone.packageName}")
    }

    fun dataSizeBytes(clone: CloneRecord): Long {
        val dir = dataDir(clone)
        return if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    /** Stub processes (:pN) currently alive for this app, read live. */
    fun stubProcesses(): List<String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return (am.runningAppProcesses ?: emptyList())
            .filter { it.processName.startsWith(context.packageName + ":p") }
            .map { "${it.processName} (pid ${it.pid})" }
    }

    companion object {
        private const val TAG = "CloneEngine"
        const val ENGINE_NAME = "BlackBox Bcore (NewBlackbox fork, Apache-2.0)"
    }
}
