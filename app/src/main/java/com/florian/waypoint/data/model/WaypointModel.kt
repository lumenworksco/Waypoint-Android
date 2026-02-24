package com.florian.waypoint.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WaypointModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
