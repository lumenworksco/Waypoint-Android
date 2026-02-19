package com.example.waypoint.util

import android.location.Location

object DistanceUtil {

    fun calculate(from: Location, toLat: Double, toLon: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, toLat, toLon, results)
        return results[0]
    }

    fun format(metres: Float): String = when {
        metres < 1000f -> "%.0f m away".format(metres)
        else -> "%.1f km away".format(metres / 1000f)
    }
}
