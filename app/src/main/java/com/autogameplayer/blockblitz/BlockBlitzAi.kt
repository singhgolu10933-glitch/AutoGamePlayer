package com.autogameplayer.blockblitz

import com.autogameplayer.blockengine.ShapeNormalizer

data class UniversalBlockPiece(
    val id: Int,
    val cells: List<Pair<Int, Int>>
) {
    val normalizedCells: List<Pair<Int, Int>>
        get() = ShapeNormalizer.normalize(cells)

    val width: Int
        get() = ShapeNormalizer.width(cells)

    val height: Int
        get() = ShapeNormalizer.height(cells)
}

data class UniversalBoard(
    val rows: Int = 9,
    val columns: Int = 9,
    val occupied: Array<BooleanArray>
) {

    companion object {
        fun empty(
            rows: Int = 9,
            columns: Int = 9
        ): UniversalBoard {
            return UniversalBoard(
                rows = rows,
                columns = columns,
                occupied = Array(rows) {
                    BooleanArray(columns)
                }
            )
        }

        fun from(
            source: Array<BooleanArray>
        ): UniversalBoard {
            val rows = source.size

            if (rows == 0) {
                return empty()
            }

            val columns = source.maxOf { it.size }

            val board = Array(rows) {
                BooleanArray(columns)
            }

            for (r in source.indices) {
                for (c in source[r].indices) {
                    board[r][c] = source[r][c]
                }
            }

            return UniversalBoard(
                rows = rows,
                columns = columns,
                occupied = board
            )
        }
    }

    fun copyBoard(): UniversalBoard {
        val copied = Array(rows) { r ->
            BooleanArray(columns) { c ->
                occupied[r][c]
            }
        }

        return UniversalBoard(
            rows = rows,
            columns = columns,
            occupied = copied
        )
    }

    fun occupiedCount(): Int {
        var count = 0

        for (r in 0 until rows) {
            for (c in 0 until columns) {
                if (occupied[r][c]) {
                    count++
                }
            }
        }

        return count
    }
}

data class UniversalMove(
    val pieceId: Int,
    val row: Int,
    val column: Int,
    val score: Double,
    val clearedRows: Int,
    val clearedColumns: Int,
    val clearedLines: Int,
    val reason: String
)

object BlockBlitzAi {

    private const val CLEAR_LINE_SCORE = 1000.0
    private const val DOUBLE_CLEAR_BONUS = 350.0
    private const val TRIPLE_CLEAR_BONUS = 800.0

    /**
     * Main AI entry point.
     *
     * The AI receives:
     *   - current board
     *   - currently available pieces
     *
     * and returns the best legal move.
     */
    fun findBestMove(
        board: UniversalBoard,
        pieces: List<UniversalBlockPiece>
    ): UniversalMove? {

        if (pieces.isEmpty()) {
            return null
        }

        var bestMove: UniversalMove? = null

        for (piece in pieces) {

            val shape = piece.normalizedCells

            if (shape.isEmpty()) {
                continue
            }

            val maxRow = board.rows - piece.height
            val maxColumn = board.columns - piece.width

            if (maxRow < 0 || maxColumn < 0) {
                continue
            }

            for (row in 0..maxRow) {
                for (column in 0..maxColumn) {

                    if (!canPlace(
                            board = board,
                            piece = shape,
                            row = row,
                            column = column
                        )
                    ) {
                        continue
                    }

                    val simulation = simulateMove(
                        board = board,
                        piece = shape,
                        row = row,
                        column = column
                    )

                    val score = evaluate(
                        before = board,
                        after = simulation.board,
                        clearedRows = simulation.clearedRows,
                        clearedColumns = simulation.clearedColumns
                    )

                    val totalClears =
                        simulation.clearedRows +
                                simulation.clearedColumns

                    val reason = buildReason(
                        clearedRows = simulation.clearedRows,
                        clearedColumns = simulation.clearedColumns,
                        score = score
                    )

                    val move = UniversalMove(
                        pieceId = piece.id,
                        row = row,
                        column = column,
                        score = score,
                        clearedRows = simulation.clearedRows,
                        clearedColumns = simulation.clearedColumns,
                        clearedLines = totalClears,
                        reason = reason
                    )

                    if (bestMove == null ||
                        isBetterMove(move, bestMove!!)
                    ) {
                        bestMove = move
                    }
                }
            }
        }

        return bestMove
    }

    /**
     * Checks whether a piece can legally occupy the requested position.
     */
    fun canPlace(
        board: UniversalBoard,
        piece: List<Pair<Int, Int>>,
        row: Int,
        column: Int
    ): Boolean {

        for ((pieceRow, pieceColumn) in piece) {

            val targetRow = row + pieceRow
            val targetColumn = column + pieceColumn

            if (targetRow !in 0 until board.rows) {
                return false
            }

            if (targetColumn !in 0 until board.columns) {
                return false
            }

            if (board.occupied[targetRow][targetColumn]) {
                return false
            }
        }

        return true
    }

    private data class SimulationResult(
        val board: UniversalBoard,
        val clearedRows: Int,
        val clearedColumns: Int
    )

