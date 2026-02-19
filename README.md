# Waypoint — Android

An Android port of the iOS Waypoint app. A minimal, single-screen waypoint manager built on OpenStreetMap.

## Features

- Drop waypoints with a long press anywhere on the map
- View, edit, and delete waypoints via an inline card
- Live GPS location tracking with auto-center on first fix
- Persistent storage — waypoints survive app restarts
- Haptic feedback on interactions

## Tech Stack

| Layer | Library |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Map | OSMDroid 6.1.18 (OpenStreetMap / MAPNIK) |
| Architecture | MVVM (single ViewModel) |
| Persistence | DataStore + kotlinx.serialization |
| Location | FusedLocationProviderClient |
| Min SDK | 31 (Android 12) |
| Target SDK | 35 |

## Getting Started

1. Clone the repo
2. Open in Android Studio Hedgehog or later
3. Run on a device or emulator with API 31+

No API keys required — map tiles are served by OpenStreetMap.

## Permissions

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS location for map centering |
| `ACCESS_COARSE_LOCATION` | Fallback network location |
| `INTERNET` | Fetching map tiles |
| `ACCESS_NETWORK_STATE` | OSMDroid tile availability check |
| `VIBRATE` | Haptic feedback |
