package com.goodyaoshi.parentsgames.feature.games.jigsaw.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carregador transitório de "intent" entre a tela de setup do Jigsaw
 * e a tela de jogo. Mantém o URI da foto escolhida em memória —
 * suficiente porque o usuário sempre vai do setup → game na sequência.
 *
 * Por que não passar pela navegação: URIs `content://` precisam de
 * URL-encoding (e podem ter caracteres especiais que quebram navigation),
 * e teríamos que persistir permission. Holder em memória é mais simples.
 */
@Singleton
class JigsawSession @Inject constructor() {
    @Volatile
    var pendingPhotoUri: String? = null

    fun consume(): String? {
        val u = pendingPhotoUri
        pendingPhotoUri = null
        return u
    }
}
