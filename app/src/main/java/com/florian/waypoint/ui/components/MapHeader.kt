package com.florian.waypoint.ui.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal frosted-style header matching iOS HeaderView:
 * - White pill with soft shadow
 * - Small coloured dot for GPS status
 * - Coordinates in compact monospaced-feel text
 */
@Composable
fun MapHeader(
    userLocation: Location?,
    locationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val coordText = if (userLocation != null) {
        // 4 decimal places — matches iOS "%.4f, %.4f"
        "${"%.4f".format(userLocation.latitude)},  ${"%.4f".format(userLocation.longitude)}"
    } else {
        "Locating…"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (locationEnabled) "Location: $coordText" else "Locating"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Small status dot — blue when active, grey when waiting
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (locationEnabled) Color(0xFF007AFF) else Color(0xFFAEAEB2)
                    )
            )
            Text(
                text = coordText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (locationEnabled) Color(0xFF1C1C1E) else Color(0xFF8E8E93),
                letterSpacing = 0.2.sp
            )
        }
    }
}
