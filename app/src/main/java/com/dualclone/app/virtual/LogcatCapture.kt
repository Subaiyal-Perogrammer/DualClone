package com.dualclone.app.virtual

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captures logcat lines belonging to DualClone's own processes — which
 * includes the BlackBox stub processes (:p0, :p1, …) where cloned apps
 * actually run, because they share this app's UID. Since Android 7 an app
 * can only read its own UID's logcat lines, which is exactly the set we
 * need: the real crash/network/render errors of a misbehaving clone land
 * here. Captured lines are written into the EngineLog.
 *
 * Anti-flood rules: captured lines are written QUIETLY (never re-emitted
 * to logcat, or the next capture re-reads its own output); lines whose log
 * tag is a previous capture ("Logcat/…") are skipped; consecutive repeats
 * of the same message collapse into one line plus a repeat count.
 */
object LogcatCapture {
    private const val MAX_CAPTURE_LINES = 200
    private val echoTag = Regex("""[VIDWE] Logcat/""")
    private val linePrefix = Regex("""^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+\s+[VIDWE]\s+""")

    /** Runs logcat for each of our processes; returns how many lines were captured. */
    suspend fun capture(context: Context, reason: String): Int = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val own = (am.runningAppProcesses ?: emptyList())
            .filter { it.processName == context.packageName || it.processName.startsWith(context.packageName + ":") }
        if (own.isEmpty()) {
            EngineLog.log("Logcat", "Capture ($reason): no DualClone processes reported by ActivityManager.")
            return@withContext 0
        }
        var total = 0
        EngineLog.log("Logcat", "=== logcat capture ($reason) for processes: ${own.joinToString { "${it.processName}:${it.pid}" }} ===")
        for (proc in own) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "--pid=${proc.pid}", "-t", "150"))
                val captured = process.inputStream.bufferedReader().readLines()
                process.waitFor()
                val kept = collapse(captured.takeLast(80))
                val shortName = proc.processName.substringAfterLast(':')
                kept.forEach { EngineLog.logQuiet("Logcat/$shortName", it) }
                total += kept.size
            } catch (e: Exception) {
                EngineLog.error("Logcat", "Capture failed for ${proc.processName} (pid ${proc.pid})", e)
            }
            if (total >= MAX_CAPTURE_LINES) break
        }
        EngineLog.log("Logcat", "=== logcat capture done: $total lines ===")
        total
    }

    /** Drops previous-capture echo lines and collapses consecutive duplicate messages. */
    private fun collapse(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var lastNorm: String? = null
        var repeats = 0
        fun flushRepeats() {
            if (repeats > 0) {
                out.add("… (previous line repeated $repeats more times)")
                repeats = 0
            }
        }
        for (line in lines) {
            if (echoTag.containsMatchIn(line) || line.contains("[Logcat/")) continue
            val norm = line.replace(linePrefix, "")
            if (norm == lastNorm) {
                repeats++
                continue
            }
            flushRepeats()
            out.add(line)
            lastNorm = norm
        }
        flushRepeats()
        return out
    }
}
