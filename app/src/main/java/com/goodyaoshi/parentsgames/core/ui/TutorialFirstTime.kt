package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.data.settings.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TutorialPreferenceVm @Inject constructor(
    private val store: AppSettingsStore,
) : ViewModel() {
    suspend fun seen(name: String): Boolean = store.tutorialSeenFlow(name).first()
    fun markSeen(name: String) { viewModelScope.launch { store.markTutorialSeen(name) } }
}

/**
 * Mostra o overlay de tutorial só se o usuário ainda não viu este (registrado em DataStore).
 *
 * Uso: chame dentro de cada GameScreen depois do conteúdo principal.
 *   TutorialFirstTime(name = "jigsaw", steps = TutorialContent.jigsaw)
 */
@Composable
fun TutorialFirstTime(
    name: String,
    steps: List<TutorialStep>,
    viewModel: TutorialPreferenceVm = hiltViewModel(),
) {
    var shouldShow by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(name) {
        shouldShow = !viewModel.seen(name)
    }
    if (shouldShow == true) {
        TutorialDialog(
            steps = steps,
            onDismiss = {
                viewModel.markSeen(name)
                shouldShow = false
            },
        )
    }
}
