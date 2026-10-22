package com.ilkimeren.shotfind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ilkimeren.shotfind.ui.ShotFindApp
import com.ilkimeren.shotfind.ui.theme.ShotFindTheme
import com.ilkimeren.shotfind.work.NewScreenshotWorker
import com.ilkimeren.shotfind.work.WatchdogWorker
import com.ilkimeren.shotfind.work.TempCleanupWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the app draw behind the status and navigation bars
        enableEdgeToEdge()
        // Make sure the background watcher for new screenshots is armed
        NewScreenshotWorker.ensureScheduled(this)
        WatchdogWorker.schedule(this)
        TempCleanupWorker.schedule(this)
        setContent {
            ShotFindTheme {
                ShotFindApp()
            }
        }
    }
}