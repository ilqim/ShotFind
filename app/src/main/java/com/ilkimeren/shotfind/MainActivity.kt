package com.ilkimeren.shotfind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ilkimeren.shotfind.ui.ShotFindApp
import com.ilkimeren.shotfind.ui.theme.ShotFindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the app draw behind the status and navigation bars
        enableEdgeToEdge()
        setContent {
            ShotFindTheme {
                ShotFindApp()
            }
        }
    }
}