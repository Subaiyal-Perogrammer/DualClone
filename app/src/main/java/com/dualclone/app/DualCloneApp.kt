package com.dualclone.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.dualclone.app.data.CloneDatabase
import com.dualclone.app.virtual.CloneEngine
import com.dualclone.app.virtual.EngineLog
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/**
 * DualClone application. Owns the BlackBox engine startup — which follows
 * the engine fork's own demo app call for call (closeCodeInit,
 * onBeforeMainApplicationAttach, doAttachBaseContext with a
 * ClientConfiguration naming this app as host, onAfterMainApplicationAttach,
 * then doCreate). It runs in EVERY process of this app (the engine's stub
 * processes and its service process included).
 *
 * Since 1.0.2 the host UI process runs that sequence on a background thread
 * (safe-start): a hung engine step on the main thread froze the splash
 * screen on 1.0.1, so Home must draw regardless. Stub/service processes
 * keep the synchronous sequence, since a guest's hooks must be in place
 * before its components run.
 *
 * Every step is failure-tolerant: if the engine cannot start, DualClone
 * still opens, and clone launches report the engine's real failure instead
 * of ever falling back to opening a real app.
 */
class DualCloneApp : Application() {

    lateinit var engine: CloneEngine
        private set

    /** True when this process is the host UI process (name == package). */
    private var mainProcess = false

    /** Set when the engine init sequence has finished (or thrown) in this process. */
    @Volatile
    private var engineInitDone = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Log file from the very first line: a hang before onCreate used to
        // leave no file trace at all.
        EngineLog.initFile(base)
        mainProcess = isMainProcess(base)
        EngineLog.log(TAG, "Engine startup in process ${android.os.Process.myPid()} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.BRAND} ${Build.MODEL}, ABI ${Build.SUPPORTED_ABIS.firstOrNull()}, mainProcess=$mainProcess")
        if (mainProcess) {
            // SAFE-START (1.0.2): on this phone 1.0.1 froze on the splash
            // screen — engine init hung on the main thread, so the app never
            // drew its first frame. The host UI process now runs engine init
            // on a background thread, so Home always opens; clone actions
            // report the engine's real state until init finishes. A watchdog
            // names the hung step in the log after 20s.
            Thread({
                runAttachSteps(base)
                runDoCreate()
            }, "dualclone-engine-init").start()
            Thread({
                try { Thread.sleep(20_000) } catch (e: InterruptedException) { /* done early */ }
                if (!engineInitDone) {
                    EngineLog.error(TAG, "ENGINE INIT TIMEOUT: engine startup still running after 20s in this process — the step logged just above this line is the hang.")
                }
            }, "dualclone-engine-watchdog").start()
        } else {
            // Stub/service processes keep the original synchronous sequence:
            // a guest's hooks must be in place before its components run.
            runAttachSteps(base)
        }
    }

    /** The engine's attach sequence (demo-app order). Never throws. */
    private fun runAttachSteps(base: Context) {
        try { BlackBoxCore.get().closeCodeInit(); EngineLog.log(TAG, "closeCodeInit OK") }
        catch (e: Exception) { EngineLog.error(TAG, "BlackBox closeCodeInit failed", e) }
        try { BlackBoxCore.get().onBeforeMainApplicationAttach(this, base); EngineLog.log(TAG, "onBeforeMainApplicationAttach OK") }
        catch (e: Exception) { EngineLog.error(TAG, "BlackBox onBeforeMainApplicationAttach failed", e) }
        try {
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String = base.packageName
            })
            EngineLog.log(TAG, "doAttachBaseContext OK")
        } catch (e: Exception) { EngineLog.error(TAG, "BlackBox doAttachBaseContext failed", e) }
        try { BlackBoxCore.get().onAfterMainApplicationAttach(this, base); EngineLog.log(TAG, "onAfterMainApplicationAttach OK") }
        catch (e: Exception) { EngineLog.error(TAG, "BlackBox onAfterMainApplicationAttach failed", e) }
    }

    /** doCreate plus the init flags. Never throws; always marks init done. */
    private fun runDoCreate() {
        try {
            BlackBoxCore.get().doCreate()
            EngineLog.engineInitialized = true
            EngineLog.engineInitDetail = "doCreate() completed without throwing in this process."
            EngineLog.log(TAG, "doCreate OK — engine initialized")
        } catch (e: Exception) {
            EngineLog.engineInitialized = false
            EngineLog.engineInitDetail = "doCreate() threw: ${e.javaClass.simpleName}: ${e.message}"
            EngineLog.error(TAG, "BlackBox doCreate failed", e)
        } finally {
            engineInitDone = true
        }
    }

    /** Main process name == package name; stubs are "package:pN". Unknown => synchronous (original behavior). */
    private fun isMainProcess(context: Context): Boolean {
        return try {
            val raw = java.io.File("/proc/self/cmdline").readBytes()
            val name = raw.takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
            name == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        EngineLog.init(this)
        engine = CloneEngine(this)
        // Main process: doCreate already runs on the background init thread.
        // Stub/service processes: run it here, synchronously, as before.
        if (!mainProcess) runDoCreate()
        createNotificationChannel()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Record crashes (with stack trace) into the shared engine log
            // file, so a crash inside a clone's stub process is visible
            // later in Settings > Diagnostics > Engine log.
            EngineLog.error("CRASH", "Uncaught exception in process/thread ${thread.name} — this process is about to die. If this was a clone's stub process, its app just crashed.", error)
            Log.e("DualCloneCrash", "Uncaught in ${thread.name}", error)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("clones", "Clone Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Notifications from cloned apps"
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "DualCloneApp"
        lateinit var app: DualCloneApp
            private set
        val db: CloneDatabase get() = CloneDatabase.get(app)
    }
}
