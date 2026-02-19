package com.example.waypoint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Clean circular white button with a blue location icon — matches iOS style.
 * Fades to 50% opacity when GPS is unavailable, exactly like the iOS implementation.
 */
@Composable
fun RecenterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(50.dp)
            .shadow(
                elevation = if (enabled) 6.dp else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled) { onClick() }
            .semantics {
                contentDescription = if (enabled) "Center on my location" else "Location unavailable"
                role = Role.Button
            }
    ) {
        Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = null,
            tint = Color(0xFF007AFF).copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}
