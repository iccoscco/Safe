package com.example.appgestin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.appgestin.controller.GstiController
import com.example.appgestin.ui.GstiView
import com.example.appgestin.ui.theme.AppGestiónTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = GstiController(applicationContext, lifecycleScope)
        setContent {
            AppGestiónTheme {
                GstiView(controller.state, controller)
            }
        }
    }
}
