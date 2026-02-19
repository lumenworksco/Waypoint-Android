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
 * Draws a complete map annotation matching the app icon style:
 *
 *   ┌──────────────┐   ← label pill (white, rounded)
 *   │  Waypoint 1  │
 *   └──────────────┘
 *      ( red pin )     ← teardrop with hollow circle (like Google Maps / app icon)
 *          ▼           ← tip = anchor point (geo-coordinate)
 */
private fun createPinWithLabel(
    context: Context,
    name: String,
    selected: Boolean
): BitmapDrawable {
    val d = context.resources.displayMetrics.density

    // ── Pin colours (red body, green hole — matches app icon) ────────────────
    val pinColor  = if (selected) Color.rgb(255, 107, 43) else Color.rgb(220, 57, 46)
    val holeColor = if (selected) Color.rgb(255, 200, 100) else Color.rgb(130, 180, 100)

    // ── Pin geometry ─────────────────────────────────────────────────────────
    val outerR  = 13f * d   // ball radius
    val holeR   =  5f * d   // inner hole radius
    val tailH   = 15f * d   // tail height (from bottom of ball to tip)
    val pad     =  4f * d   // shadow bleed padding

    // ── Label pill ────────────────────────────────────────────────────────────
    val textSize = 11f * d
    val padH     =  5f * d
    val padV     =  3f * d
    val pillR    =  6f * d
    val gap      =  3f * d  // between pill bottom and ball top

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = Color.rgb(28, 28, 30)
    }
    val textW = textPaint.measureText(name)
    val fm    = textPaint.fontMetrics
    val pillW = textW + padH * 2
    val pillH = (fm.descent - fm.ascent) + padV * 2

    // ── Bitmap dimensions ────────────────────────────────────────────────────
    val contentW = maxOf(pillW, outerR * 2f)
    val bitmapW  = (contentW + pad * 2).toInt()
    val bitmapH  = (pillH + gap + outerR * 2f + tailH + pad).toInt()
    val cx       = bitmapW / 2f

    val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ── Draw label pill ───────────────────────────────────────────────────────
    val pillLeft = cx - pillW / 2f
    val pillBot  = pillH
    canvas.drawRoundRect(
        RectF(pillLeft, 0f, cx + pillW / 2f, pillBot),
        pillR, pillR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 255, 255, 255) }
    )
    canvas.drawText(name, cx - textW / 2f, padV - fm.ascent, textPaint)

    // ── Draw pin ──────────────────────────────────────────────────────────────
    val ballCy = pillBot + gap + outerR   // centre of the ball
    val tipY   = ballCy + outerR + tailH  // sharp tip at very bottom

    // Build the pin path: full ball circle + tail quadratics, with EVEN_ODD hole
    // The tail starts from the two bottom-quarter points on the circle:
    //   left:  cx - outerR*sin(50°), ballCy + outerR*cos(50°)
    //   right: cx + outerR*sin(50°), ballCy + outerR*cos(50°)
    val sinA = Math.sin(Math.toRadians(50.0)).toFloat()
    val cosA = Math.cos(Math.toRadians(50.0)).toFloat()
    val tx   = outerR * sinA   // horizontal offset of tail break-off
    val ty   = outerR * cosA   // downward offset of tail break-off from ball centre

    val pinPath = Path().apply {
        // Full ball circle (CCW)
        addCircle(cx, ballCy, outerR, Path.Direction.CCW)
        // Tail: triangle with slightly curved sides
        moveTo(cx - tx, ballCy + ty)
        quadTo(cx - tx * 0.3f, ballCy + ty + (tailH * 0.6f), cx, tipY)
        quadTo(cx + tx * 0.3f, ballCy + ty + (tailH * 0.6f), cx + tx, ballCy + ty)
        close()
        // Hole (CW = opposite winding → punches through with EVEN_ODD)
        fillType = Path.FillType.EVEN_ODD
    }
    pinPath.addCircle(cx, ballCy, holeR, Path.Direction.CW)

    // Shadow (offset copy, no hole needed)
    val shadowPath = Path().apply {
        addCircle(cx, ballCy, outerR, Path.Direction.CCW)
        moveTo(cx - tx, ballCy + ty)
        quadTo(cx - tx * 0.3f, ballCy + ty + (tailH * 0.6f), cx, tipY)
        quadTo(cx + tx * 0.3f, ballCy + ty + (tailH * 0.6f), cx + tx, ballCy + ty)
        close()
    }
    canvas.save()
    canvas.translate(0f, 1.5f * d)
    canvas.drawPath(shadowPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
        maskFilter = android.graphics.BlurMaskFilter(3f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    canvas.restore()

    // Pin body
    canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    })

    // Hole fill (draws over the transparent hole to give it a colour)
    canvas.drawCircle(cx, ballCy, holeR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = holeColor
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
