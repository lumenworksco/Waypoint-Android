package com.example.waypoint.util

object ValidationUtil {
    fun sanitizeName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank()) "Unnamed Waypoint" else trimmed.take(64)
    }
}
