package com.example.waypoint

import android.app.Application
import org.osmdroid.config.Configuration

class WaypointApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName
    }
}
