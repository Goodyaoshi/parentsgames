package com.goodyaoshi.parentsgames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.goodyaoshi.parentsgames.core.feedback.HapticController
import com.goodyaoshi.parentsgames.core.feedback.LocalHapticController
import com.goodyaoshi.parentsgames.core.feedback.LocalSoundController
import com.goodyaoshi.parentsgames.core.feedback.SoundController
import com.goodyaoshi.parentsgames.core.navigation.AppNavGraph
import com.goodyaoshi.parentsgames.core.theme.FatimaGamesTheme
import com.goodyaoshi.parentsgames.data.settings.AppSettingsStore
import com.goodyaoshi.parentsgames.data.settings.FontScale
import com.goodyaoshi.parentsgames.data.settings.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: AppSettingsStore
    @Inject lateinit var haptic: HapticController
    @Inject lateinit var sound: SoundController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsStore.flow.collectAsState(initial = com.goodyaoshi.parentsgames.data.settings.AppSettings())
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            // Aplica configurações dinâmicas
            haptic.enabled = settings.hapticsEnabled
            sound.enabled = settings.soundsEnabled

            // Escala de fonte interna multiplicando densidade
            val currentDensity = LocalDensity.current
            val scaledDensity = androidx.compose.ui.unit.Density(
                density = currentDensity.density,
                fontScale = currentDensity.fontScale * settings.fontScale.factor,
            )

            CompositionLocalProvider(
                LocalDensity provides scaledDensity,
                LocalHapticController provides haptic,
                LocalSoundController provides sound,
            ) {
                FatimaGamesTheme(
                    darkTheme = isDark,
                    reduceMotion = settings.reduceMotion,
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
