package com.example.waypoint.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waypoint.data.model.WaypointModel
import com.example.waypoint.data.repository.WaypointRepository
import com.example.waypoint.location.LocationManagerWrapper
import com.example.waypoint.util.ValidationUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val waypoints: List<WaypointModel> = emptyList(),
    val userLocation: Location? = null,
    val locationEnabled: Boolean = false,
    val selectedWaypoint: WaypointModel? = null,
    val isEditingWaypoint: Boolean = false,
    val showHint: Boolean = true,
    val isRecenterRequested: Boolean = false,
    val hasCenteredOnUser: Boolean = false
)

class WaypointViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WaypointRepository(application)
    private val locationManager = LocationManagerWrapper(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.waypointsFlow.collect { saved ->
                _uiState.update { state ->
                    state.copy(
                        waypoints = saved,
                        showHint = saved.isEmpty()
                    )
                }
            }
        }
    }

    fun startLocationUpdates() {
        viewModelScope.launch {
            locationManager.locationFlow()
                .catch {
                    _uiState.update { it.copy(locationEnabled = false) }
                }
                .collect { location ->
                    _uiState.update { state ->
                        val shouldAutoCenter = !state.hasCenteredOnUser
                        state.copy(
                            userLocation = location,
                            locationEnabled = true,
                            isRecenterRequested = if (shouldAutoCenter) true else state.isRecenterRequested,
                            hasCenteredOnUser = true
                        )
                    }
                }
        }
    }

    fun addWaypoint(latitude: Double, longitude: Double) {
        val current = _uiState.value.waypoints
        val newWaypoint = WaypointModel(
            name = "Waypoint ${current.size + 1}",
            latitude = latitude,
            longitude = longitude
        )
        val updated = current + newWaypoint
        _uiState.update { it.copy(waypoints = updated, showHint = false) }
        persist(updated)
    }

    fun selectWaypoint(waypoint: WaypointModel?) {
        _uiState.update { it.copy(selectedWaypoint = waypoint, isEditingWaypoint = false) }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditingWaypoint = true) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditingWaypoint = false) }
    }

    fun saveEdit(id: String, name: String, notes: String) {
        val sanitizedName = ValidationUtil.sanitizeName(name)
        val updated = _uiState.value.waypoints.map { wp ->
            if (wp.id == id) wp.copy(name = sanitizedName, notes = notes.trim()) else wp
        }
        val updatedSelected = updated.find { it.id == id }
        _uiState.update { state ->
            state.copy(
                waypoints = updated,
                isEditingWaypoint = false,
                selectedWaypoint = updatedSelected
            )
        }
        persist(updated)
    }

    fun deleteWaypoint(id: String) {
        val updated = _uiState.value.waypoints.filterNot { it.id == id }
        _uiState.update { state ->
            state.copy(
                waypoints = updated,
                selectedWaypoint = null,
                isEditingWaypoint = false,
                showHint = updated.isEmpty()
            )
        }
        persist(updated)
    }

    fun requestRecenter() {
        _uiState.update { it.copy(isRecenterRequested = true) }
    }

    fun recenterHandled() {
        _uiState.update { it.copy(isRecenterRequested = false) }
    }

    private fun persist(waypoints: List<WaypointModel>) {
        viewModelScope.launch {
            repository.saveWaypoints(waypoints)
        }
    }
}
