package com.goodyaoshi.parentsgames.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.compositionLocalOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sistema central de sons. Carrega assets do soundpool e toca por chave.
 *
 * No MVP, sem assets de áudio — métodos viram no-op. Para habilitar, adicione
 * arquivos .ogg em assets/sounds/ e descomente o registro em [loadAll].
 */
@Singleton
class SoundController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    private val soundIds = mutableMapOf<SoundEvent, Int>()
    var enabled: Boolean = true

    init { loadAll() }

    private fun loadAll() {
        // Quando tiver assets:
        // soundIds[SoundEvent.SNAP] = pool.load(context.assets.openFd("sounds/snap.ogg"), 1)
        // soundIds[SoundEvent.MATCH] = pool.load(context.assets.openFd("sounds/match.ogg"), 1)
        // soundIds[SoundEvent.WIN] = pool.load(context.assets.openFd("sounds/win.ogg"), 1)
        // soundIds[SoundEvent.TILT] = pool.load(context.assets.openFd("sounds/tilt.ogg"), 1)
    }

    fun play(event: SoundEvent, volume: Float = 0.6f) {
        if (!enabled) return
        val id = soundIds[event] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }
}

enum class SoundEvent { TICK, SNAP, MATCH, WIN, TILT }

val LocalSoundController = compositionLocalOf<SoundController> {
    error("SoundController not provided.")
}
