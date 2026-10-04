package com.goodyaoshi.parentsgames.feature.games.jigsaw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawBoard
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawBoardBuilder
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawEngine
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawSession
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.JigsawSnapshot
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.PhotoLoader
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.PieceSnap
import com.goodyaoshi.parentsgames.feature.games.jigsaw.domain.PieceState
import com.goodyaoshi.parentsgames.core.feedback.HapticController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface JigsawUiState {
    data object Loading : JigsawUiState
    data class Playing(
        val board: JigsawBoard,
        val states: List<PieceState>,
        val elapsedMs: Long,
        val completed: Boolean,
        val justSnappedPieceIds: Set<Int> = emptySet(),
    ) : JigsawUiState
    data class Error(val message: String) : JigsawUiState
}

@HiltViewModel
class JigsawViewModel @Inject constructor(
    private val photoLoader: PhotoLoader,
    private val session: JigsawSession,
    private val recordRepo: RecordRepository,
    private val stateRepo: GameStateRepository,
    private val haptic: HapticController,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private var saveJob: Job? = null

    private val _uiState = MutableStateFlow<JigsawUiState>(JigsawUiState.Loading)
    val uiState: StateFlow<JigsawUiState> = _uiState.asStateFlow()

    private var engine: JigsawEngine? = null
    private var startTime = 0L
    private var pieceCount: Int = 24
    private var photoId: Long = 0L

    fun initialize(photoId: Long, pieceCount: Int, viewportW: Int, viewportH: Int) {
        if (viewportW <= 0 || viewportH <= 0) return
        if (engine != null) return
        this.photoId = photoId
        this.pieceCount = pieceCount
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val uri = session.pendingPhotoUri
                photoLoader.loadFromUri(uri) ?: photoLoader.generateDefaultLandscape()
            }
            // 尝试恢复存档：版本/照片/难度/屏幕尺寸一致才认，否则安全弃档开新局
            val saved = runCatching {
                stateRepo.loadSnapshot(GameType.JIGSAW)?.let {
                    json.decodeFromString<JigsawSnapshot>(it)
                }
            }.getOrNull()?.takeIf {
                it.version == JigsawSnapshot.CURRENT_VERSION &&
                    it.photoId == photoId &&
                    it.pieceCount == pieceCount &&
                    it.viewportW == viewportW &&
                    it.viewportH == viewportH &&
                    it.pieces.isNotEmpty()
            }

            val board = withContext(Dispatchers.Default) {
                JigsawBoardBuilder.build(
                    sourceBitmap = bitmap,
                    targetPieces = pieceCount,
                    viewportWidthPx = viewportW,
                    viewportHeightPx = viewportH,
                    seed = saved?.seed ?: System.currentTimeMillis(),
                )
            }

            var restoredStates: List<PieceState>? = null
            if (saved != null && saved.pieces.size == board.pieces.size) {
                val byId = board.pieces.associateBy { it.id }
                val mapped = saved.pieces.mapNotNull { snap ->
                    byId[snap.pieceId]?.let {
                        // 碎片状态 = 未吸附的当前位置 + 已吸附标记
                        PieceState(
                            pieceId = snap.pieceId,
                            xPx = snap.xPx,
                            yPx = snap.yPx,
                            placed = snap.placed,
                        )
                    }
                }
                if (mapped.size == board.pieces.size && !mapped.all { st -> st.placed }) {
                    restoredStates = mapped
                }
            }

            if (restoredStates != null && saved != null) {
                engine = JigsawEngine(board, restoredStates)
                startTime = System.currentTimeMillis() - saved.elapsedMs
            } else {
                // 旧快照不兼容（版本/尺寸/难度不一致或已损坏）→ 弃档，开新局
                if (saved != null) stateRepo.clearSnapshot(GameType.JIGSAW)
                engine = JigsawEngine(board)
                startTime = System.currentTimeMillis()
            }

            _uiState.value = JigsawUiState.Playing(
                board = board,
                states = engine!!.states,
                elapsedMs = System.currentTimeMillis() - startTime,
                completed = false,
            )
        }
    }

    fun onPieceDrag(pieceId: Int, dx: Float, dy: Float) {
        val eng = engine ?: return
        eng.moveBy(pieceId, dx, dy)
        publishPlaying(completed = false)
        scheduleSave()
    }

    fun onPieceReleased(pieceId: Int) {
        val eng = engine ?: return
        val placedNow = eng.tryPlace(pieceId)
        val done = eng.isComplete()
        val cur = (_uiState.value as? JigsawUiState.Playing) ?: return
        _uiState.value = cur.copy(
            states = eng.states,
            elapsedMs = System.currentTimeMillis() - startTime,
            completed = done,
            justSnappedPieceIds = if (placedNow) setOf(pieceId) else emptySet(),
        )
        if (placedNow) {
            haptic.snap()
            scheduleSave()
        }
        if (done) {
            haptic.win()
            finishGame()
        }
    }

    private fun publishPlaying(completed: Boolean) {
        val eng = engine ?: return
        val cur = _uiState.value as? JigsawUiState.Playing ?: return
        _uiState.value = cur.copy(
            states = eng.states,
            elapsedMs = System.currentTimeMillis() - startTime,
            completed = completed,
            justSnappedPieceIds = emptySet(),
        )
    }

    /** 防抖保存：拖动/入位后 800ms 内没有新动作才落盘。 */
    private fun scheduleSave() {
        if (engine?.isComplete() == true) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            val eng = engine ?: return@launch
            val snap = JigsawSnapshot(
                version = JigsawSnapshot.CURRENT_VERSION,
                photoId = photoId,
                pieceCount = pieceCount,
                seed = eng.board.seed,
                viewportW = eng.board.viewportWidthPx,
                viewportH = eng.board.viewportHeightPx,
                elapsedMs = System.currentTimeMillis() - startTime,
                pieces = eng.states.map { PieceSnap(it.pieceId, it.xPx, it.yPx, it.placed) },
            )
            stateRepo.saveSnapshot(GameType.JIGSAW, json.encodeToString(snap))
        }
    }

    private fun finishGame() {
        saveJob?.cancel()
        viewModelScope.launch {
            val duration = System.currentTimeMillis() - startTime
            recordRepo.save(
                GameRecord(
                    id = 0,
                    gameType = GameType.JIGSAW,
                    score = null,
                    durationMs = duration,
                    difficulty = "${pieceCount}P",
                    finishedAt = System.currentTimeMillis(),
                )
            )
            stateRepo.clearSnapshot(GameType.JIGSAW)
        }
    }
}
