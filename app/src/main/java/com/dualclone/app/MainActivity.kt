package com.dualclone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dualclone.app.ui.AppViewModel
import com.dualclone.app.ui.DualCloneRoot
import com.dualclone.app.ui.DualCloneTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DualCloneTheme {
                DualCloneRoot(vm)
            }
        }
    }
}
