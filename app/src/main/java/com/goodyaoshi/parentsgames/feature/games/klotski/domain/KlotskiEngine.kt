package com.goodyaoshi.parentsgames.feature.games.klotski.domain

import kotlin.math.abs
import kotlinx.serialization.Serializable

/** 棋盘宽 4 列、高 5 行；出口在底部中间两列。 */
object KlotskiBoard {
    const val COLS = 4
    const val ROWS = 5
    /** 曹操左上角到 (x=1, y=3) 时，底边正好贴住底部中间两列的出口。 */
    const val EXIT_X = 1
}

enum class KlotskiPieceType(val w: Int, val h: Int, val label: String) {
    CAO(2, 2, "曹"),
    V_GENERAL(1, 2, "将"),
    H_GENERAL(2, 1, "将"),
    SOLDIER(1, 1, "兵"),
}

@Serializable
data class KlotskiPiece(val id: Int, val type: KlotskiPieceType, val x: Int, val y: Int)

@Serializable
data class KlotskiSnapshot(
    val layoutId: String,
    val pieces: List<KlotskiPiece>,
    val moves: Int,
    val finished: Boolean = false,
)

/**
 * 九个开局，由易到难：
 *  - 过五关：曹操在第三、四行，离出口只差两步，适合第一次玩；
 *  - 横刀立马：最经典的布局（四竖将 + 一横将 + 四兵）；
 *  - 兵临城下：横刀立马的变局，四兵垫底、横将压阵，中间留一线通道；
 *  - 捷足先登：四竖将一字排开守住出口，曹操贴顶，公认最快通关（约三十多步）；
 *  - 一路顺风：右侧留出一条纵向通道，顺着走就能把曹操送出去；
 *  - 雨声淅沥：竖将斜插在曹操侧下方，空格像雨点一样零散；
 *  - 齐头并进：四兵一字横排拦腰把守，下方横将压阵；
 *  - 围而不歼：三面围死、只留四角活眼，考验借空周转；
 *  - 指挥若定：横将坐镇中路、两兵护驾，四竖将分守四角。
 *
 * 均为经典开局，坐标已逐局验证：不越界、不重叠、恰好留 2 个空格。
 */
enum class KlotskiLayout(val id: String, val label: String, val pieces: List<KlotskiPiece>) {
    GUO_WU_GUAN(
        "GUO_WU_GUAN", "过五关",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 2),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 3, 0),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 0, 2),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 3, 2),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 0),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 1, 4),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 2, 4),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 1, 1),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 2, 1),
        ),
    ),
    HENG_DAO_LI_MA(
        "HENG_DAO_LI_MA", "横刀立马",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 3, 0),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 0, 2),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 3, 2),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 2),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 1, 3),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 2, 3),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 1, 4),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 2, 4),
        ),
    ),
    BING_LIN_CHENG_XIA(
        "BING_LIN_CHENG_XIA", "兵临城下",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 3, 0),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 0, 2),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 3, 2),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 3),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 0, 4),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 1, 4),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 2, 4),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 3, 4),
        ),
    ),
    JIE_ZU_XIAN_DENG(
        "JIE_ZU_XIAN_DENG", "捷足先登",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 3),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 1, 3),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 2, 3),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 3, 3),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 2),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 0, 0),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 3, 0),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 0, 1),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 3, 1),
        ),
    ),
    YI_LU_SHUN_FENG(
        "YI_LU_SHUN_FENG", "一路顺风",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 0, 2),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 3, 2),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 2, 3),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 2),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 3, 0),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 3, 1),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 1, 3),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 1, 4),
        ),
    ),
    YU_SHENG_XI_LI(
        "YU_SHENG_XI_LI", "雨声淅沥",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 0, 2),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 3, 2),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 1, 3),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 2),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 3, 0),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 3, 1),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 0, 4),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 3, 4),
        ),
    ),
    QI_TOU_BING_JIN(
        "QI_TOU_BING_JIN", "齐头并进",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 3, 0),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 0, 3),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 3, 3),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 3),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 0, 2),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 1, 2),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 2, 2),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 3, 2),
        ),
    ),
    WEI_ER_BU_JIAN(
        "WEI_ER_BU_JIAN", "围而不歼",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 0, 2),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 1, 3),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 2, 3),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 2),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 3, 0),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 3, 1),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 3, 2),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 3, 3),
        ),
    ),
    ZHI_HUI_RUO_DING(
        "ZHI_HUI_RUO_DING", "指挥若定",
        listOf(
            KlotskiPiece(1, KlotskiPieceType.CAO, 1, 0),
            KlotskiPiece(2, KlotskiPieceType.V_GENERAL, 0, 0),
            KlotskiPiece(3, KlotskiPieceType.V_GENERAL, 3, 0),
            KlotskiPiece(4, KlotskiPieceType.V_GENERAL, 0, 3),
            KlotskiPiece(5, KlotskiPieceType.V_GENERAL, 3, 3),
            KlotskiPiece(6, KlotskiPieceType.H_GENERAL, 1, 2),
            KlotskiPiece(7, KlotskiPieceType.SOLDIER, 0, 2),
            KlotskiPiece(8, KlotskiPieceType.SOLDIER, 3, 2),
            KlotskiPiece(9, KlotskiPieceType.SOLDIER, 1, 3),
            KlotskiPiece(10, KlotskiPieceType.SOLDIER, 2, 3),
        ),
    ),
}

