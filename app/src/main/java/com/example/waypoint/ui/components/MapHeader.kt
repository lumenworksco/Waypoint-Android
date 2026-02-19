package com.example.waypoint.ui.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun MapHeader(
    userLocation: Location?,
    locationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val statusText = if (userLocation != null) {
        val lat = "%.5f".format(userLocation.latitude)
        val lon = "%.5f".format(userLocation.longitude)
        "$lat, $lon"
    } else {
        "Locating…"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (locationEnabled) {
                        "Location ready. $statusText"
                    } else {
                        "Waiting for location"
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (locationEnabled) Icons.Filled.LocationOn else Icons.Outlined.LocationOff,
                contentDescription = null,
                tint = if (locationEnabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}
