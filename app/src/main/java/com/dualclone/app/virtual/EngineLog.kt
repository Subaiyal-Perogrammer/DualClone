package com.dualclone.app.virtual

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DualClone's in-app engine log (Settings > Diagnostics > Engine log).
 *
 * Everything the engine bridge does — installs, launches, refusals, GMS
 * installs, caught exceptions with stack traces, and crashes inside the
 * clone stub processes — is recorded here so a failure on a real phone can
 * be copied and pasted to the developer instead of failing silently.
 *
 * Entries live in a 600-line ring buffer AND are appended to a shared file
 * (filesDir/engine-log.txt): cloned apps run in separate stub processes
 * with their own in-memory buffers, and only the shared file lets the
 * Diagnostics screen show what happened in every process.
 */
object EngineLog {
    private const val MAX_LINES = 600
    private const val MAX_FILE_BYTES = 512 * 1024
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null

    /** Set during Application.onCreate: did the engine finish initializing? */
    @Volatile var engineInitialized: Boolean = false
    @Volatile var engineInitDetail: String = "Engine startup has not run yet in this process."

    /** Call once per process so entries persist to the shared file. */
    fun init(context: Context) {
        if (logFile == null) logFile = File(context.filesDir, "engine-log.txt")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        add("${fmt.format(Date())} [$tag] $message")
        Log.i(tag, message)
    }

    /**
     * Like [log] but NOT re-emitted to logcat. For lines that came FROM
     * logcat (LogcatCapture): re-logging them makes the next capture re-read
     * its own previous capture — an echo loop that floods the log.
     */
    @Synchronized
    fun logQuiet(tag: String, message: String) {
        add("${fmt.format(Date())} [$tag] $message")
    }

    @Synchronized
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        add("${fmt.format(Date())} [$tag] ERROR: $message")
        if (throwable != null) {
            add(Log.getStackTraceString(throwable).trimEnd())
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    @Synchronized private fun add(text: String) {
        text.lineSequence().forEach { line ->
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
            appendToFile(line)
        }
    }

    private fun appendToFile(line: String) {
        val f = logFile ?: return
        try {
            if (f.length() > MAX_FILE_BYTES) {
                val tail = f.readLines().takeLast(MAX_LINES)
                f.writeText(tail.joinToString("\n", postfix = "\n"))
            }
            f.appendText(line + "\n")
        } catch (e: Exception) {
            // Logging must never crash the app; the in-memory buffer still works.
        }
    }

    /** Last [MAX_LINES] lines — from the shared file when available (all processes), else memory. */
    @Synchronized fun snapshotLines(): List<String> {
        val f = logFile
        if (f != null && f.exists()) {
            try {
                val fromFile = f.readLines().filter { it.isNotBlank() }
                if (fromFile.isNotEmpty()) return fromFile.takeLast(MAX_LINES)
            } catch (e: Exception) { /* fall through to memory */ }
        }
        return lines.toList()
    }

    @Synchronized fun clear() {
        lines.clear()
        try { logFile?.delete() } catch (e: Exception) { /* ignore */ }
    }

    /** Full copyable report: device + engine state header, then the log lines. */
    fun fullReport(context: Context): String = buildString {
        appendLine("=== DualClone engine log ===")
        appendLine("App: ${context.packageName}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.BRAND} ${Build.MODEL} (${Build.MANUFACTURER})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Engine initialized: $engineInitialized — $engineInitDetail")
        appendLine("--- last ${snapshotLines().size} log lines ---")
        snapshotLines().forEach { appendLine(it) }
    }
}
