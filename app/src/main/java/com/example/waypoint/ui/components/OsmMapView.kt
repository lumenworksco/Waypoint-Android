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
 * Draws a complete map annotation:
 *
 *   ┌──────────────┐   ← label pill (white, rounded)
 *   │  Waypoint 1  │
 *   └──────────────┘
 *       (  O  )        ← teardrop pin with hollow circle cutout
 *          ▼           ← tip = anchor point (geo-coordinate)
 *
 * Pin shape matches the reference image: a filled teardrop with a circular
 * hole punched through the ball head, drawn with Path.FillType.EVEN_ODD.
 *
 * The bitmap anchor is ANCHOR_CENTER / ANCHOR_BOTTOM so OSMDroid places
 * the very bottom pixel (the pin tip) exactly on the geo-coordinate.
 */
private fun createPinWithLabel(
    context: Context,
    name: String,
    selected: Boolean
): BitmapDrawable {
    val d = context.resources.displayMetrics.density

    val pinColor  = if (selected) Color.rgb(255, 107, 43) else Color.rgb(255, 59, 48)

    // ── Pin dimensions ───────────────────────────────────────────────────────
    val outerR    = 12f * d     // outer ball radius
    val holeR     = 5f  * d     // inner hole radius (≈40% of outerR, matching reference)
    // The tail narrows from the ball tangent breakaway down to a sharp tip.
    // We use bezier curves so the join to the ball is perfectly smooth.
    val tailH     = 16f * d     // total tail height below ball centre
    val shadowBleed = 4f * d    // extra space around pin for drop-shadow

    // ── Label pill dimensions ────────────────────────────────────────────────
    val textSize  = 11f * d
    val padH      = 5f * d
    val padV      = 3f * d
    val pillR     = 6f * d
    val connGap   = 3f * d      // vertical gap between pill bottom and pin top

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface      = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color         = Color.rgb(28, 28, 30)
    }
    val textW = textPaint.measureText(name)
    val fm    = textPaint.fontMetrics
    val pillW = textW + padH * 2
    val pillH = (fm.descent - fm.ascent) + padV * 2

    // ── Bitmap size ──────────────────────────────────────────────────────────
    val pinW     = (outerR * 2 + shadowBleed * 2)
    val bitmapW  = maxOf(pillW + shadowBleed * 2, pinW).toInt()
    val bitmapH  = (pillH + connGap + outerR * 2 + tailH + shadowBleed).toInt()
    val cx       = bitmapW / 2f

    val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ── Label pill ───────────────────────────────────────────────────────────
    val pillLeft = cx - pillW / 2f
    val pillTop  = 0f
    val pillBot  = pillH

    // Pill drop-shadow
    canvas.drawRoundRect(
        RectF(pillLeft, pillTop + d, cx + pillW / 2f, pillBot + d),
        pillR, pillR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 0, 0, 0)
            setShadowLayer(3f * d, 0f, 1.5f * d, Color.argb(35, 0, 0, 0))
        }
    )
    // Pill fill
    canvas.drawRoundRect(
        RectF(pillLeft, pillTop, cx + pillW / 2f, pillBot),
        pillR, pillR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 255, 255, 255) }
    )
    // Pill text
    canvas.drawText(name, cx - textW / 2f, pillTop + padV - fm.ascent, textPaint)

    // ── Pin: teardrop with EVEN_ODD hole ─────────────────────────────────────
    // Ball centre sits just below the pill
    val ballCy = pillBot + connGap + outerR
    // Tip at the very bottom of the bitmap (minus shadow bleed)
    val tipY   = ballCy + outerR + tailH

    // The teardrop tail breaks away at ±60° from the bottom of the ball.
    // In canvas angles (0° = east, CW):
    //   bottom of ball = 90°  →  right break = 90°+60° = 150°  (below-right)
    //                          →  left  break = 90°-60° =  30°  — NO, we go the other way:
    // Right break-off: angle 150° from east  → x = cx + outerR·cos(150°), y = ballCy + outerR·sin(150°)
    //   cos(150°) = -√3/2 ≈ -0.866,  sin(150°) = 0.5   → that's on the LEFT side.
    // We want the break to be symmetric left/right, below centre.
    // Simpler: measure the break angle φ from the bottom (6-o'clock):
    //   φ = 55° either side.  In canvas angles from east: right = 90+55=145°, left = 90-55=35°.
    //   Right point: cx + outerR·cos(145°), ballCy + outerR·sin(145°)
    //               = cx - outerR·sin(55°), ballCy + outerR·cos(55°)
    val phi       = Math.toRadians(55.0)
    val bx = (outerR * Math.sin(phi)).toFloat()   // horizontal offset (sin because from vertical)
    val by = (outerR * Math.cos(phi)).toFloat()   // downward offset from ball centre

    // Arc: start at right break-off (canvas angle = 90+55 = 145°),
    //      sweep CCW (negative) 360-110 = 250° to left break-off (canvas angle = 90-55 = 35°)
    val arcStart  = 90f + 55f   // 145° — right break-off
    val arcSweep  = -(360f - 110f)  // -250° CCW

    // Outer teardrop + tail as a single path (EVEN_ODD fill rule)
    val outerPath = Path().apply {
        moveTo(cx + bx, ballCy + by)          // right break-off
        arcTo(
            cx - outerR, ballCy - outerR,
            cx + outerR, ballCy + outerR,
            arcStart, arcSweep, false
        )                                       // sweeps CCW over the top to left break-off
        // Left bezier down to tip
        quadTo(cx - bx * 0.25f, tipY - tailH * 0.4f, cx, tipY)
        // Right bezier back up to start
        quadTo(cx + bx * 0.25f, tipY - tailH * 0.4f, cx + bx, ballCy + by)
        close()
        fillType = Path.FillType.EVEN_ODD
    }

    // Inner hole circle (opposite winding = CW) at ball centre, slightly above centre
    val holeCy = ballCy - outerR * 0.08f   // nudge hole slightly upward for visual balance
    outerPath.addCircle(cx, holeCy, holeR, Path.Direction.CW)

    // Drop shadow — draw a slightly offset solid version first
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
        setShadowLayer(3f * d, 0f, 2f * d, Color.argb(55, 0, 0, 0))
    }
    canvas.save()
    canvas.translate(0f, 1.5f * d)
    canvas.drawPath(outerPath, shadowPaint)
    canvas.restore()

    // Pin fill
    canvas.drawPath(outerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
