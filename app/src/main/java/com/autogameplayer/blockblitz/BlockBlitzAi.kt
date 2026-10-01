package com.autogameplayer.blockblitz

import kotlin.math.abs

data class BlockBlitzMove(
    val pieceIndex: Int,
    val row: Int,
    val column: Int,
    val score: Float,
    val linesCleared: Int,
    val reason: String
)

object BlockBlitzAi {

    private const val ROWS = 8
    private const val COLUMNS = 8

    // ============================================================
    // FIND BEST MOVE
    // ============================================================

    fun findBestMove(
        state: BlockBlitzState
    ): BlockBlitzMove? {

        val board =
            state.board
                ?: return null

        var bestMove: BlockBlitzMove? = null

        for (piece in state.pieces) {

            if (!piece.detected) {
                continue
            }

            if (piece.cells.isEmpty()) {
                continue
            }

            val normalized =
                normalizePiece(
                    piece.cells
                )

            if (normalized.isEmpty()) {
                continue
            }

            val maxRow =
                normalized.maxOf {
                    it.first
                }

            val maxColumn =
                normalized.maxOf {
                    it.second
                }

            for (row in 0 until ROWS) {

                for (column in 0 until COLUMNS) {

                    if (
                        row + maxRow >= ROWS ||
                        column + maxColumn >= COLUMNS
                    ) {
                        continue
                    }

                    if (
                        !canPlace(
                            board,
                            normalized,
                            row,
                            column
                        )
                    ) {
                        continue
                    }

                    val simulation =
                        simulate(
                            board,
                            normalized,
                            row,
                            column
                        )

                    val score =
                        evaluate(
                            simulation.board,
                            simulation.linesCleared,
                            row,
                            column,
                            normalized
                        )

                    val reason =
                        if (
                            simulation.linesCleared > 0
                        ) {
                            "Clears ${simulation.linesCleared} line"
                        } else {
                            "Good space and mobility"
                        }

                    val move =
                        BlockBlitzMove(
                            pieceIndex = piece.index,
                            row = row,
                            column = column,
                            score = score,
                            linesCleared =
                                simulation.linesCleared,
                            reason = reason
                        )

                    if (
                        bestMove == null ||
                        move.score >
                        bestMove!!.score
                    ) {
                        bestMove = move
                    }
                }
            }
        }

        return bestMove
    }

    // ============================================================
    // NORMALIZE PIECE
    // ============================================================

    private fun normalizePiece(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (cells.isEmpty()) {
            return emptyList()
        }

        var minRow =
            cells[0].first

        var minColumn =
            cells[0].second

        for (cell in cells) {

            if (cell.first < minRow) {
                minRow = cell.first
            }

            if (cell.second < minColumn) {
                minColumn = cell.second
            }
        }

        return cells
            .map {
                Pair(
                    it.first - minRow,
                    it.second - minColumn
                )
            }
            .distinct()
            .sortedWith(
                compareBy<Pair<Int, Int>>(
                    { it.first },
                    { it.second }
                )
            )
    }

    // ============================================================
    // CAN PLACE
    // ============================================================

    private fun canPlace(
        board: BlockBlitzBoard,
        piece: List<Pair<Int, Int>>,
        row: Int,
        column: Int
    ): Boolean {

        for (cell in piece) {

            val targetRow =
                row + cell.first

            val targetColumn =
                column + cell.second

            if (
                targetRow !in 0 until ROWS ||
                targetColumn !in 0 until COLUMNS
            ) {
                return false
            }

            if (
                board.occupied[
                    targetRow
                ][
                    targetColumn
                ]
            ) {
                return false
            }
        }

        return true
    }

    // ============================================================
    // SIMULATION
    // ============================================================

    private data class Simulation(
        val board: Array<BooleanArray>,
        val linesCleared: Int
    )

    private fun simulate(
        board: BlockBlitzBoard,
        piece: List<Pair<Int, Int>>,
        row: Int,
        column: Int
    ): Simulation {

        val copy =
            board.copyBoard()

        for (cell in piece) {

            copy[
                row + cell.first
            ][
                column + cell.second
            ] = true
        }

        val rowsToClear =
            mutableSetOf<Int>()

        val columnsToClear =
            mutableSetOf<Int>()

        // --------------------------------------------------------
        // ROWS
        // --------------------------------------------------------

        for (r in 0 until ROWS) {

            var complete =
                true

            for (c in 0 until COLUMNS) {

                if (!copy[r][c]) {
                    complete = false
                    break
                }
            }

            if (complete) {
                rowsToClear.add(r)
            }
        }

        // --------------------------------------------------------
        // COLUMNS
        // --------------------------------------------------------

        for (c in 0 until COLUMNS) {

            var complete =
                true

            for (r in 0 until ROWS) {

                if (!copy[r][c]) {
                    complete = false
                    break
                }
            }

            if (complete) {
                columnsToClear.add(c)
            }
        }

        val linesCleared =
            rowsToClear.size +
                    columnsToClear.size

        // --------------------------------------------------------
        // CLEAR ROWS
        // --------------------------------------------------------

        for (r in rowsToClear) {

            for (c in 0 until COLUMNS) {

                copy[r][c] = false
            }
        }

        // --------------------------------------------------------
        // CLEAR COLUMNS
        // --------------------------------------------------------

        for (c in columnsToClear) {

            for (r in 0 until ROWS) {

                copy[r][c] = false
            }
        }

        return Simulation(
            board = copy,
            linesCleared = linesCleared
        )
    }

