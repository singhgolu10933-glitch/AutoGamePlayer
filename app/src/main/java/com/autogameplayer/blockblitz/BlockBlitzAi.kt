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
    // MAIN AI
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
            Log.d(
                TAG,
                "Invalid board dimensions"
            )

            return null
        }

        var bestMove: BlockBlitzMove? =
            null

        var bestScore =
            Double.NEGATIVE_INFINITY

        var totalLegalMoves =
            0

        for (piece in state.pieces) {

            if (!piece.detected) {
                continue
            }

            if (piece.cells.isEmpty()) {
                continue
            }

            val shape =
                normalizeShape(
                    piece.cells
                )

            if (!isValidShape(shape)) {
                continue
            }

            val maxShapeRow =
                shape.maxOf {
                    it.first
                }

            val maxShapeColumn =
                shape.maxOf {
                    it.second
                }

            if (
                maxShapeRow >= ROWS ||
                maxShapeColumn >= COLUMNS
            ) {
                continue
            }

            for (row in 0 until ROWS) {

                for (column in 0 until COLUMNS) {

                    if (
                        !canPlace(
                            board = board,
                            shape = shape,
                            startRow = row,
                            startColumn = column
                        )
                    ) {
                        continue
                    }

                    totalLegalMoves++

                    val simulation =
                        simulatePlacement(
                            board = board,
                            shape = shape,
                            startRow = row,
                            startColumn = column
                        )

                    val score =
                        evaluatePosition(
                            board =
                                simulation.board,
                            linesCleared =
                                simulation.linesCleared,
                            shape =
                                shape
                        )

                    Log.d(
                        TAG,
                        "LEGAL MOVE -> " +
                                "piece=${piece.index} " +
                                "row=$row " +
                                "column=$column " +
                                "score=$score"
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
                                    buildReason(
                                        simulation.linesCleared,
                                        score
                                    )
                            )
                    }
                }
            }
        }

        Log.d(
            TAG,
            "AI RESULT -> " +
                    "legalMoves=$totalLegalMoves " +
                    "bestMove=$bestMove"
        )

        return bestMove
    }

    // ============================================================
    // SHAPE NORMALIZATION
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
    // SHAPE VALIDATION
    // ============================================================

    private fun isValidShape(
        shape: List<Pair<Int, Int>>
    ): Boolean {

        if (shape.isEmpty()) {
            return false
        }

        /*
         * Block Blitz pieces should normally
         * contain between 1 and 25 cells.
         */

        if (shape.size > 25) {
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
    // LEGAL PLACEMENT
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

        if (
            board.rows != ROWS ||
            board.columns != COLUMNS
        ) {
            return false
        }

        for ((dr, dc) in shape) {

            val row =
                startRow + dr

            val column =
                startColumn + dc

            // Outside board
            if (
                row < 0 ||
                row >= board.rows ||
                column < 0 ||
                column >= board.columns
            ) {
                return false
            }

            // Collision
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
        val linesCleared: Int,
        val rowsCleared: Int,
        val columnsCleared: Int
    )

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

        // Place piece
        for ((dr, dc) in shape) {

            val row =
                startRow + dr

            val column =
                startColumn + dc

            if (
                row !in 0 until board.rows ||
                column !in 0 until board.columns
            ) {
                return Simulation(
                    board = board,
                    linesCleared = 0,
                    rowsCleared = 0,
                    columnsCleared = 0
                )
            }

            cells[row][column] =
                true
        }

        var rowsCleared =
            0

        var columnsCleared =
            0

        // --------------------------------------------------------
        // FULL ROWS
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
        // FULL COLUMNS
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

            linesCleared =
                rowsCleared +
                        columnsCleared,

            rowsCleared =
                rowsCleared,

            columnsCleared =
                columnsCleared
        )
    }

    // ============================================================
    // POSITION EVALUATION
    // ============================================================

    private fun evaluatePosition(
        board: BlockBlitzBoard,
        linesCleared: Int,
        shape: List<Pair<Int, Int>>
    ): Double {

        val occupied =
            board.occupiedCount()

        val totalCells =
            board.rows *
                    board.columns

        val emptyCells =
            totalCells -
                    occupied

        val mobility =
            calculateMobility(
                board
            )

        val holes =
            calculateHoles(
                board
            )

        val fragmentation =
            calculateFragmentation(
                board
            )

        val density =
            occupied.toDouble() /
                    totalCells.toDouble()

        /*
         * Conservative scoring.
         *
         * Line clear is the strongest signal.
         * We preserve playable empty space.
         * Dense/fragmented positions receive
         * penalties.
         */

        val lineScore =
            linesCleared *
                    1200.0

        val emptyScore =
            emptyCells *
                    2.0

        val mobilityScore =
            mobility *
                    4.0

        val holePenalty =
            holes *
                    25.0

        val fragmentationPenalty =
            fragmentation *
                    5.0

        val densityPenalty =
            if (density > 0.75) {
                (density - 0.75) *
                        300.0
            } else {
                0.0
            }

        /*
         * Slight preference for smaller
         * pieces occupying fewer cells.
         *
         * This avoids unnecessarily aggressive
         * board filling when alternatives exist.
         */

        val piecePenalty =
            shape.size *
                    0.25

        return (
            lineScore +
                    emptyScore +
                    mobilityScore -
                    holePenalty -
                    fragmentationPenalty -
                    densityPenalty -
                    piecePenalty
            )
    }

    // ============================================================
    // MOBILITY
    // ============================================================

    private fun calculateMobility(
        board: BlockBlitzBoard
    ): Int {

        var mobility =
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

                /*
                 * Empty cells surrounded by some
                 * blocks can still be strategically
                 * useful.
                 */

                if (
                    neighbors >= 1
                ) {
                    mobility++
                }
            }
        }

        return mobility
    }

    // ============================================================
    // HOLES
    // ============================================================

    private fun calculateHoles(
        board: BlockBlitzBoard
    ): Int {

        var holes =
            0

        for (row in 1 until board.rows - 1) {

            for (
                column
                in 1 until board.columns - 1
            ) {

                if (
                    board.occupied[
                        row
                    ][column]
                ) {
                    continue
                }

                var occupiedNeighbors =
                    0

                if (
                    board.occupied[
                        row - 1
                    ][column]
                ) {
                    occupiedNeighbors++
                }

                if (
                    board.occupied[
                        row + 1
                    ][column]
                ) {
                    occupiedNeighbors++
                }

                if (
                    board.occupied[
                        row
                    ][column - 1]
                ) {
                    occupiedNeighbors++
                }

                if (
                    board.occupied[
                        row
                    ][column + 1]
                ) {
                    occupiedNeighbors++
                }

                if (
                    occupiedNeighbors >= 3
                ) {
                    holes++
                }
            }
        }

        return holes
    }

    // ============================================================
    // FRAGMENTATION
    // ============================================================

    private fun calculateFragmentation(
        board: BlockBlitzBoard
    ): Int {

        var fragmentation =
            0

        for (row in 0 until board.rows) {

            for (
                column
                in 0 until board.columns
            ) {

                if (
                    !board.occupied[
                        row
                    ][column]
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

                /*
                 * Isolated blocks increase
                 * fragmentation.
                 */

                if (
                    neighbors == 0
                ) {
                    fragmentation += 3

                } else if (
                    neighbors == 1
                ) {
                    fragmentation++
                }
            }
        }

        return fragmentation
    }

    // ============================================================
    // REASON
    // ============================================================

    private fun buildReason(
        linesCleared: Int,
        score: Double
    ): String {

        return when {

            linesCleared >= 2 ->
                "Clears $linesCleared lines"

            linesCleared == 1 ->
                "Clears 1 line"

            score >= 100 ->
                "Preserves mobility and open space"

            score >= 50 ->
                "Maintains a safe board position"

            else ->
                "Legal placement with lowest risk"
        }
    }
}
