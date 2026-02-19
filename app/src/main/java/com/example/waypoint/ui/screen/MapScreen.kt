package com.example.waypoint.ui.screen

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waypoint.ui.components.AddHintCapsule
import com.example.waypoint.ui.components.MapHeader
import com.example.waypoint.ui.components.OsmMapView
import com.example.waypoint.ui.components.RecenterButton
import com.example.waypoint.ui.components.WaypointDetailSheet
import com.example.waypoint.util.HapticUtil
import com.example.waypoint.viewmodel.WaypointViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(viewModel: WaypointViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticUtil(context) }

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Request permissions on first composition
    LaunchedEffect(Unit) {
        if (!locationPermissionsState.allPermissionsGranted) {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    // Start location updates once permissions are granted
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            viewModel.startLocationUpdates()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Full-screen map
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            waypoints = uiState.waypoints,
            selectedWaypointId = uiState.selectedWaypoint?.id,
            isRecenterRequested = uiState.isRecenterRequested,
            onRecenterHandled = { viewModel.recenterHandled() },
            onLongPress = { lat, lon ->
                haptic.success()
                viewModel.addWaypoint(lat, lon)
            },
            onMarkerTap = { wp ->
                haptic.light()
                viewModel.selectWaypoint(wp)
            }
        )

        // Header overlay (top)
        MapHeader(
            userLocation = uiState.userLocation,
            locationEnabled = uiState.locationEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
        )

        // Hint capsule (bottom center, disappears after first waypoint added)
        if (uiState.showHint) {
            AddHintCapsule(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            )
        }

        // Re-center button (bottom right)
        RecenterButton(
            enabled = uiState.locationEnabled,
            onClick = { viewModel.requestRecenter() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
        )
    }

    // Bottom sheet shown when a waypoint is selected
    uiState.selectedWaypoint?.let { wp ->
        WaypointDetailSheet(
            waypoint = wp,
            userLocation = uiState.userLocation,
            isEditing = uiState.isEditingWaypoint,
            onDismiss = { viewModel.selectWaypoint(null) },
            onEdit = {
                viewModel.startEditing()
            },
            onCancelEdit = {
                viewModel.cancelEditing()
            },
            onSave = { name, notes ->
                haptic.success()
                viewModel.saveEdit(wp.id, name, notes)
            },
            onDelete = {
                haptic.warning()
                viewModel.deleteWaypoint(wp.id)
            }
        )
    }
}
