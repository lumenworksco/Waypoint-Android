package com.florian.waypoint.util

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

class HapticUtil(context: Context) {

    private val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
        .defaultVibrator

    fun success() {
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        )
    }

    fun light() {
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        )
    }

    fun warning() {
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        )
    }
}
