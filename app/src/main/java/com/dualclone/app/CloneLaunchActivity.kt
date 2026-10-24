package com.dualclone.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dualclone.app.ui.AppIcon
import com.dualclone.app.ui.DualCloneTheme
import com.dualclone.app.virtual.LogcatCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Target of home-screen clone shortcuts: shows a short opening screen while
 * the engine ensures the clone is installed, launches the guest inside the
 * virtual environment, then finishes. Errors are shown as toasts with the
 * engine's real reason — never a fallback to the real app.
 */
class CloneLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cloneId = intent.getLongExtra(EXTRA_CLONE_ID, -1L)
        setContent {
            DualCloneTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(52.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Opening clone…", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        lifecycleScope.launch {
            val record = if (cloneId > 0) DualCloneApp.db.cloneDao().byId(cloneId) else null
            if (record == null) {
                Toast.makeText(this@CloneLaunchActivity, "Clone not found (it may have been deleted).", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val ensured = DualCloneApp.app.engine.ensureInstalled(record)
            if (ensured.isFailure) {
                Toast.makeText(this@CloneLaunchActivity, "Could not open ${record.displayName}: ${ensured.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val error = DualCloneApp.app.engine.launchDetailed(record)
            if (error != null) {
                Toast.makeText(this@CloneLaunchActivity, error, Toast.LENGTH_LONG).show()
            } else {
                launch {
                    delay(6_000)
                    LogcatCapture.capture(this@CloneLaunchActivity, "auto, 6s after shortcut launch of ${record.packageName}")
                }
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_CLONE_ID = "com.dualclone.app.extra.CLONE_ID"
    }
}
