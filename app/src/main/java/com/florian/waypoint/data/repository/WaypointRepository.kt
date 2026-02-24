package com.florian.waypoint.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.florian.waypoint.data.model.WaypointModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "waypoints")

class WaypointRepository(private val context: Context) {

    private val waypointsKey = stringPreferencesKey("waypoints_json")

    private val json = Json { ignoreUnknownKeys = true }

    val waypointsFlow: Flow<List<WaypointModel>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[waypointsKey] ?: return@map emptyList()
            runCatching {
                json.decodeFromString<List<WaypointModel>>(raw)
            }.getOrElse { emptyList() }
        }

    suspend fun saveWaypoints(waypoints: List<WaypointModel>) {
        context.dataStore.edit { prefs ->
            prefs[waypointsKey] = json.encodeToString(waypoints)
        }
    }
}
