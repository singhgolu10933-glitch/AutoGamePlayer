package com.autogameplayer.blockblitz

import android.util.Log

data class BlockBlitzMove(
    val pieceIndex: Int,
    val row: Int,
    val column: Int,
    val score: Double,
    val reason: String
)

object BlockBlitzAi {

    private const val TAG = "BlockBlitzAi"

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

        if (
            board.rows != ROWS ||
            board.columns != COLUMNS
        ) {
            return null
        }

        var bestMove:
                BlockBlitzMove? =
            null

        var bestScore =
            Double.NEGATIVE_INFINITY

        var legalMoves =
            0

        for (piece in state.pieces) {

            if (
                !piece.detected ||
                piece.cells.isEmpty()
            ) {
                continue
            }

            val shape =
                normalizeShape(
                    piece.cells
                )

            if (
                !isValidShape(shape)
            ) {
                continue
            }

            for (row in 0 until ROWS) {

                for (column in 0 until COLUMNS) {

                    if (
                        !canPlace(
                            board,
                            shape,
                            row,
                            column
                        )
                    ) {
                        continue
                    }

                    legalMoves++

                    val simulation =
                        simulatePlacement(
                            board,
                            shape,
                            row,
                            column
                        )

                    val score =
                        evaluate(
                            simulation,
                            shape
                        )

                    if (
                        score > bestScore
                    ) {

                        bestScore =
                            score

                        bestMove =
                            BlockBlitzMove(
                                pieceIndex =
                                    piece.index,

                                row =
                                    row,

                                column =
                                    column,

                                score =
                                    score,

                                reason =
                                    reason(
                                        simulation,
                                        score
                                    )
                            )
                    }
                }
            }
        }

        Log.d(
            TAG,
            "AI -> legalMoves=$legalMoves " +
                    "best=$bestMove"
        )

