package com.example.waypoint.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.waypoint.data.model.WaypointModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    waypoints: List<WaypointModel>,
    selectedWaypointId: String?,
    isRecenterRequested: Boolean,
    onRecenterHandled: () -> Unit,
    onLongPress: (latitude: Double, longitude: Double) -> Unit,
    onMarkerTap: (WaypointModel) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            isVerticalMapRepetitionEnabled = false
        }
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            disableFollowLocation()
        }
    }

    // Add base overlays once
    LaunchedEffect(Unit) {
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { onLongPress(it.latitude, it.longitude) }
                return true
            }
        })
        mapView.overlays.add(0, eventsOverlay)
        mapView.overlays.add(myLocationOverlay)
    }

    // Recenter on user location
    LaunchedEffect(isRecenterRequested) {
        if (isRecenterRequested) {
            val loc = myLocationOverlay.myLocation
            if (loc != null) {
                mapView.controller.animateTo(loc, 15.0, 800L)
            }
            onRecenterHandled()
        }
    }

    // Sync waypoint markers when list or selection changes
    LaunchedEffect(waypoints, selectedWaypointId) {
        syncWaypointMarkers(
            mapView = mapView,
            waypoints = waypoints,
            selectedWaypointId = selectedWaypointId,
            onMarkerTap = onMarkerTap
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { it.invalidate() }
    )

    // Lifecycle management for OSMDroid
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
}

private fun syncWaypointMarkers(
    mapView: MapView,
    waypoints: List<WaypointModel>,
    selectedWaypointId: String?,
    onMarkerTap: (WaypointModel) -> Unit
) {
    // Remove existing waypoint markers, keep system overlays (first two: events + myLocation)
    val systemOverlays = mapView.overlays.take(2).toMutableList()
    mapView.overlays.clear()
    mapView.overlays.addAll(systemOverlays)

    waypoints.forEach { wp ->
        val isSelected = wp.id == selectedWaypointId
        val marker = Marker(mapView).apply {
            position = GeoPoint(wp.latitude, wp.longitude)
            title = wp.name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = createPinDrawable(mapView.context, isSelected)
            setOnMarkerClickListener { _, _ ->
                onMarkerTap(wp)
                true
            }
        }
        mapView.overlays.add(marker)
    }
    mapView.invalidate()
}

private fun createPinDrawable(context: Context, selected: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val tailHeight = (14 * density).toInt()
    val totalHeight = size + tailHeight

    val bitmap = Bitmap.createBitmap(size, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinColor = if (selected) Color.rgb(255, 87, 34) else Color.rgb(229, 57, 53)
    val circleColor = if (selected) Color.WHITE else Color.WHITE

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }

    val cx = size / 2f
    val cy = size / 2f
    val radius = size / 2f - density

    // Draw circle body
    canvas.drawCircle(cx, cy, radius, paint)

    // Draw tail (downward pointing triangle)
    val tailPath = Path().apply {
        moveTo(cx - radius * 0.5f, cy + radius * 0.7f)
        lineTo(cx + radius * 0.5f, cy + radius * 0.7f)
        lineTo(cx, totalHeight.toFloat())
        close()
    }
    canvas.drawPath(tailPath, paint)

    // Draw inner white circle
    paint.color = circleColor
    canvas.drawCircle(cx, cy, radius * 0.38f, paint)

    return BitmapDrawable(context.resources, bitmap)
}
