package com.example.waypoint.ui.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waypoint.data.model.WaypointModel
import com.example.waypoint.util.DistanceUtil

/**
 * iOS-style inline waypoint detail card.
 * Sits in the bottom-left corner of the map, next to the recenter button —
 * exactly like the iOS WaypointDetailCard layout in ContentView.
 */
@Composable
fun WaypointDetailCard(
    waypoint: WaypointModel,
    userLocation: Location?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (name: String, notes: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember(waypoint.id) { mutableStateOf(false) }
    var editName  by remember(waypoint.id) { mutableStateOf(waypoint.name) }
    var editNotes by remember(waypoint.id) { mutableStateOf(waypoint.notes) }
    val isSaveEnabled = editName.trim().isNotBlank()

    Box(
        modifier = modifier
            .widthIn(min = 240.dp, max = 280.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.97f))
            .padding(12.dp)
    ) {
            if (isEditing) {
                // ── Edit mode ─────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        placeholder = { Text("Name", color = Color(0xFF8E8E93), fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            focusedContainerColor   = Color(0xFFF2F2F7),
                            unfocusedBorderColor    = Color.Transparent,
                            focusedBorderColor      = Color.Transparent,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        placeholder = { Text("Notes", color = Color(0xFF8E8E93), fontSize = 13.sp) },
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            focusedContainerColor   = Color(0xFFF2F2F7),
                            unfocusedBorderColor    = Color.Transparent,
                            focusedBorderColor      = Color.Transparent,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (isSaveEnabled) { isEditing = false; onSave(editName, editNotes) } }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel", color = Color(0xFF8E8E93), fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSaveEnabled) Color(0xFF007AFF) else Color(0xFFD1D1D6)
                                )
                                .clickable(enabled = isSaveEnabled) {
                                    isEditing = false
                                    onSave(editName, editNotes)
                                }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "Save",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else {
                // ── View mode ─────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Text info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = waypoint.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFF1C1C1E),
                        )
                        if (waypoint.notes.isNotBlank()) {
                            Text(
                                text = waypoint.notes,
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93),
                                maxLines = 1,
                            )
                        }
                        userLocation?.let { loc ->
                            val metres = DistanceUtil.calculate(loc, waypoint.latitude, waypoint.longitude)
                            Text(
                                text = DistanceUtil.format(metres),
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93),
                            )
                        }
                    }
                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CardIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            backgroundColor = Color(0xFFEFEFF4),
                            iconColor = Color(0xFF3C3C43),
                            onClick = {
                                editName = waypoint.name
                                editNotes = waypoint.notes
                                isEditing = true
                            },
                        )
                        CardIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            backgroundColor = Color(0xFFFF3B30),
                            iconColor = Color.White,
                            onClick = onDelete,
                        )
                        CardIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Close",
                            backgroundColor = Color(0xFFEFEFF4),
                            iconColor = Color(0xFF3C3C43),
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
}

@Composable
private fun CardIconButton(
    icon: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(15.dp),
        )
    }
}
