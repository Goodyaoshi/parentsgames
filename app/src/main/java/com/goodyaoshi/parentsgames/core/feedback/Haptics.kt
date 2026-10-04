package com.goodyaoshi.parentsgames.core.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sistema central de háptica. Aplica feedback respeitando configuração do usuário.
 */
@Singleton
class HapticController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var enabled: Boolean = true

    fun tick() {
        if (!enabled) return
        vibrate(VibrationEffect.createOneShot(15, 50))
    }

    fun snap() {
        if (!enabled) return
        vibrate(VibrationEffect.createOneShot(35, 120))
    }

    fun error() {
        if (!enabled) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), -1))
    }

    fun win() {
        if (!enabled) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 60), -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(effect)
    }
}

val LocalHapticController = compositionLocalOf<HapticController> {
    error("HapticController not provided. Wrap content with CompositionLocalProvider(LocalHapticController provides ...).")
}
