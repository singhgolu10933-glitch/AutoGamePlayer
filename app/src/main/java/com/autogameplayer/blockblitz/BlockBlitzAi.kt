package com.autogameplayer.blockblitz

data class BlockBlitzMove(
    val pieceIndex: Int,
    val row: Int,
    val column: Int,
    val score: Double,
    val reason: String
)

object BlockBlitzAi {

    private const val ROWS = 8
    private const val COLUMNS = 8

    fun findBestMove(
        state: BlockBlitzState
    ): BlockBlitzMove? {

        val board = state.board ?: return null

        var bestMove: BlockBlitzMove? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (piece in state.pieces) {

            if (!piece.detected || piece.cells.isEmpty()) {
                continue
            }

            val normalized =
                normalizeShape(piece.cells)

            for (row in 0 until ROWS) {

                for (column in 0 until COLUMNS) {

                    if (!canPlace(
                            board,
                            normalized,
                            row,
                            column
                        )
                    ) {
                        continue
                    }

                    val simulation =
                        simulatePlacement(
                            board,
                            normalized,
                            row,
                            column
                        )

                    val score =
                        evaluate(
                            simulation.board,
                            simulation.linesCleared,
                            normalized
                        )

                    if (score > bestScore) {

                        bestScore = score

                        bestMove =
                            BlockBlitzMove(
                                pieceIndex = piece.index,
                                row = row,
                                column = column,
                                score = score,
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

        return bestMove
    }

    private fun normalizeShape(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (cells.isEmpty()) {
            return emptyList()
        }

        val minRow =
            cells.minOf { it.first }

        val minColumn =
            cells.minOf { it.second }

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

    private fun canPlace(
        board: BlockBlitzBoard,
        shape: List<Pair<Int, Int>>,
        startRow: Int,
        startColumn: Int
    ): Boolean {

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

    private data class Simulation(
        val board: BlockBlitzBoard,
        val linesCleared: Int
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

        for ((dr, dc) in shape) {

            val row =
                startRow + dr

            val column =
                startColumn + dc

            if (
                row in cells.indices &&
                column in cells[row].indices
            ) {
                cells[row][column] = true
            }
        }

        var cleared = 0

        // Full rows
        for (row in 0 until board.rows) {

            var full = true

            for (column in 0 until board.columns) {

                if (!cells[row][column]) {
                    full = false
                    break
                }
            }

            if (full) {

                cleared++

                for (column in 0 until board.columns) {
                    cells[row][column] = false
                }
            }
        }

        // Full columns
        for (column in 0 until board.columns) {

            var full = true

            for (row in 0 until board.rows) {

                if (!cells[row][column]) {
                    full = false
                    break
                }
            }

            if (full) {

                cleared++

                for (row in 0 until board.rows) {
                    cells[row][column] = false
                }
            }
        }

        return Simulation(
            board =
                BlockBlitzBoard(
                    rows = board.rows,
                    columns = board.columns,
                    occupied = cells
                ),
            linesCleared = cleared
        )
    }

    private fun evaluate(
        board: BlockBlitzBoard,
        linesCleared: Int,
        shape: List<Pair<Int, Int>>
    ): Double {

        val occupied =
            board.occupiedCount()

        val total =
            board.rows * board.columns

        val empty =
            total - occupied

        val mobility =
            countEmptyCellsWithNeighbors(
                board
            )

        val holes =
            estimateHoles(
                board
            )

        /*
         * Human-like conservative scoring:
         *
         * Line clears are strongly rewarded.
         * Empty space and mobility are preserved.
         * Holes/fragmentation are penalized.
         */

        return (
            linesCleared * 1000.0
                    +
                    empty * 2.5
                    +
                    mobility * 1.5
                    -
                    holes * 12.0
                    -
                    shape.size * 0.25
            )
    }

    private fun countEmptyCellsWithNeighbors(
        board: BlockBlitzBoard
    ): Int {

        var count = 0

        for (row in 0 until board.rows) {

            for (column in 0 until board.columns) {

                if (board.occupied[row][column]) {
                    continue
                }

                var neighbors = 0

                if (
                    row > 0 &&
                    board.occupied[row - 1][column]
                ) {
                    neighbors++
                }

                if (
                    row + 1 < board.rows &&
                    board.occupied[row + 1][column]
                ) {
                    neighbors++
                }

                if (
                    column > 0 &&
                    board.occupied[row][column - 1]
                ) {
                    neighbors++
                }

                if (
                    column + 1 < board.columns &&
                    board.occupied[row][column + 1]
                ) {
                    neighbors++
                }

                if (neighbors >= 2) {
                    count++
                }
            }
        }

        return count
    }

    private fun estimateHoles(
        board: BlockBlitzBoard
    ): Int {

        var holes = 0

        for (row in 1 until board.rows - 1) {

            for (column in 1 until board.columns - 1) {

                if (board.occupied[row][column]) {
                    continue
                }

                var occupiedNeighbors = 0

                if (board.occupied[row - 1][column]) {
                    occupiedNeighbors++
                }

                if (board.occupied[row + 1][column]) {
                    occupiedNeighbors++
                }

                if (board.occupied[row][column - 1]) {
                    occupiedNeighbors++
                }

                if (board.occupied[row][column + 1]) {
                    occupiedNeighbors++
                }

                if (occupiedNeighbors >= 3) {
                    holes++
                }
            }
        }

        return holes
    }

    private fun buildReason(
        linesCleared: Int,
        score: Double
    ): String {

        return when {
            linesCleared >= 2 ->
                "Clears $linesCleared lines"

            linesCleared == 1 ->
                "Clears 1 line"

            score > 40 ->
                "Preserves board mobility"

            else ->
                "Creates a safe placement"
        }
    }
}
