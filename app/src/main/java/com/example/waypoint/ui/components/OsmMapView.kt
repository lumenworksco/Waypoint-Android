package com.example.waypoint.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
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
                mapView.controller.animateTo(loc, 18.5, 800L)
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
            // anchorV = 1.0 means the very bottom of our bitmap sits on the geo-point.
            // Our bitmap has: label on top, then pin below. The pin tip is at the very
            // bottom of the bitmap, so ANCHOR_BOTTOM is exactly right.
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = createPinWithLabel(mapView.context, wp.name, isSelected)
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
 * Draws a map pin that matches the logo.png design:
 * dark charcoal teardrop with a white circle hole, using EVEN_ODD fill.
 *
 * Bitmap layout (ANCHOR_CENTER / ANCHOR_BOTTOM → tip sits on geo-coord):
 *
 *   ┌──────────────┐   ← white pill with waypoint name
 *   │  Waypoint 1  │
 *   └──────────────┘
 *        [ ◉ ]         ← canvas-drawn pin (teardrop + hole)
 *          ▼  tip      ← this pixel is the anchor
 */
private fun createPinWithLabel(
    context: Context,
    name: String,
    selected: Boolean
): BitmapDrawable {
    val d = context.resources.displayMetrics.density

    // ── Pin dimensions ────────────────────────────────────────────────────────
    // The pin shape: width W, total height H = W * 1.35 (matches logo proportions)
    val pinW  = (32f * d)
    val pinH  = (pinW * 1.35f)
    val pinWi = pinW.toInt()
    val pinHi = pinH.toInt()

    // Pin colour: charcoal (default) or orange-red (selected)
    val pinColor = if (selected) Color.rgb(220, 80, 40) else Color.rgb(60, 55, 52)

    // ── Label pill ────────────────────────────────────────────────────────────
    val textSizePx = 11f * d
    val pillPH     =  5f * d
    val pillPV     =  3f * d
    val pillCorner =  6f * d
    val pillGap    =  3f * d

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color    = Color.rgb(28, 28, 30)
    }
    val textW = textPaint.measureText(name)
    val fm    = textPaint.fontMetrics
    val pillW = textW + pillPH * 2f
    val pillH = (fm.descent - fm.ascent) + pillPV * 2f

    // ── Composite bitmap ──────────────────────────────────────────────────────
    val bitmapW = maxOf(pillW, pinW).toInt()
    val bitmapH = (pillH + pillGap + pinH).toInt()
    val cx      = bitmapW / 2f

    val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas  = Canvas(bitmap)

    // ── Pill ──────────────────────────────────────────────────────────────────
    val pillLeft = cx - pillW / 2f
    // subtle drop shadow
    canvas.drawRoundRect(
        RectF(pillLeft, d * 0.8f, pillLeft + pillW, pillH + d * 0.8f),
        pillCorner, pillCorner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
    )
    // white body
    canvas.drawRoundRect(
        RectF(pillLeft, 0f, pillLeft + pillW, pillH),
        pillCorner, pillCorner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 255, 255, 255) }
    )
    canvas.drawText(name, cx - textW / 2f, pillPV - fm.ascent, textPaint)

    // ── Pin shape (teardrop + hole via EVEN_ODD) ──────────────────────────────
    // Coordinate system: origin at top-left of pin area
    val ox = cx              // horizontal centre
    val oy = pillH + pillGap // top of pin area

    // Radius of the round head of the pin (upper circle part)
    val headR = pinW / 2f
    // Centre of that circle: sits headR from the top
    val headCy = oy + headR

    // The pin tapers to a point at the bottom
    val tipY = oy + pinH

    // Build the outer teardrop path:
    // Two cubic bezier curves that go from the tip up to the head circle,
    // then arc around the top.
    val path = Path().apply {
        fillType = Path.FillType.EVEN_ODD

        // Outer teardrop ──────────────────────────────────────────────────────
        // Start at tip
        moveTo(ox, tipY)

        // Left side: tip → upper-left tangent of head circle
        // The circle is centred at (ox, headCy) with radius headR.
        // Left tangent point is approximately at (ox - headR, headCy + headR * 0.3)
        cubicTo(
            ox - headR * 0.15f, tipY - pinH * 0.08f,   // ctrl1
            ox - headR,         headCy + headR * 0.5f,  // ctrl2
            ox - headR,         headCy                   // end = left of circle
        )
        // Arc the top of the circle (left → right, sweeping through the top)
        arcTo(
            RectF(ox - headR, oy, ox + headR, oy + headR * 2f),
            180f, 180f, false
        )
        // Right side: upper-right of head circle → tip
        cubicTo(
            ox + headR,         headCy + headR * 0.5f,  // ctrl1
            ox + headR * 0.15f, tipY - pinH * 0.08f,   // ctrl2
            ox,                 tipY                     // tip
        )
        close()

        // Inner hole (punched via EVEN_ODD) ───────────────────────────────────
        // Hole centre: upper third of the pin, radius ~30% of headR
        val holeCx = ox
        val holeCy = headCy - headR * 0.05f
        val holeR  = headR * 0.38f
        addCircle(holeCx, holeCy, holeR, Path.Direction.CW)
    }

    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    })

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * iOS-style user location dot — solid blue with white ring and faint glow.
 */
private fun createLocationDotBitmap(context: Context): Bitmap {
    val dp    = context.resources.displayMetrics.density
    val dotR  = 8f * dp
    val ringR = 11f * dp
    val glowR = 16f * dp
    val size  = (glowR * 2 + 2 * dp).toInt()
    val cx    = size / 2f
    val cy    = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    canvas.drawCircle(cx, cy, glowR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 0, 122, 255)
        style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, ringR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 122, 255)
        style = Paint.Style.FILL
    })

    return bitmap
}