    // ============================================================
    // EVALUATION
    // ============================================================

    private fun evaluate(
        board: Array<BooleanArray>,
        linesCleared: Int,
        row: Int,
        column: Int,
        piece: List<Pair<Int, Int>>
    ): Float {

        var score =
            0f

        // --------------------------------------------------------
        // LINE CLEAR
        // --------------------------------------------------------

        score +=
            linesCleared * 1000f

        // --------------------------------------------------------
        // EMPTY SPACE
        // --------------------------------------------------------

        var occupied =
            0

        for (r in 0 until ROWS) {

            for (c in 0 until COLUMNS) {

                if (board[r][c]) {
                    occupied++
                }
            }
        }

        val totalCells =
            ROWS * COLUMNS

        val empty =
            totalCells - occupied

        score +=
            empty * 3.0f

        // --------------------------------------------------------
        // FRAGMENTATION
        // --------------------------------------------------------

        score -=
            fragmentationPenalty(
                board
            )

        // --------------------------------------------------------
        // CENTER PREFERENCE
        // --------------------------------------------------------

        val centerRow =
            (ROWS - 1) / 2f

        val centerColumn =
            (COLUMNS - 1) / 2f

        var pieceRowSum =
            0f

        var pieceColumnSum =
            0f

        for (cell in piece) {

            pieceRowSum +=
                cell.first.toFloat()

            pieceColumnSum +=
                cell.second.toFloat()
        }

        val pieceCount =
            piece.size.coerceAtLeast(1)

        val averagePieceRow =
            pieceRowSum /
                    pieceCount

        val averagePieceColumn =
            pieceColumnSum /
                    pieceCount

        score -=
            abs(
                row +
                        averagePieceRow -
                        centerRow
            ) * 2f

        score -=
            abs(
                column +
                        averagePieceColumn -
                        centerColumn
            ) * 2f

        // --------------------------------------------------------
        // MOBILITY
        // --------------------------------------------------------

        score +=
            mobilityScore(
                board
            )

        // --------------------------------------------------------
        // HOLES
        // --------------------------------------------------------

        score -=
            holePenalty(
                board
            ) * 4f

        return score
    }

    // ============================================================
    // MOBILITY
    // ============================================================

    private fun mobilityScore(
        board: Array<BooleanArray>
    ): Float {

        var open =
            0

        for (r in 0 until ROWS) {

            for (c in 0 until COLUMNS) {

                if (!board[r][c]) {
                    open++
                }
            }
        }

        return open * 0.8f
    }

    // ============================================================
    // FRAGMENTATION
    // ============================================================

    private fun fragmentationPenalty(
        board: Array<BooleanArray>
    ): Float {

        var penalty =
            0f

        for (r in 0 until ROWS) {

            for (c in 0 until COLUMNS) {

                if (!board[r][c]) {
                    continue
                }

                var neighbors =
                    0

                if (
                    r > 0 &&
                    board[r - 1][c]
                ) {
                    neighbors++
                }

                if (
                    r < ROWS - 1 &&
                    board[r + 1][c]
                ) {
                    neighbors++
                }

                if (
                    c > 0 &&
                    board[r][c - 1]
                ) {
                    neighbors++
                }

                if (
                    c < COLUMNS - 1 &&
                    board[r][c + 1]
                ) {
                    neighbors++
                }

                if (neighbors == 0) {
                    penalty += 5f
                }
            }
        }

        return penalty
    }

    // ============================================================
    // HOLE PENALTY
    // ============================================================

    private fun holePenalty(
        board: Array<BooleanArray>
    ): Float {

        var holes =
            0

        for (r in 1 until ROWS - 1) {

            for (c in 1 until COLUMNS - 1) {

                if (board[r][c]) {
                    continue
                }

                var blocked =
                    0

                if (board[r - 1][c]) {
                    blocked++
                }

                if (board[r + 1][c]) {
                    blocked++
                }

                if (board[r][c - 1]) {
                    blocked++
                }

                if (board[r][c + 1]) {
                    blocked++
                }

                if (blocked >= 3) {
                    holes++
                }
            }
        }

        return holes.toFloat()
    }
}
