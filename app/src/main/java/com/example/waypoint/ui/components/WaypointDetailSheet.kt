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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waypoint.data.model.WaypointModel
import com.example.waypoint.util.DistanceUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointDetailSheet(
    waypoint: WaypointModel,
    userLocation: Location?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (name: String, notes: String) -> Unit,
    onDelete: () -> Unit,
) {
    // skipPartiallyExpanded = true so sheet always opens fully — no half-peek showing map through
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editName by remember(waypoint.id) { mutableStateOf(waypoint.name) }
    var editNotes by remember(waypoint.id) { mutableStateOf(waypoint.notes) }

    val isSaveEnabled = editName.trim().isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = null,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 8.dp)
                .navigationBarsPadding()
        ) {
            if (isEditing) {
                // ── Edit mode ──────────────────────────────────────────────────────────────
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    placeholder = { Text("Name", color = Color(0xFF8E8E93)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF2F2F7),
                        focusedContainerColor = Color(0xFFF2F2F7),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    placeholder = { Text("Notes", color = Color(0xFF8E8E93)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF2F2F7),
                        focusedContainerColor = Color(0xFFF2F2F7),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isSaveEnabled) onSave(editName, editNotes) }
                    )
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancelEdit) {
                        Text("Cancel", color = Color(0xFF8E8E93), fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSaveEnabled) Color(0xFF007AFF) else Color(0xFFD1D1D6))
                            .clickable(enabled = isSaveEnabled) { onSave(editName, editNotes) }
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }

            } else {
                // ── View mode ──────────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = waypoint.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = Color.Black
                        )
                        userLocation?.let { loc ->
                            val metres = DistanceUtil.calculate(loc, waypoint.latitude, waypoint.longitude)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = DistanceUtil.format(metres),
                                fontSize = 13.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    // iOS-style circular icon buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircleIconButton(
                            onClick = onEdit,
                            backgroundColor = Color(0xFFEFEFF4),
                            iconColor = Color(0xFF3C3C43),
                            icon = Icons.Filled.Edit,
                            contentDescription = "Edit"
                        )
                        CircleIconButton(
                            onClick = onDelete,
                            backgroundColor = Color(0xFFFF3B30),
                            iconColor = Color.White,
                            icon = Icons.Filled.Delete,
                            contentDescription = "Delete"
                        )
                        CircleIconButton(
                            onClick = onDismiss,
                            backgroundColor = Color(0xFFEFEFF4),
                            iconColor = Color(0xFF3C3C43),
                            icon = Icons.Filled.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                if (waypoint.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = waypoint.notes,
                        fontSize = 15.sp,
                        color = Color(0xFF3C3C43)
                    )
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    icon: ImageVector,
    contentDescription: String,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
