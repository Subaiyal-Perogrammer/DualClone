package com.dualclone.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dualclone.app.CloneLaunchActivity
import com.dualclone.app.data.CloneRecord
import com.dualclone.app.data.InstalledApps
import com.dualclone.app.virtual.CloneEngine
import com.dualclone.app.virtual.EngineLog

@Composable
fun CloneDetailScreen(vm: AppViewModel, record: CloneRecord, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val gmsStatus by vm.gmsStatus.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(record.displayName) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(record.packageName, 84)
        Spacer(Modifier.height(12.dp))
        Text(record.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(record.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Virtual user ${record.engineUserId} • Data: ${vm.formatSize(vm.dataDirOf(record).let { if (it.exists()) it.walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() } else 0L })}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Button(onClick = { vm.launchClone(record) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text("  Open clone")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { vm.installGms(record) }, modifier = Modifier.fillMaxWidth()) {
            Text("Install Google Services for This Clone")
        }
        gmsStatus?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val ok = createHomeShortcut(context, record)
                Toast.makeText(context, if (ok) "Shortcut requested — confirm on your home screen." else "Your launcher does not support pinned shortcuts.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Shortcut, contentDescription = null)
            Text("  Add Home-Screen Shortcut")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Text("  Rename clone")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { vm.clearCloneData(record) }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear clone data")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text("  Delete clone")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Clones run inside DualClone's virtual environment (${CloneEngine.ENGINE_NAME}). The original app is never opened as a fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this clone?") },
            text = { Text("The clone and its virtual data (including its login) will be removed. The original app is not touched.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteClone(record); onDeleted() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename clone") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { renaming = false; vm.renameClone(record, newName) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }
}

/** Pin a home-screen shortcut that opens this clone through CloneLaunchActivity. */
fun createHomeShortcut(context: Context, record: CloneRecord): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!sm.isRequestPinShortcutSupported) return false
    val intent = Intent(context, CloneLaunchActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(CloneLaunchActivity.EXTRA_CLONE_ID, record.id)
    }
    val drawable = InstalledApps.icon(context, record.packageName)
    val icon = if (drawable != null) AndroidIcon.createWithBitmap(drawable.toBitmap(192, 192)) else AndroidIcon.createWithResource(context, com.dualclone.app.R.mipmap.ic_launcher)
    val info = ShortcutInfo.Builder(context, "clone-${record.id}")
        .setShortLabel(record.displayName)
        .setLongLabel("Open ${record.displayName} (clone)")
        .setIcon(icon)
        .setIntent(intent)
        .build()
    return sm.requestPinShortcut(info, null)
}

@Composable
fun SettingsScreen(vm: AppViewModel, onOpenLog: () -> Unit) {
    val diagnostics by vm.diagnostics.collectAsState()
    val readAt by vm.diagReadAt.collectAsState()
    val gmsStatus by vm.gmsStatus.collectAsState()
    val testStatus by vm.testCloneStatus.collectAsState()

    LaunchedEffect(Unit) { vm.refreshDiagnostics() }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("DualClone 1.0.0 — a fresh app shell on the ${CloneEngine.ENGINE_NAME} virtualization engine (vendored source, Apache-2.0). Clones are isolated per virtual user; the original apps are never launched as a fallback.")
                    Spacer(Modifier.height(6.dp))
                    Text("DualClone stores clone data only on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Owner: Subaiyal", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("This device (read live)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    vm.deviceSummary().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenLog, modifier = Modifier.weight(1f)) { Text("Engine Log") }
                OutlinedButton(onClick = { vm.testClone() }, modifier = Modifier.weight(1f)) { Text("Test Clone") }
            }
            testStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            gmsStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.refreshDiagnostics() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh Diagnostics") }
            readAt?.let {
                Spacer(Modifier.height(6.dp))
                Text("Values read from the system at $it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val diags = diagnostics
        if (diags == null) {
            item { Text("Reading diagnostics…", style = MaterialTheme.typography.bodySmall) }
        } else if (diags.isEmpty()) {
            item { Text("No clones yet — diagnostics for clones will appear here.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(diags, key = { it.record.id }) { d ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(d.record.displayName, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Package: ${d.record.packageName} • Virtual user: ${d.record.engineUserId}", style = MaterialTheme.typography.bodySmall)
                        Text("Installed in virtual environment (engine's live answer): ${d.installed?.let { if (it) "YES" else "NO" } ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                        Text("Virtual data path: ${d.dataDir} — exists: ${d.dataDirExists}", style = MaterialTheme.typography.bodySmall)
                        Text("Actual size of that folder: ${vm.formatSize(d.dataSizeBytes)} (measured now)", style = MaterialTheme.typography.bodySmall)
                        if (d.originalSplitNames.isEmpty()) {
                            Text("Original app install type: single APK (no splits)", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Original app install type: SPLIT APKs (${d.originalSplitNames.size} splits: ${d.originalSplitNames.joinToString()}) — base + all splits are passed into the virtual environment", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Google services inside the virtual environment (this clone's user): ${d.gmsInstalled?.let { if (it) "INSTALLED" else "not installed" } ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.installGms(d.record) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Install Google Services for This Clone")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EngineLogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tick by remember { mutableStateOf(0) }
    val lines = remember(tick) { EngineLog.snapshotLines() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "The last ${lines.size} lines of engine/launch logging, recorded live at runtime — installs, launches, refusals, exceptions with stack traces, and captured logcat from the clone processes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(EngineLog.fullReport(context)))
                Toast.makeText(context, "Engine log copied.", Toast.LENGTH_SHORT).show()
            }) { Text("Copy Log") }
            OutlinedButton(onClick = { vm.captureLogcatNow(); tick++ }) { Text("Capture Logcat") }
            OutlinedButton(onClick = { EngineLog.clear(); tick++ }) { Text("Clear") }
            OutlinedButton(onClick = { tick++ }) { Text("Refresh") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(lines) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
