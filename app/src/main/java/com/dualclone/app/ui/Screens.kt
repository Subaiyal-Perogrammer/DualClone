package com.dualclone.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dualclone.app.data.CloneRecord
import com.dualclone.app.data.InstalledApp
import com.dualclone.app.data.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Simple in-app navigation state (no nav library in the fresh shell). */
sealed interface Screen {
    data object Home : Screen
    data object AddApp : Screen
    data object Settings : Screen
    data object EngineLog : Screen
    data class Detail(val cloneId: Long) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualCloneRoot(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val clones by vm.clones.collectAsState()
    val message by vm.message.collectAsState()
    val busyId by vm.busyCloneId.collectAsState()
    val busyLabel by vm.busyLabel.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when (screen) {
                        Screen.Home -> "DualClone"
                        Screen.AddApp -> "Add app"
                        Screen.Settings -> "Settings"
                        Screen.EngineLog -> "Engine log"
                        is Screen.Detail -> "Clone"
                    }
                )
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen is Screen.Home || screen is Screen.AddApp || screen is Screen.Detail,
                    onClick = { screen = Screen.Home },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = screen is Screen.Settings || screen is Screen.EngineLog,
                    onClick = { screen = Screen.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            if (screen is Screen.Home) {
                FloatingActionButton(onClick = { vm.loadInstalledApps(); screen = Screen.AddApp }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add app")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = screen) {
                Screen.Home -> HomeGrid(
                    clones = clones,
                    onOpen = { vm.launchClone(it) },
                    onDetail = { screen = Screen.Detail(it.id) }
                )
                Screen.AddApp -> AddAppList(
                    vm = vm,
                    onCloned = { screen = Screen.Home }
                )
                Screen.Settings -> SettingsScreen(
                    vm = vm,
                    onOpenLog = { screen = Screen.EngineLog }
                )
                Screen.EngineLog -> EngineLogScreen(vm = vm, onBack = { screen = Screen.Settings })
                is Screen.Detail -> {
                    val record = clones.firstOrNull { it.id == s.cloneId }
                    if (record == null) {
                        screen = Screen.Home
                    } else {
                        CloneDetailScreen(
                            vm = vm,
                            record = record,
                            onDeleted = { screen = Screen.Home }
                        )
                    }
                }
            }
            // Reference-style opening screen while a clone installs/launches.
            if (busyId != null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(56.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(busyLabel ?: "Working…", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The clone runs inside DualClone's virtual environment — the original app is never opened.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, size: Int = 56) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, packageName) {
        value = withContext(Dispatchers.IO) { InstalledApps.icon(context, packageName) }
    }
    val d = drawable
    if (d != null) {
        Image(
            bitmap = remember(d) { d.toBitmap(size * 3, size * 3).asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(14.dp))
        )
    } else {
        Surface(Modifier.size(size.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeGrid(clones: List<CloneRecord>, onOpen: (CloneRecord) -> Unit, onDetail: (CloneRecord) -> Unit) {
    if (clones.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Apps, contentDescription = null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("No clones yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap + to clone an installed app. Each clone runs in its own isolated space with its own data and login.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(clones, key = { it.id }) { record ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(
                    onClick = { onOpen(record) },
                    onLongClick = { onDetail(record) }
                )
            ) {
                AppIcon(record.packageName, 60)
                Spacer(Modifier.height(6.dp))
                Text(
                    record.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AddAppList(vm: AppViewModel, onCloned: () -> Unit) {
    val apps by vm.installedApps.collectAsState()
    var query by remember { mutableStateOf("") }
    val list = apps
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        val filtered = list.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.packageName }) { app ->
                val blocked = InstalledApps.isBlocked(app.packageName)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, 48)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val meta = buildString {
                            append(vm.formatSize(app.apkSizeBytes))
                            if (app.isSplit) append(" • Split APKs (${app.splitNames.size} splits) — passed into the virtual environment")
                            if (app.isSystem) append(" • System app")
                            if (blocked != null) append(" • Not supported")
                        }
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { vm.addClone(app, onCloned) },
                        enabled = blocked == null
                    ) { Text("Clone") }
                }
            }
        }
    }
}
