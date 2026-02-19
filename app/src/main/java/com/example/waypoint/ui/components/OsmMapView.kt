package com.example.waypoint.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import org.osmdroid.views.CustomZoomButtonsController
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
            // Hide built-in zoom buttons — pinch-to-zoom only, matches iOS
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            // Replace default Android arrow with clean iOS-style blue dot
            val dotBitmap = createLocationDotBitmap(context)
            setPersonIcon(dotBitmap)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionIcon(dotBitmap)
            setDirectionAnchor(0.5f, 0.5f)
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

/**
 * iOS-style lollipop map pin — mirrors MKMarkerAnnotationView:
 *  - Filled ball + smooth tapered stem pointing down
 *  - White inner ring for depth (like the iOS glint)
 *  - Subtle drop shadow
 *  - Default: system red #E53935, Selected: vivid orange #FF5722
 */
private fun createPinDrawable(context: Context, selected: Boolean): BitmapDrawable {
    val dp = context.resources.displayMetrics.density

    val ballR = 12f * dp
    val stemW = 5f * dp
    val stemH = 10f * dp
    val pad = 3f * dp          // padding for shadow room

    val totalW = ((ballR + pad) * 2).toInt()
    val totalH = (ballR * 2 + stemH + pad * 2).toInt()
    val cx = totalW / 2f
    val ballCy = ballR + pad

    val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Drop shadow (soft blur underneath)
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 0, 0, 0)
        maskFilter = BlurMaskFilter(3f * dp, BlurMaskFilter.Blur.NORMAL)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, ballCy + 1.5f * dp, ballR, shadowPaint)

    val pinColor = if (selected) Color.rgb(255, 87, 34) else Color.rgb(229, 57, 53)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }

    // Ball
    canvas.drawCircle(cx, ballCy, ballR, fillPaint)

    // Stem: tapered triangle that smoothly continues from the ball bottom
    val stemPath = Path().apply {
        moveTo(cx - stemW / 2f, ballCy + ballR * 0.7f)
        lineTo(cx + stemW / 2f, ballCy + ballR * 0.7f)
        lineTo(cx, ballCy + ballR + stemH)
        close()
    }
    canvas.drawPath(stemPath, fillPaint)

    // White inner ring — the "glint" that gives the iOS pin its depth
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        alpha = 200
    }
    canvas.drawCircle(cx, ballCy, ballR - 3.5f * dp, ringPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * iOS-style user location dot:
 *  - Bright iOS blue (#007AFF) filled circle
 *  - White border ring (accuracy indicator style)
 *  - Faint blue outer glow pulse
 */
private fun createLocationDotBitmap(context: Context): Bitmap {
    val dp = context.resources.displayMetrics.density
    val dotR = 8f * dp
    val ringR = 11f * dp
    val glowR = 16f * dp
    val size = (glowR * 2 + 2 * dp).toInt()
    val cx = size / 2f
    val cy = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Outer glow ring (semi-transparent blue)
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 0, 122, 255)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, glowR, glowPaint)

    // White ring
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, ringR, ringPaint)

    // iOS blue dot
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 122, 255)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, dotR, dotPaint)

    return bitmap
}
