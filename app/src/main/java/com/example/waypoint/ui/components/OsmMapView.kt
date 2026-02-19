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
            // Hide built-in zoom buttons — pinch-to-zoom only (matches iOS)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            val dotBitmap = createLocationDotBitmap(context)
            setPersonIcon(dotBitmap)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionIcon(dotBitmap)
            setDirectionAnchor(0.5f, 0.5f)
        }
    }

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

    LaunchedEffect(isRecenterRequested) {
        if (isRecenterRequested) {
            val loc = myLocationOverlay.myLocation
            if (loc != null) {
                // Zoom level 17 ≈ street-level (matches iOS 0.01 delta ≈ ~1km range → zoom ~16-17)
                mapView.controller.animateTo(loc, 17.0, 800L)
            }
            onRecenterHandled()
        }
    }

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
    val systemOverlays = mapView.overlays.take(2).toMutableList()
    mapView.overlays.clear()
    mapView.overlays.addAll(systemOverlays)

    waypoints.forEach { wp ->
        val isSelected = wp.id == selectedWaypointId
        val marker = Marker(mapView).apply {
            position = GeoPoint(wp.latitude, wp.longitude)
            title = wp.name
            // Anchor at bottom-centre of the pin tip
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
 * Clean iOS-style balloon pin:
 *  - Pure filled teardrop — no inner ring, no glyph, just a solid smooth shape
 *  - Matches the minimalist look of MKMarkerAnnotationView in red
 *  - Subtle drop-shadow for depth
 *  - Default: iOS system red, Selected: iOS system orange
 */
private fun createPinDrawable(context: Context, selected: Boolean): BitmapDrawable {
    val dp = context.resources.displayMetrics.density

    // Dimensions
    val ballR   = 11f * dp   // radius of the ball head
    val tipH    = 14f * dp   // height of the pointed tail below the ball
    val pad     = 4f  * dp   // padding around edges for shadow bleed

    val w = ((ballR + pad) * 2).toInt()
    val h = ((ballR * 2) + tipH + pad * 2).toInt()
    val cx = w / 2f
    val ballCy = pad + ballR  // centre of ball

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ── Shadow ──────────────────────────────────────────────────────────────
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        maskFilter = BlurMaskFilter(3.5f * dp, BlurMaskFilter.Blur.NORMAL)
        style = Paint.Style.FILL
    }
    // Draw the full teardrop shape slightly offset as shadow
    val shadowPath = buildTearPath(cx + 1f * dp, ballCy + 2f * dp, ballR, tipH)
    canvas.drawPath(shadowPath, shadowPaint)

    // ── Pin fill ─────────────────────────────────────────────────────────────
    // iOS red:    #FF3B30  (selected gets a touch more orange: #FF6B2B)
    val pinColor = if (selected) Color.rgb(255, 107, 43) else Color.rgb(255, 59, 48)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    val pinPath = buildTearPath(cx, ballCy, ballR, tipH)
    canvas.drawPath(pinPath, fillPaint)

    // ── Subtle top highlight (semi-transparent white arc at top of ball) ──────
    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }
    // Small filled arc in the upper-left quadrant of the ball
    val highlightPath = Path().apply {
        addCircle(cx - ballR * 0.18f, ballCy - ballR * 0.2f, ballR * 0.42f, Path.Direction.CW)
    }
    // Clip to just the ball area before drawing highlight
    canvas.save()
    val clipPath = Path().apply { addCircle(cx, ballCy, ballR, Path.Direction.CW) }
    canvas.clipPath(clipPath)
    canvas.drawPath(highlightPath, highlightPaint)
    canvas.restore()

    return BitmapDrawable(context.resources, bitmap)
}

/** Builds a smooth teardrop path: ball on top, sharp tip pointing down */
private fun buildTearPath(cx: Float, ballCy: Float, ballR: Float, tipH: Float): Path {
    return Path().apply {
        // Start at right side of ball
        moveTo(cx + ballR, ballCy)
        // Top arc of ball (right → top → left)
        arcTo(
            cx - ballR, ballCy - ballR,
            cx + ballR, ballCy + ballR,
            0f, -180f, false
        )
        // Bottom-left of ball curves into tail
        // Left side goes down and inward to tip
        quadTo(
            cx - ballR, ballCy + ballR * 0.85f,   // control point
            cx, ballCy + ballR + tipH              // tip point
        )
        // Right side mirrors
        quadTo(
            cx + ballR, ballCy + ballR * 0.85f,
            cx + ballR, ballCy
        )
        close()
    }
}

/**
 * iOS-style user location dot — solid iOS blue with white ring and faint glow.
 */
private fun createLocationDotBitmap(context: Context): Bitmap {
    val dp = context.resources.displayMetrics.density
    val dotR  = 8f * dp
    val ringR = 11f * dp
    val glowR = 16f * dp
    val size  = (glowR * 2 + 2 * dp).toInt()
    val cx    = size / 2f
    val cy    = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Outer glow
    canvas.drawCircle(cx, cy, glowR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 0, 122, 255)
        style = Paint.Style.FILL
    })
    // White ring
    canvas.drawCircle(cx, cy, ringR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    })
    // Blue dot
    canvas.drawCircle(cx, cy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 122, 255)
        style = Paint.Style.FILL
    })

    return bitmap
}