class KlotskiEngine(val layout: KlotskiLayout) {

    private val pieces = layout.pieces.toMutableList()
    private val history = ArrayDeque<List<KlotskiPiece>>()

    var moves = 0
        private set
    var selectedId: Int? = null
        private set
    var finished = false
        private set

    val pieceList: List<KlotskiPiece> get() = pieces.toList()

    fun pieceById(id: Int): KlotskiPiece? = pieces.firstOrNull { it.id == id }

    fun pieceAt(x: Int, y: Int): KlotskiPiece? = pieces.firstOrNull { occupies(it, x, y) }

    fun selectPiece(id: Int) {
        if (finished) return
        selectedId = if (selectedId == id) null else id
    }

    /**
     * 点一个格子：点到块上就改选那块；点到空白处，就让选中块
     * 沿横或竖方向一步滑过去（只要路径上没有别的块挡着）。
     * 返回局面是否发生变化。
     */
    fun tapCell(x: Int, y: Int): Boolean {
        if (finished) return false
        val clicked = pieceAt(x, y)
        if (clicked != null) {
            selectPiece(clicked.id)
            return true
        }
        val selId = selectedId ?: return false
        val piece = pieceById(selId) ?: return false
        val dest = slideDestination(piece, x, y) ?: return false
        history.addLast(pieceList)
        pieces[pieces.indexOfFirst { it.id == piece.id }] = piece.copy(x = dest.first, y = dest.second)
        selectedId = null
        moves++
        finished = checkWon()
        return true
    }

    /** 悔棋：退回上一步（步数同步减一）。 */
    fun undo(): Boolean {
        if (finished) return false
        val last = history.removeLastOrNull() ?: return false
        pieces.clear()
        pieces.addAll(last)
        if (moves > 0) moves--
        selectedId = null
        return true
    }

    fun toSnapshot() = KlotskiSnapshot(layout.id, pieceList, moves, finished)

    /** 校验并恢复快照；快照损坏（越界/重叠/缺曹操）时返回 false。 */
    fun loadFromSnapshot(snap: KlotskiSnapshot): Boolean {
        val list = snap.pieces
        if (list.isEmpty() || list.size > KlotskiBoard.COLS * KlotskiBoard.ROWS) return false
        if (snap.moves < 0) return false
        if (list.count { it.type == KlotskiPieceType.CAO } != 1) return false
        val seenIds = HashSet<Int>()
        val cells = HashSet<Int>()
        for (p in list) {
            if (!seenIds.add(p.id)) return false
            if (p.x < 0 || p.y < 0 ||
                p.x + p.type.w > KlotskiBoard.COLS || p.y + p.type.h > KlotskiBoard.ROWS
            ) {
                return false
            }
            for (cy in p.y until p.y + p.type.h) {
                for (cx in p.x until p.x + p.type.w) {
                    if (!cells.add(cy * KlotskiBoard.COLS + cx)) return false
                }
            }
        }
        pieces.clear()
        pieces.addAll(list)
        moves = snap.moves
        selectedId = null
        finished = checkWon()
        return true
    }

    private fun checkWon(): Boolean {
        val cao = pieces.firstOrNull { it.type == KlotskiPieceType.CAO } ?: return false
        return cao.x == KlotskiBoard.EXIT_X && cao.y == KlotskiBoard.ROWS - 2
    }

    private fun occupies(p: KlotskiPiece, x: Int, y: Int): Boolean =
        x >= p.x && x < p.x + p.type.w && y >= p.y && y < p.y + p.type.h

    private fun occupiedByOther(x: Int, y: Int, exceptId: Int): Boolean =
        pieces.any { it.id != exceptId && occupies(it, x, y) }

    /**
     * 把点中的目标格换算成块的新左上角：枚举目标格落在块内的位置，
     * 只接受横平竖直、路径无阻挡的方案，取滑动距离最短的一个。
     */
    private fun slideDestination(p: KlotskiPiece, tx: Int, ty: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDist = Int.MAX_VALUE
        for (ox in 0 until p.type.w) {
            for (oy in 0 until p.type.h) {
                val nx = tx - ox
                val ny = ty - oy
                if (nx == p.x && ny == p.y) continue
                if (nx < 0 || ny < 0 ||
                    nx + p.type.w > KlotskiBoard.COLS || ny + p.type.h > KlotskiBoard.ROWS
                ) {
                    continue
                }
                val dx = nx - p.x
                val dy = ny - p.y
                if (dx != 0 && dy != 0) continue
                if (!pathClear(p, nx, ny)) continue
                val dist = abs(dx) + abs(dy)
                if (dist < bestDist) {
                    bestDist = dist
                    best = nx to ny
                }
            }
        }
        return best
    }

