package com.example.waypoint.ui.screen

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.waypoint.ui.components.WaypointDetailCard
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
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )

    LaunchedEffect(Unit) {
        if (!locationPermissionsState.allPermissionsGranted) {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            viewModel.startLocationUpdates()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen map ──────────────────────────────────────────────────
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

        // ── Header (top) ─────────────────────────────────────────────────────
        MapHeader(
            userLocation = uiState.userLocation,
            locationEnabled = uiState.locationEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
        )

        // ── Bottom row: card (left) + recenter (right) ───────────────────────
        // Mirrors the iOS ContentView HStack layout exactly.
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Left slot: detail card OR hint capsule
            // Use Column (not Box) so AnimatedVisibility resolves to the
            // ColumnScope overload instead of the ambiguous RowScope one.
            Column(modifier = Modifier.weight(1f)) {
                // Hint capsule (shown until first waypoint is added)
                AnimatedVisibility(
                    visible = uiState.showHint && uiState.selectedWaypoint == null,
                    enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
                    exit  = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180)),
                ) {
                    AddHintCapsule()
                }

                // Waypoint detail card (shown when a waypoint is selected)
                val selectedWp = uiState.selectedWaypoint
                AnimatedVisibility(
                    visible = selectedWp != null,
                    enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
                    exit  = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180)),
                ) {
                    if (selectedWp != null) {
                        WaypointDetailCard(
                            waypoint = selectedWp,
                            userLocation = uiState.userLocation,
                            onDismiss = { viewModel.selectWaypoint(null) },
                            onDelete = {
                                haptic.warning()
                                viewModel.deleteWaypoint(selectedWp.id)
                            },
                            onSave = { name, notes ->
                                haptic.success()
                                viewModel.saveEdit(selectedWp.id, name, notes)
                            },
                        )
                    }
                }
            }

            // Right slot: recenter button (always visible)
            RecenterButton(
                enabled = uiState.locationEnabled,
                onClick = { viewModel.requestRecenter() },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