        return bestMove
    }

    // ============================================================
    // NORMALIZE
    // ============================================================

    private fun normalizeShape(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (cells.isEmpty()) {
            return emptyList()
        }

        val minRow =
            cells.minOf {
                it.first
            }

        val minColumn =
            cells.minOf {
                it.second
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
                compareBy<Pair<Int, Int>> {
                    it.first
                }.thenBy {
                    it.second
                }
            )
    }

    // ============================================================
    // VALIDATE SHAPE
    // ============================================================

    private fun isValidShape(
        shape: List<Pair<Int, Int>>
    ): Boolean {

        if (
            shape.isEmpty() ||
            shape.size > 25
        ) {
            return false
        }

        for ((row, column) in shape) {

            if (
                row < 0 ||
                column < 0 ||
                row >= ROWS ||
                column >= COLUMNS
            ) {
                return false
            }
        }

        return true
    }

    // ============================================================
    // LEGAL MOVE
    // ============================================================

    private fun canPlace(
        board: BlockBlitzBoard,
        shape: List<Pair<Int, Int>>,
        startRow: Int,
        startColumn: Int
    ): Boolean {

        if (
            shape.isEmpty()
        ) {
            return false
        }

        for ((dr, dc) in shape) {

            val row =
                startRow + dr

            val column =
                startColumn + dc

            if (
                row !in 0 until board.rows ||
                column !in 0 until board.columns
            ) {
                return false
            }

            if (
                board.occupied[row][column]
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
        val board: BlockBlitzBoard,
        val rowsCleared: Int,
        val columnsCleared: Int
    ) {

        val linesCleared: Int
            get() =
                rowsCleared +
                        columnsCleared
    }

    private fun simulatePlacement(
        board: BlockBlitzBoard,
        shape: List<Pair<Int, Int>>,
        startRow: Int,
        startColumn: Int
    ): Simulation {

        val cells =
            Array(board.rows) {
                board.occupied[it].clone()
            }

        for ((dr, dc) in shape) {

            val row =
                startRow + dr

            val column =
                startColumn + dc

            cells[row][column] =
                true
        }

        var rowsCleared =
            0

        var columnsCleared =
            0

        // --------------------------------------------------------
        // ROWS
        // --------------------------------------------------------

        for (row in 0 until board.rows) {

            var full =
                true

            for (column in 0 until board.columns) {

                if (
                    !cells[row][column]
                ) {
                    full = false
                    break
                }
            }

            if (full) {

                rowsCleared++

                for (column in 0 until board.columns) {

                    cells[row][column] =
                        false
                }
            }
        }

        // --------------------------------------------------------
        // COLUMNS
        // --------------------------------------------------------

        for (column in 0 until board.columns) {

            var full =
                true

            for (row in 0 until board.rows) {

                if (
                    !cells[row][column]
                ) {
                    full = false
                    break
                }
            }

            if (full) {

                columnsCleared++

                for (row in 0 until board.rows) {

                    cells[row][column] =
                        false
                }
            }
        }

        return Simulation(
            board =
                BlockBlitzBoard(
                    rows =
                        board.rows,

                    columns =
                        board.columns,

                    occupied =
                        cells
                ),

            rowsCleared =
                rowsCleared,

            columnsCleared =
                columnsCleared
        )
    }

    // ============================================================
    // SCORE
    // ============================================================

    private fun evaluate(
        simulation: Simulation,
        shape: List<Pair<Int, Int>>
    ): Double {

        val board =
            simulation.board

        val total =
            board.rows *
                    board.columns

        val occupied =
            board.occupiedCount()

        val empty =
            total -
                    occupied

        val mobility =
            mobility(board)

        val holes =
            holes(board)

        val fragmentation =
            fragmentation(board)

        /*
         * Clearing a line is much more important
         * than simply occupying a convenient cell.
         */

        val lineScore =
            simulation.linesCleared *
                    1500.0

        val emptyScore =
            empty *
                    2.0

        val mobilityScore =
            mobility *
                    4.0

        val holePenalty =
            holes *
                    30.0

        val fragmentationPenalty =
            fragmentation *
                    4.0

        val piecePenalty =
            shape.size *
                    0.25

        return (
            lineScore +
                    emptyScore +
                    mobilityScore -
                    holePenalty -
                    fragmentationPenalty -
                    piecePenalty
            )
    }

    // ============================================================
    // MOBILITY
    // ============================================================

    private fun mobility(
        board: BlockBlitzBoard
    ): Int {

        var result =
            0

        for (row in 0 until board.rows) {

            for (column in 0 until board.columns) {

                if (
                    board.occupied[row][column]
                ) {
                    continue
                }

                var neighbors =
                    0

                if (
                    row > 0 &&
                    board.occupied[
                        row - 1
                    ][column]
                ) {
                    neighbors++
                }

                if (
                    row + 1 < board.rows &&
                    board.occupied[
                        row + 1
                    ][column]
                ) {
                    neighbors++
                }

                if (
                    column > 0 &&
                    board.occupied[
                        row
                    ][column - 1]
                ) {
                    neighbors++
                }

                if (
                    column + 1 < board.columns &&
                    board.occupied[
                        row
                    ][column + 1]
                ) {
                    neighbors++
                }

                if (
                    neighbors >= 1
                ) {
                    result++
                }
            }
        }

        return result
    }

    // ============================================================
    // HOLES
    // ============================================================

    private fun holes(
        board: BlockBlitzBoard
    ): Int {

        var result =
            0

        for (row in 1 until board.rows - 1) {

            for (
                column in
                1 until board.columns - 1
            ) {

                if (
                    board.occupied[row][column]
                ) {
                    continue
                }

                var neighbors =
                    0

                if (
                    board.occupied[
                        row - 1
                    ][column]
                ) {
                    neighbors++
                }

                if (
                    board.occupied[
                        row + 1
                    ][column]
                ) {
                    neighbors++
                }

                if (
                    board.occupied[
                        row
                    ][column - 1]
                ) {
                    neighbors++
                }

                if (
                    board.occupied[
                        row
                    ][column + 1]
                ) {
                    neighbors++
                }

                if (
                    neighbors >= 3
                ) {
                    result++
                }
            }
        }

        return result
    }

    // ============================================================
    // FRAGMENTATION
    // ============================================================

    private fun fragmentation(
        board: BlockBlitzBoard
    ): Int {

        var result =
            0

        for (row in 0 until board.rows) {

            for (column in 0 until board.columns) {

                if (
                    !board.occupied[row][column]
                ) {
                    continue
                }

                var neighbors =
                    0

                if (
                    row > 0 &&
                    board.occupied[
                        row - 1
                    ][column]
                ) {
                    neighbors++
                }

                if (
                    row + 1 < board.rows &&
                    board.occupied[
                        row + 1
                    ][column]
                ) {
                    neighbors++
                }

                if (
                    column > 0 &&
                    board.occupied[
                        row
                    ][column - 1]
                ) {
                    neighbors++
                }

                if (
                    column + 1 < board.columns &&
                    board.occupied[
                        row
                    ][column + 1]
                ) {
                    neighbors++
                }

                if (
                    neighbors == 0
                ) {
                    result += 3

                } else if (
                    neighbors == 1
                ) {
                    result++
                }
            }
        }

        return result
    }

    // ============================================================
    // REASON
    // ============================================================

    private fun reason(
        simulation: Simulation,
        score: Double
    ): String {

        return when {

            simulation.linesCleared >= 2 ->
                "Clears ${simulation.linesCleared} lines"

            simulation.linesCleared == 1 ->
                "Clears 1 line"

            score >= 100 ->
                "Preserves open space"

            else ->
                "Safe legal placement"
        }
    }
}