    /** 逐格检查滑动经过的新格子是否被别的块占着。 */
    private fun pathClear(p: KlotskiPiece, nx: Int, ny: Int): Boolean {
        val dx = nx - p.x
        val dy = ny - p.y
        if (dx != 0) {
            val step = if (dx > 0) 1 else -1
            for (i in 1..abs(dx)) {
                val col = if (step > 0) p.x + p.type.w - 1 + i else p.x - i
                for (row in p.y until p.y + p.type.h) {
                    if (occupiedByOther(col, row, p.id)) return false
                }
            }
        }
        if (dy != 0) {
            val step = if (dy > 0) 1 else -1
            for (i in 1..abs(dy)) {
                val row = if (step > 0) p.y + p.type.h - 1 + i else p.y - i
                for (col in p.x until p.x + p.type.w) {
                    if (occupiedByOther(col, row, p.id)) return false
                }
            }
        }
        return true
    }
}

/**
 * 华容道 BFS 求解器：从当前局面搜到「曹操抵达底部出口」的最少滑动步数解法，
 * 返回第一步该怎么走。状态空间约几万~十几万，BFS 毫秒~百毫秒级即可解完。
 *
 * 走法定义与棋盘交互一致：一步 = 一个块沿直线滑到任意远（路径无阻挡）。
 */
object KlotskiSolver {

    /** 第一步提示：把 [pieceId] 的块滑到左上角 (toX, toY)。 */
    data class Hint(val pieceId: Int, val toX: Int, val toY: Int)

    private class Node(
        val pieces: List<KlotskiPiece>,
        val parent: Node?,
        val move: Hint?,
    )

    /** 返回最优解的第一步；已通关或无解时返回 null。 */
    fun firstStep(start: List<KlotskiPiece>): Hint? {
        if (start.any { isCaoAtExit(it) }) return null
        val visited = HashSet<String>(1 shl 17)
        visited.add(canonicalKey(start))
        val queue = ArrayDeque<Node>()
        queue.addLast(Node(start, parent = null, move = null))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for ((piece, nx, ny) in slides(node.pieces)) {
                val next = node.pieces.map { p -> if (p.id == piece.id) p.copy(x = nx, y = ny) else p }
                if (!visited.add(canonicalKey(next))) continue
                val child = Node(next, parent = node, move = Hint(piece.id, nx, ny))
                if (next.any { isCaoAtExit(it) }) {
                    // 回溯到根节点的直接子节点，即是解法的第一步
                    var first = child
                    while (first.parent?.parent != null) first = first.parent!!
                    return first.move
                }
                queue.addLast(child)
            }
        }
        return null
    }

    private fun isCaoAtExit(p: KlotskiPiece): Boolean =
        p.type == KlotskiPieceType.CAO && p.x == KlotskiBoard.EXIT_X && p.y == KlotskiBoard.ROWS - 2

    /** 枚举所有「沿直线滑到任意空位」的合法走法。 */
    private fun slides(pieces: List<KlotskiPiece>): List<Triple<KlotskiPiece, Int, Int>> {
        val result = ArrayList<Triple<KlotskiPiece, Int, Int>>(32)
        for (p in pieces) {
            for (dx in intArrayOf(-1, 1)) {
                var nx = p.x + dx
                while (canPlace(pieces, p, nx, p.y)) {
                    result.add(Triple(p, nx, p.y))
                    nx += dx
                }
            }
            for (dy in intArrayOf(-1, 1)) {
                var ny = p.y + dy
                while (canPlace(pieces, p, p.x, ny)) {
                    result.add(Triple(p, p.x, ny))
                    ny += dy
                }
            }
        }
        return result
    }

    /** 块平移到 (nx, ny) 后不越界、且与任何别的块都不重叠。 */
    private fun canPlace(pieces: List<KlotskiPiece>, p: KlotskiPiece, nx: Int, ny: Int): Boolean {
        if (nx < 0 || ny < 0 || nx + p.type.w > KlotskiBoard.COLS || ny + p.type.h > KlotskiBoard.ROWS) return false
        for (q in pieces) {
            if (q.id == p.id) continue
            if (q.x < nx + p.type.w && nx < q.x + q.type.w &&
                q.y < ny + p.type.h && ny < q.y + q.type.h
            ) {
                return false
            }
        }
        return true
    }

    /**
     * 局面唯一标识：同型块（竖将之间、兵之间）可互换，按类型归并格子编号，
     * 等价状态只搜一次，可显著缩小搜索空间。
     */
    private fun canonicalKey(pieces: List<KlotskiPiece>): String {
        var cao = -1
        val v = ArrayList<Int>(4)
        val h = ArrayList<Int>(1)
        val s = ArrayList<Int>(4)
        for (p in pieces) {
            val code = p.y * KlotskiBoard.COLS + p.x
            when (p.type) {
                KlotskiPieceType.CAO -> cao = code
                KlotskiPieceType.V_GENERAL -> v.add(code)
                KlotskiPieceType.H_GENERAL -> h.add(code)
                KlotskiPieceType.SOLDIER -> s.add(code)
            }
        }
        v.sort()
        s.sort()
        return "c$cao|v$v|h$h|s$s"
    }
}
