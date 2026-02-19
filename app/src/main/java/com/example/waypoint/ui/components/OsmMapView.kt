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
 * Draws a map pin + label that matches logo.png exactly:
 *
 *   • Single continuous path for the teardrop:
 *       top = full semicircle arc (180°) left→right across the top
 *       right side = cubic bezier curving inward to sharp tip
 *       left  side = cubic bezier curving inward back to start
 *   • White circle punched through upper-centre of pin (EVEN_ODD)
 *   • White label pill floating above
 *
 * Pin proportions from logo.png:
 *   total height ≈ 1.55 × width
 *   hole diameter ≈ 0.38 × pin width, centred at ≈ 38% down from top
 */
private fun createPinWithLabel(
    context: Context,
    name: String,
    selected: Boolean
): BitmapDrawable {
    val d = context.resources.displayMetrics.density

    // Colours — charcoal when default, orange when selected (keeps good contrast on map)
    val pinColor  = if (selected) Color.rgb(220, 80, 40) else Color.rgb(61, 57, 53)
    val holeColor = Color.WHITE

    // ── Pin size ──────────────────────────────────────────────────────────────
    // W = full pin width = 2× the ball radius
    val W     = 26f * d          // pin width in px
    val H     = W * 1.55f        // total pin height (matches logo proportions)
    val holeR = W * 0.19f        // hole radius ≈ 38% of width / 2
    val pad   =  4f * d          // shadow bleed around pin

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

    // ── Bitmap ────────────────────────────────────────────────────────────────
    val bitmapW = (maxOf(pillW, W) + pad * 2f).toInt()
    val bitmapH = (pillH + pillGap + H + pad).toInt()
    val cx      = bitmapW / 2f           // horizontal centre
    val pinTop  = pillH + pillGap        // y where pin starts (top of arc)
    val pinBot  = pinTop + H             // y of the sharp tip

    val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ── Pill ─────────────────────────────────────────────────────────────────
    val pillLeft = cx - pillW / 2f
    canvas.drawRoundRect(                                    // shadow
        RectF(pillLeft, d * 0.8f, pillLeft + pillW, pillH + d * 0.8f),
        pillCorner, pillCorner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
    )
    canvas.drawRoundRect(                                    // body
        RectF(pillLeft, 0f, pillLeft + pillW, pillH),
        pillCorner, pillCorner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 255, 255, 255) }
    )
    canvas.drawText(name, cx - textW / 2f, pillPV - fm.ascent, textPaint)

    // ── Teardrop path ─────────────────────────────────────────────────────────
    // The pin is W wide.  The semicircle sits at the top:
    //   centre of circle = (cx, pinTop + W/2),  radius = W/2
    // The tip is at (cx, pinBot).
    // The sides leave the circle at its leftmost/rightmost equator points
    //   i.e. (cx - W/2, pinTop + W/2) and (cx + W/2, pinTop + W/2)
    // and curve inward to the tip using cubic beziers.
    //
    // Control points tuned to match logo.png:
    //   Each side: cp1 is straight down from the equator point,
    //              cp2 is close to the tip, pulled slightly outward.
    val R      = W / 2f
    val circCy = pinTop + R          // circle centre y
    // equator points (where sides start):
    val eqY    = circCy              // = pinTop + R
    val leftX  = cx - R
    val rightX = cx + R

    // tail length = H - R (distance from equator to tip)
    val tailLen = H - R

    val pinPath = Path().apply {
        // Start at left equator, arc CCW (negative sweep) over the top to right equator
        moveTo(leftX, eqY)
        arcTo(cx - R, pinTop, cx + R, pinTop + W, 180f, -180f, false)
        // now at (rightX, eqY) — right equator
        // Right cubic → tip
        cubicTo(
            rightX,        eqY + tailLen * 0.50f,   // cp1: straight down
            cx + R * 0.18f, pinBot - tailLen * 0.12f, // cp2: near tip, slightly right
            cx,            pinBot                    // tip
        )
        // Left cubic ← tip back to start
        cubicTo(
            cx - R * 0.18f, pinBot - tailLen * 0.12f, // cp1: near tip, slightly left
            leftX,          eqY + tailLen * 0.50f,    // cp2: straight up
            leftX,          eqY                       // back to start
        )
        close()
        fillType = Path.FillType.EVEN_ODD
    }

    // Hole centre: 38% down from top of pin = pinTop + H*0.38
    // but must stay within the circle: clamp to circCy
    val holeCy = pinTop + H * 0.36f
    pinPath.addCircle(cx, holeCy, holeR, Path.Direction.CW)  // CW = EVEN_ODD hole

    // ── Shadow ────────────────────────────────────────────────────────────────
    val shadowPath = Path().apply {
        moveTo(leftX, eqY)
        arcTo(cx - R, pinTop, cx + R, pinTop + W, 180f, -180f, false)
        cubicTo(rightX, eqY + tailLen * 0.50f, cx + R * 0.18f, pinBot - tailLen * 0.12f, cx, pinBot)
        cubicTo(cx - R * 0.18f, pinBot - tailLen * 0.12f, leftX, eqY + tailLen * 0.50f, leftX, eqY)
        close()
    }
    canvas.save()
    canvas.translate(0f, 2f * d)
    canvas.drawPath(shadowPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(4f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    canvas.restore()

    // ── Pin body ──────────────────────────────────────────────────────────────
    canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    })

    // ── Hole (white circle over the EVEN_ODD transparent region) ─────────────
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
