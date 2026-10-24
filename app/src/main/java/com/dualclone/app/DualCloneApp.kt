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
 * then doCreate in onCreate). It must run in EVERY process of this app (the
 * engine's stub processes and its service process included), which is why it
 * lives in attachBaseContext with no process-name guard.
 *
 * Every step is failure-tolerant: if the engine cannot start, DualClone
 * still opens, and clone launches report the engine's real failure instead
 * of ever falling back to opening a real app.
 */
class DualCloneApp : Application() {

    lateinit var engine: CloneEngine
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        EngineLog.log(TAG, "Engine startup in process ${android.os.Process.myPid()} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.BRAND} ${Build.MODEL}, ABI ${Build.SUPPORTED_ABIS.firstOrNull()}")
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

    override fun onCreate() {
        super.onCreate()
        app = this
        EngineLog.init(this)
        engine = CloneEngine(this)
        try {
            BlackBoxCore.get().doCreate()
            EngineLog.engineInitialized = true
            EngineLog.engineInitDetail = "doCreate() completed without throwing in this process."
            EngineLog.log(TAG, "doCreate OK — engine initialized")
        } catch (e: Exception) {
            EngineLog.engineInitialized = false
            EngineLog.engineInitDetail = "doCreate() threw: ${e.javaClass.simpleName}: ${e.message}"
            EngineLog.error(TAG, "BlackBox doCreate failed", e)
        }
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
