package dev.tpcoder.blindfriendlywearos.presentation.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class HapticService(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Quick tap for user action
    fun vibrateTap() {
        vibrate(50)
    }

    // Connected confirmation
    fun vibrateConnected() {
        val pattern = longArrayOf(0, 100, 100, 100)
        vibrate(pattern)
    }

    // Navigation patterns
    fun vibratePattern(patternType: Int) {
        Log.d("HapticService", "Vibrate pattern: $patternType")
        val pattern = when (patternType) {
            1 -> longArrayOf(0, 200) // Clear path - single long
            2 -> longArrayOf(0, 150, 100, 150) // Caution - two medium
            3 -> longArrayOf(0, 100, 50, 100, 50, 100) // Danger - three short
            else -> longArrayOf(0, 150, 100, 150) // Default caution
        }
        vibrate(pattern)
    }

    private fun vibrate(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}