package com.example.waypoint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waypoint.ui.screen.MapScreen
import com.example.waypoint.ui.theme.WaypointTheme
import com.example.waypoint.viewmodel.WaypointViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaypointTheme {
                val viewModel: WaypointViewModel = viewModel()
                MapScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
