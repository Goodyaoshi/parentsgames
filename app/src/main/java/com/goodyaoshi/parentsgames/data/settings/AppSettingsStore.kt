package com.goodyaoshi.parentsgames.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "fatima_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class FontScale(val factor: Float) { NORMAL(1.0f), LARGE(1.15f), XLARGE(1.30f) }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.NORMAL,
    val soundsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_FONT = stringPreferencesKey("font_scale")
        private val KEY_SOUND = booleanPreferencesKey("sounds")
        private val KEY_HAPTICS = booleanPreferencesKey("haptics")
        private val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        fun tutorialKey(name: String) = booleanPreferencesKey("tutorial_seen_$name")
    }

    fun tutorialSeenFlow(name: String): Flow<Boolean> = context.dataStore.data.map { it[tutorialKey(name)] ?: false }
    suspend fun markTutorialSeen(name: String) {
        context.dataStore.edit { it[tutorialKey(name)] = true }
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            fontScale = prefs[KEY_FONT]?.let { runCatching { FontScale.valueOf(it) }.getOrNull() } ?: FontScale.NORMAL,
            soundsEnabled = prefs[KEY_SOUND] ?: true,
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }
    suspend fun setFontScale(scale: FontScale) {
        context.dataStore.edit { it[KEY_FONT] = scale.name }
    }
    suspend fun setSounds(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND] = enabled }
    }
    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS] = enabled }
    }
    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REDUCE_MOTION] = enabled }
    }
}
