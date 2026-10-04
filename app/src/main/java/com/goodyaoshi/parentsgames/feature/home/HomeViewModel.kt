package com.goodyaoshi.parentsgames.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ContinueGameInfo(
    val gameType: GameType,
    val title: String,
    val updatedAt: Long,
)

data class HomeUiState(
    val weekdayLabel: String = "",
    val continueGame: ContinueGameInfo? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameStateRepo: GameStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(weekdayLabel = computeGreeting()) }
        observeContinue()
    }

    private fun computeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "早上好，爸妈"
            in 12..17 -> "下午好，爸妈"
            in 18..23 -> "晚上好，爸妈"
            else -> "夜深了，爸妈早点休息"
        }
    }

    private fun observeContinue() {
        viewModelScope.launch {
            gameStateRepo.observeMostRecentSnapshot().collect { latest ->
                val info = latest?.let { (type, updatedAt) ->
                    ContinueGameInfo(
                        gameType = type,
                        title = type.label,
                        updatedAt = updatedAt,
                    )
                }
                _uiState.update { it.copy(continueGame = info) }
            }
        }
    }
}