    /**
     * Simulates placing one piece and clearing completed rows/columns.
     */
    private fun simulateMove(
        board: UniversalBoard,
        piece: List<Pair<Int, Int>>,
        row: Int,
        column: Int
    ): SimulationResult {

        val simulated = board.copyBoard()

        for ((pieceRow, pieceColumn) in piece) {

            val targetRow = row + pieceRow
            val targetColumn = column + pieceColumn

            if (
                targetRow in 0 until simulated.rows &&
                targetColumn in 0 until simulated.columns
            ) {
                simulated.occupied[targetRow][targetColumn] = true
            }
        }

        val fullRows = mutableListOf<Int>()

        for (r in 0 until simulated.rows) {

            var full = true

            for (c in 0 until simulated.columns) {
                if (!simulated.occupied[r][c]) {
                    full = false
                    break
                }
            }

            if (full) {
                fullRows.add(r)
            }
        }

        val fullColumns = mutableListOf<Int>()

        for (c in 0 until simulated.columns) {

            var full = true

            for (r in 0 until simulated.rows) {
                if (!simulated.occupied[r][c]) {
                    full = false
                    break
                }
            }

            if (full) {
                fullColumns.add(c)
            }
        }

        for (r in fullRows) {
            for (c in 0 until simulated.columns) {
                simulated.occupied[r][c] = false
            }
        }

        for (c in fullColumns) {
            for (r in 0 until simulated.rows) {
                simulated.occupied[r][c] = false
            }
        }

        return SimulationResult(
            board = simulated,
            clearedRows = fullRows.size,
            clearedColumns = fullColumns.size
        )
    }

    /**
     * Human-like heuristic.
     *
     * The AI does not simply chase maximum line clears.
     * It also values open space and future mobility.
     */
    private fun evaluate(
        before: UniversalBoard,
        after: UniversalBoard,
        clearedRows: Int,
        clearedColumns: Int
    ): Double {

        val clearedLines =
            clearedRows + clearedColumns

        val occupiedBefore =
            before.occupiedCount()

        val occupiedAfter =
            after.occupiedCount()

        val freeBefore =
            before.rows * before.columns - occupiedBefore

        val freeAfter =
            after.rows * after.columns - occupiedAfter

        val mobility =
            countLegalSingleCellMoves(after)

        var score = 0.0

        // Line clearing is strongly rewarded.
        score += clearedLines * CLEAR_LINE_SCORE

        if (clearedLines == 2) {
            score += DOUBLE_CLEAR_BONUS
        }

        if (clearedLines >= 3) {
            score += TRIPLE_CLEAR_BONUS
        }

        // Preserve empty space.
        score += freeAfter * 1.5

        // Keep future moves available.
        score += mobility * 8.0

        // Slightly discourage unnecessary board filling.
        val addedOccupancy =
            occupiedAfter - occupiedBefore

        score -= addedOccupancy * 1.5

        // Avoid creating highly fragmented areas.
        score -= fragmentationPenalty(after)

        // Avoid creating isolated holes.
        score -= holePenalty(after) * 2.0

        // Tiny preference for preserving free space.
        if (freeAfter >= freeBefore) {
            score += 10.0
        }

        return score
    }

    /**
     * Counts cells where a single block could still be placed.
     * This is a simple mobility approximation.
     */
    private fun countLegalSingleCellMoves(
        board: UniversalBoard
    ): Int {

        var count = 0

        for (r in 0 until board.rows) {
            for (c in 0 until board.columns) {

                if (!board.occupied[r][c]) {
                    count++
                }
            }
        }

        return count
    }

    /**
     * Penalizes disconnected occupied regions.
     */
    private fun fragmentationPenalty(
        board: UniversalBoard
    ): Double {

        var penalty = 0.0

        for (r in 0 until board.rows) {
            for (c in 0 until board.columns) {

                if (!board.occupied[r][c]) {
                    continue
                }

                var neighbours = 0

                if (r > 0 && board.occupied[r - 1][c]) {
                    neighbours++
                }

                if (r + 1 < board.rows &&
                    board.occupied[r + 1][c]
                ) {
                    neighbours++
                }

                if (c > 0 && board.occupied[r][c - 1]) {
                    neighbours++
                }

                if (c + 1 < board.columns &&
                    board.occupied[r][c + 1]
                ) {
                    neighbours++
                }

                if (neighbours == 0) {
                    penalty += 4.0
                }
            }
        }

        return penalty
    }

    /**
     * Detects simple empty pockets surrounded by occupied cells.
     */
    private fun holePenalty(
        board: UniversalBoard
    ): Double {

        var penalty = 0.0

        for (r in 1 until board.rows - 1) {
            for (c in 1 until board.columns - 1) {

                if (board.occupied[r][c]) {
                    continue
                }

                val surrounded =
                    board.occupied[r - 1][c] &&
                    board.occupied[r + 1][c] &&
                    board.occupied[r][c - 1] &&
                    board.occupied[r][c + 1]

                if (surrounded) {
                    penalty += 5.0
                }
            }
        }

        return penalty
    }

    private fun isBetterMove(
        candidate: UniversalMove,
        current: UniversalMove
    ): Boolean {

        if (candidate.clearedLines != current.clearedLines) {
            return candidate.clearedLines > current.clearedLines
        }

        if (candidate.score != current.score) {
            return candidate.score > current.score
        }

        // Stable tie-breaker.
        if (candidate.pieceId != current.pieceId) {
            return candidate.pieceId < current.pieceId
        }

        if (candidate.row != current.row) {
            return candidate.row < current.row
        }

        return candidate.column < current.column
    }

    private fun buildReason(
        clearedRows: Int,
        clearedColumns: Int,
        score: Double
    ): String {

        val lines =
            clearedRows + clearedColumns

        return when {
            lines >= 3 ->
                "Clears $lines lines and preserves board mobility"

            lines == 2 ->
                "Clears 2 lines with good board space"

            lines == 1 ->
                "Clears 1 line"

            else ->
                "Best legal placement for future mobility"
        }
    }
}
