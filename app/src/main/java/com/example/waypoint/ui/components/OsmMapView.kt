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
 * Draws a map annotation exactly matching the reference icon:
 *  - Red teardrop: full circle on top, sides curve smoothly into a ROUNDED tip
 *  - Dark-green hollow circle in the upper centre of the ball
 *  - White label pill above
 *
 * Shape detail: the teardrop is drawn as a single closed cubic-bezier path
 * (not a circle + triangle). The sides break off at 60° below the equator
 * and converge to a rounded tip. The hole is EVEN_ODD.
 */
private fun createPinWithLabel(
    context: Context,
    name: String,
    selected: Boolean
): BitmapDrawable {
    val d = context.resources.displayMetrics.density

    val pinColor  = if (selected) Color.rgb(230, 90, 60)  else Color.rgb(214, 64, 52)
    val holeColor = Color.rgb(83, 138, 83)

    // ── Pin geometry ─────────────────────────────────────────────────────────
    val R     = 13f * d    // ball radius
    val holeR =  4.5f * d  // hole radius (≈35% of R)
    val pad   =  4f * d    // shadow bleed

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

    // Tail height = 0.85 × R (short rounded tail matching icon)
    val tailH   = R * 0.85f
    val bitmapW = (maxOf(pillW, R * 2f) + pad * 2f).toInt()
    val bitmapH = (pillH + pillGap + R * 2f + tailH + pad).toInt()
    val cx      = bitmapW / 2f

    val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ── Pill ─────────────────────────────────────────────────────────────────
    val pillLeft = cx - pillW / 2f
    val pillBot  = pillH
    // shadow
    canvas.drawRoundRect(
        RectF(pillLeft, 0f + d * 0.8f, pillLeft + pillW, pillBot + d * 0.8f),
        pillCorner, pillCorner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
    )
    // body
    canvas.drawRoundRect(
        RectF(pillLeft, 0f, pillLeft + pillW, pillBot),
        pillCorner, pillCorner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 255, 255, 255) }
    )
    canvas.drawText(name, cx - textW / 2f, pillPV - fm.ascent, textPaint)

    // ── Teardrop path (EVEN_ODD) ──────────────────────────────────────────────
    val ballCy = pillBot + pillGap + R
    val tipY   = ballCy + R + tailH

    // Break-off at 60° below equator on each side:
    //   brkX = R·sin(60°) = R·0.866
    //   brkY = R·cos(60°) = R·0.5   (below ball centre)
    val brkX = R * 0.866f
    val brkY = R * 0.5f

    // Right break-off point in canvas angle:  90° + 60° = 150° from east (CW)
    // We draw the arc CCW from 150° with sweep −280° to reach left break-off.
    val tearPath = Path().apply {
        moveTo(cx + brkX, ballCy + brkY)
        arcTo(
            cx - R, ballCy - R, cx + R, ballCy + R,
            150f, -280f, false
        )
        // Left side → rounded tip (cubic bezier)
        cubicTo(
            cx - brkX,      ballCy + brkY + tailH * 0.6f,
            cx - R * 0.15f, tipY - R * 0.1f,
            cx,             tipY
        )
        // Right side ← rounded tip back up
        cubicTo(
            cx + R * 0.15f, tipY - R * 0.1f,
            cx + brkX,      ballCy + brkY + tailH * 0.6f,
            cx + brkX,      ballCy + brkY
        )
        close()
        fillType = Path.FillType.EVEN_ODD
    }
    // Hole (CW = punches through with EVEN_ODD)
    val holeCy = ballCy - R * 0.05f
    tearPath.addCircle(cx, holeCy, holeR, Path.Direction.CW)

    // ── Shadow ────────────────────────────────────────────────────────────────
    val shadowPath = Path().apply {
        moveTo(cx + brkX, ballCy + brkY)
        arcTo(cx - R, ballCy - R, cx + R, ballCy + R, 150f, -280f, false)
        cubicTo(cx - brkX, ballCy + brkY + tailH * 0.6f, cx - R * 0.15f, tipY - R * 0.1f, cx, tipY)
        cubicTo(cx + R * 0.15f, tipY - R * 0.1f, cx + brkX, ballCy + brkY + tailH * 0.6f, cx + brkX, ballCy + brkY)
        close()
    }
    canvas.save()
    canvas.translate(0f, 2f * d)
    canvas.drawPath(shadowPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(3.5f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    canvas.restore()

    // ── Pin body ──────────────────────────────────────────────────────────────
    canvas.drawPath(tearPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    })

    // ── Hole colour ───────────────────────────────────────────────────────────
    canvas.drawCircle(cx, holeCy, holeR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
