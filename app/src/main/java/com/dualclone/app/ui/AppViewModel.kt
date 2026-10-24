package com.dualclone.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualclone.app.DualCloneApp
import com.dualclone.app.data.CloneRecord
import com.dualclone.app.data.InstalledApp
import com.dualclone.app.data.InstalledApps
import com.dualclone.app.virtual.CloneEngine
import com.dualclone.app.virtual.EngineLog
import com.dualclone.app.virtual.LogcatCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Live per-clone diagnostics, read from the system/engine at display time. */
data class CloneDiag(
    val record: CloneRecord,
    val installed: Boolean?,
    val gmsInstalled: Boolean?,
    val dataDir: String,
    val dataDirExists: Boolean,
    val dataSizeBytes: Long,
    val originalSplitNames: List<String>
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DualCloneApp
    private val engine: CloneEngine = app.engine
    private val dao = DualCloneApp.db.cloneDao()

    val clones: StateFlow<List<CloneRecord>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Transient user-facing message (snackbar). */
    val message = MutableStateFlow<String?>(null)
    /** Clone id currently being installed/launched (drives the loading overlay). */
    val busyCloneId = MutableStateFlow<Long?>(null)
    val busyLabel = MutableStateFlow<String?>(null)

    val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    val diagnostics = MutableStateFlow<List<CloneDiag>?>(null)
    val diagReadAt = MutableStateFlow<String?>(null)
    val gmsStatus = MutableStateFlow<String?>(null)
    val testCloneStatus = MutableStateFlow<String?>(null)

    fun consumeMessage() { message.value = null }

    fun loadInstalledApps() {
        if (installedApps.value != null) return
        viewModelScope.launch { installedApps.value = InstalledApps.load(app) }
    }

    /** Create a clone record (next free engine user id) and install it into the virtual environment. */
    fun addClone(installed: InstalledApp, onDone: () -> Unit) {
        InstalledApps.isBlocked(installed.packageName)?.let { message.value = it; return }
        viewModelScope.launch {
            val userId = dao.maxUserId() + 1
            val record = CloneRecord(packageName = installed.packageName, label = installed.label, engineUserId = userId)
            val id = dao.insert(record)
            val saved = record.copy(id = id)
            busyCloneId.value = id; busyLabel.value = "Cloning ${installed.label}…"
            val result = engine.install(saved)
            busyCloneId.value = null; busyLabel.value = null
            result.onSuccess { message.value = "${installed.label} cloned. Tap it to open." }
                .onFailure { message.value = "Clone failed for ${installed.label}: ${it.message}" }
            onDone()
        }
    }

    /**
     * Launch a clone with the reference-style loading overlay: ensure it is
     * installed, ask the engine to launch it, and auto-capture logcat a few
     * seconds later so a guest failure lands in the Engine Log.
     */
    fun launchClone(record: CloneRecord) {
        viewModelScope.launch {
            busyCloneId.value = record.id; busyLabel.value = "Opening ${record.displayName}…"
            val ensured = engine.ensureInstalled(record)
            if (ensured.isFailure) {
                busyCloneId.value = null; busyLabel.value = null
                message.value = "Could not open ${record.displayName}: ${ensured.exceptionOrNull()?.message}"
                return@launch
            }
            val error = engine.launchDetailed(record)
            busyCloneId.value = null; busyLabel.value = null
            if (error != null) {
                message.value = error
            } else {
                launch {
                    delay(6_000)
                    LogcatCapture.capture(app, "auto, 6s after launching ${record.packageName}")
                }
            }
        }
    }

    fun deleteClone(record: CloneRecord) {
        viewModelScope.launch {
            engine.uninstall(record)
            dao.delete(record)
            message.value = "${record.displayName} clone deleted."
        }
    }

    fun clearCloneData(record: CloneRecord) {
        viewModelScope.launch {
            engine.clearData(record)
            message.value = "${record.displayName} clone data cleared."
        }
    }

    fun renameClone(record: CloneRecord, newName: String) {
        viewModelScope.launch {
            dao.update(record.copy(customName = newName.ifBlank { null }))
        }
    }

    fun installGms(record: CloneRecord) {
        viewModelScope.launch {
            gmsStatus.value = "Installing Google services for ${record.displayName}…"
            val result = engine.installGms(record)
            gmsStatus.value = result.getOrElse { "Google services install failed for ${record.displayName}: ${it.message}" }
            refreshDiagnostics()
        }
    }

    /** Smallest non-system installed app cloned as an engine smoke test. */
    fun testClone() {
        viewModelScope.launch {
            testCloneStatus.value = "Running: finding the smallest installed app…"
            try {
                val apps = InstalledApps.load(app)
                val candidate = apps.filter { !it.isSystem }
                    .filter { InstalledApps.isBlocked(it.packageName) == null }
                    .minByOrNull { if (it.apkSizeBytes > 0) it.apkSizeBytes else Long.MAX_VALUE }
                if (candidate == null) {
                    testCloneStatus.value = "No suitable installed app found to test with."
                    return@launch
                }
                testCloneStatus.value = "Cloning ${candidate.label} (${candidate.packageName}) as a test…"
                val userId = dao.maxUserId() + 1
                val record = CloneRecord(packageName = candidate.packageName, label = candidate.label, engineUserId = userId)
                val id = dao.insert(record)
                val saved = record.copy(id = id)
                val result = engine.install(saved)
                testCloneStatus.value = result.fold(
                    onSuccess = { "Test clone OK: ${candidate.label} installed into the virtual environment for user $userId. It now appears on the home grid — open it to verify the launch too." },
                    onFailure = { "Test clone FAILED for ${candidate.label}: ${it.message} (see Engine Log for the engine's exact words)." }
                )
            } catch (e: Exception) {
                testCloneStatus.value = "Test clone threw: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val list = dao.all()
            diagnostics.value = list.map { record ->
                val original = try {
                    app.packageManager.getPackageInfo(record.packageName, 0)?.splitNames?.toList() ?: emptyList()
                } catch (e: Exception) { emptyList() }
                val dir = engine.dataDir(record)
                CloneDiag(
                    record = record,
                    installed = try { engine.isInstalled(record) } catch (e: Exception) { null },
                    gmsInstalled = engine.isGmsInstalled(record),
                    dataDir = dir.absolutePath,
                    dataDirExists = dir.exists(),
                    dataSizeBytes = engine.dataSizeBytes(record),
                    originalSplitNames = original
                )
            }
            diagReadAt.value = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", java.util.Locale.US).format(java.util.Date())
        }
    }

    fun captureLogcatNow() {
        viewModelScope.launch {
            val n = LogcatCapture.capture(app, "manual, from Diagnostics")
            message.value = "Captured $n logcat lines into the Engine Log."
        }
    }

    fun deviceSummary(): List<String> = listOf(
        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Device: ${Build.BRAND} ${Build.MODEL} (${Build.MANUFACTURER})",
        "ABI: ${Build.SUPPORTED_ABIS.joinToString()}",
        "Engine initialized: ${EngineLog.engineInitialized} — ${EngineLog.engineInitDetail}",
        "Stub processes alive: ${engine.stubProcesses().joinToString().ifBlank { "none right now" }}",
        "Engine: ${CloneEngine.ENGINE_NAME}"
    )

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun dataDirOf(record: CloneRecord): File = engine.dataDir(record)
}
