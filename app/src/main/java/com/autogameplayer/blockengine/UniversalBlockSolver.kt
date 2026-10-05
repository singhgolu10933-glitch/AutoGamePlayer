package com.autogameplayer.blockengine

/**
 * Universal solver for block-placement puzzle games.
 *
 * It does NOT interact with the game.
 *
 * Pipeline:
 *
 * Board + Pieces
 *      ↓
 * Legal move search
 *      ↓
 * Simulation
 *      ↓
 * Line clearing
 *      ↓
 * Position evaluation
 *      ↓
 * Best Move
 */
object UniversalBlockSolver {

    /**
     * Represents the best move selected by the solver.
     */
    data class Result(
        val pieceId: Int,
        val row: Int,
        val column: Int,
        val score: Double,
        val clearedRows: Int,
        val clearedColumns: Int,
        val clearedLines: Int,
        val reason: String
    )

    /**
     * Finds the best legal placement.
     */
    fun findBestMove(
        board: UniversalBoard,
        pieces: List<UniversalBlockPiece>
    ): Result? {

        if (!board.isValid()) {
            return null
        }

        if (pieces.isEmpty()) {
            return null
        }

        var bestMove: Result? = null

        for (piece in pieces) {

            val shape =
                piece.normalizedCells

            if (shape.isEmpty()) {
                continue
            }

            val height =
                piece.height

            val width =
                piece.width

            if (
                height <= 0 ||
                width <= 0
            ) {
                continue
            }

            val maxRow =
                board.rows - height

            val maxColumn =
                board.columns - width

            if (
                maxRow < 0 ||
                maxColumn < 0
            ) {
                continue
            }

            for (
                row in
                0..maxRow
            ) {

                for (
                    column in
                    0..maxColumn
                ) {

                    if (
                        !canPlace(
                            board = board,
                            piece = shape,
                            row = row,
                            column = column
                        )
                    ) {
                        continue
                    }

                    val simulation =
                        simulatePlacement(
                            board = board,
                            piece = shape,
                            row = row,
                            column = column
                        )

                    val score =
                        evaluatePosition(
                            before = board,
                            after = simulation.board,
                            piece = piece,
                            clearedRows =
                                simulation.clearedRows,
                            clearedColumns =
                                simulation.clearedColumns
                        )

                    val clearedLines =
                        simulation.clearedRows +
                                simulation.clearedColumns

                    val reason =
                        buildReason(
                            clearedRows =
                                simulation.clearedRows,
                            clearedColumns =
                                simulation.clearedColumns,
                            score = score
                        )

                    val candidate =
                        Result(
                            pieceId = piece.id,
                            row = row,
                            column = column,
                            score = score,
                            clearedRows =
                                simulation.clearedRows,
                            clearedColumns =
                                simulation.clearedColumns,
                            clearedLines =
                                clearedLines,
                            reason = reason
                        )

                    if (
                        isBetterMove(
                            candidate,
                            bestMove
                        )
                    ) {
                        bestMove = candidate
                    }
                }
            }
        }

        return bestMove
    }

    /**
     * Checks whether a piece can legally be placed.
     */
    fun canPlace(
        board: UniversalBoard,
        piece: List<UniversalCell>,
        row: Int,
        column: Int
    ): Boolean {

        if (!board.isValid()) {
            return false
        }

        if (piece.isEmpty()) {
            return false
        }

        for (cell in piece) {

            val targetRow =
                row + cell.row

            val targetColumn =
                column + cell.column

            if (
                targetRow < 0 ||
                targetRow >= board.rows ||
                targetColumn < 0 ||
                targetColumn >= board.columns
            ) {
                return false
            }

            if (
                board.isOccupied(
                    targetRow,
                    targetColumn
                )
            ) {
                return false
            }
        }

        return true
    }

    /**
     * Simulates a placement and removes
     * completed rows and columns.
     */
    private fun simulatePlacement(
        board: UniversalBoard,
        piece: List<UniversalCell>,
        row: Int,
        column: Int
    ): SimulationResult {

        val simulated =
            board.copyBoard()

        for (cell in piece) {

            val targetRow =
                row + cell.row

            val targetColumn =
                column + cell.column

            if (
                targetRow in 0 until simulated.rows &&
                targetColumn in 0 until simulated.columns
            ) {

                simulated.occupied[targetRow][targetColumn] =
                    true
            }
        }

        val fullRows =
            mutableListOf<Int>()

        val fullColumns =
            mutableListOf<Int>()

        for (
            currentRow in
            0 until simulated.rows
        ) {

            var full = true

            for (
                currentColumn in
                0 until simulated.columns
            ) {

                if (
                    !simulated.occupied[
                        currentRow
                    ][
                        currentColumn
                    ]
                ) {
                    full = false
                    break
                }
            }

            if (full) {
                fullRows.add(currentRow)
            }
        }

        for (
            currentColumn in
            0 until simulated.columns
        ) {

            var full = true

            for (
                currentRow in
                0 until simulated.rows
            ) {

                if (
                    !simulated.occupied[
                        currentRow
                    ][
                        currentColumn
                    ]
                ) {
                    full = false
                    break
                }
            }

            if (full) {
                fullColumns.add(
                    currentColumn
                )
            }
        }

        for (currentRow in fullRows) {

            for (
                currentColumn in
                0 until simulated.columns
            ) {

                simulated.occupied[
                    currentRow
                ][
                    currentColumn
                ] = false
            }
        }

        for (currentColumn in fullColumns) {

            for (
                currentRow in
                0 until simulated.rows
            ) {

                simulated.occupied[
                    currentRow
                ][
                    currentColumn
                ] = false
            }
        }

        return SimulationResult(
            board = simulated,
            clearedRows = fullRows.size,
            clearedColumns =
                fullColumns.size
        )
    }

    /**
     * Evaluates the resulting board.
     *
     * The scoring is intentionally moderate:
     * - clearing lines is highly valuable
     * - keeping empty space is valuable
     * - excessive fragmentation is penalized
     * - excessive board occupancy is penalized
     */
    private fun evaluatePosition(
        before: UniversalBoard,
        after: UniversalBoard,
        piece: UniversalBlockPiece,
        clearedRows: Int,
        clearedColumns: Int
    ): Double {

        var score = 0.0

        val clearedLines =
            clearedRows +
                    clearedColumns

        /*
         * Line clearing.
         */
        score +=
            clearedLines *
                    1000.0

        /*
         * Additional reward for
         * multiple simultaneous clears.
         */
        when {
            clearedLines >= 4 -> {
                score += 1200.0
            }

            clearedLines == 3 -> {
                score += 700.0
            }

            clearedLines == 2 -> {
                score += 300.0
            }
        }

        /*
         * Empty-space reward.
         */
        score +=
            after.emptyCount() *
                    2.0

        /*
         * Lower occupancy is generally safer.
         */
        score -=
            after.occupiedCount() *
                    1.5

        /*
         * Reward pieces that fit compactly.
         */
        score +=
            piece.cellCount *
                    1.5

        /*
         * Penalize isolated empty holes.
         */
        score -=
            countSmallHoles(after) *
                    8.0

        /*
         * Penalize fragmented occupied cells.
         */
        score -=
            countFragmentation(after) *
                    3.0

        /*
         * Preserve some mobility.
         */
        val mobility =
            estimateMobility(after)

        score +=
            mobility *
                    4.0

        /*
         * Small preference for improving
         * the board compared with its
         * original state.
         */
        val occupancyDifference =
            before.occupiedCount() -
                    after.occupiedCount()

        score +=
            occupancyDifference *
                    1.0

        return score
    }

    /**
     * Determines which candidate is better.
     */
    private fun isBetterMove(
        candidate: Result,
        current: Result?
    ): Boolean {

        if (current == null) {
            return true
        }

        /*
         * First priority:
         * more cleared lines.
         */
        if (
            candidate.clearedLines !=
            current.clearedLines
        ) {

            return candidate.clearedLines >
                    current.clearedLines
        }

        /*
         * Second priority:
         * higher evaluation score.
         */
        if (
            candidate.score !=
            current.score
        ) {

            return candidate.score >
                    current.score
        }

        /*
         * Deterministic tie breakers.
         */
        if (
            candidate.pieceId !=
            current.pieceId
        ) {

            return candidate.pieceId <
                    current.pieceId
        }

        if (
            candidate.row !=
            current.row
        ) {

            return candidate.row <
                    current.row
        }

        return candidate.column <
                current.column
    }

    /**
     * Counts small enclosed empty areas.
     */
    private fun countSmallHoles(
        board: UniversalBoard
    ): Int {

        var holes = 0

        for (
            row in
            0 until board.rows
        ) {

            for (
                column in
                0 until board.columns
            ) {

                if (
                    board.isOccupied(
                        row,
                        column
                    )
                ) {
                    continue
                }

                val neighbours =
                    countOccupiedNeighbours(
                        board,
                        row,
                        column
                    )

                if (
                    neighbours >= 3
                ) {
                    holes++
                }
            }
        }

        return holes
    }

    /**
     * Counts occupied cells that are
     * surrounded by relatively few
     * empty neighbours.
     */
    private fun countFragmentation(
        board: UniversalBoard
    ): Int {

        var fragmentation = 0

        for (
            row in
            0 until board.rows
        ) {

            for (
                column in
                0 until board.columns
            ) {

                if (
                    !board.isOccupied(
                        row,
                        column
                    )
                ) {
                    continue
                }

                val occupiedNeighbours =
                    countOccupiedNeighbours(
                        board,
                        row,
                        column
                    )

                if (
                    occupiedNeighbours <= 1
                ) {
                    fragmentation++
                }
            }
        }

        return fragmentation
    }

    /**
     * Estimates how many neighbouring
     * cells remain available.
     */
    private fun estimateMobility(
        board: UniversalBoard
    ): Int {

        var mobility = 0

        for (
            row in
            0 until board.rows
        ) {

            for (
                column in
                0 until board.columns
            ) {

                if (
                    board.isOccupied(
                        row,
                        column
                    )
                ) {
                    continue
                }

                val emptyNeighbours =
                    countEmptyNeighbours(
                        board,
                        row,
                        column
                    )

                mobility +=
                    emptyNeighbours
            }
        }

        return mobility
    }

    /**
     * Counts occupied neighbours.
     */
    private fun countOccupiedNeighbours(
        board: UniversalBoard,
        row: Int,
        column: Int
    ): Int {

        var count = 0

        if (
            board.isOccupied(
                row - 1,
                column
            )
        ) {
            count++
        }

        if (
            board.isOccupied(
                row + 1,
                column
            )
        ) {
            count++
        }

        if (
            board.isOccupied(
                row,
                column - 1
            )
        ) {
            count++
        }

        if (
            board.isOccupied(
                row,
                column + 1
            )
        ) {
            count++
        }

        return count
    }

    /**
     * Counts empty neighbours.
     */
    private fun countEmptyNeighbours(
        board: UniversalBoard,
        row: Int,
        column: Int
    ): Int {

        var count = 0

        if (
            !board.isOccupied(
                row - 1,
                column
            ) &&
            row - 1 >= 0
        ) {
            count++
        }

        if (
            !board.isOccupied(
                row + 1,
                column
            ) &&
            row + 1 < board.rows
        ) {
            count++
        }

        if (
            !board.isOccupied(
                row,
                column - 1
            ) &&
            column - 1 >= 0
        ) {
            count++
        }

        if (
            !board.isOccupied(
                row,
                column + 1
            ) &&
            column + 1 < board.columns
        ) {
            count++
        }

        return count
    }

    /**
     * Human-readable explanation for the UI.
     */
    private fun buildReason(
        clearedRows: Int,
        clearedColumns: Int,
        score: Double
    ): String {

        val totalLines =
            clearedRows +
                    clearedColumns

        return when {
            totalLines >= 4 ->
                "Clears $totalLines lines; strong move"

            totalLines == 3 ->
                "Clears 3 lines"

            totalLines == 2 ->
                "Clears 2 lines"

            totalLines == 1 ->
                "Clears 1 line"

            score > 100.0 ->
                "Best safe placement"

            else ->
                "Best available placement"
        }
    }

    /**
     * Internal simulation result.
     */
    private data class SimulationResult(
        val board: UniversalBoard,
        val clearedRows: Int,
        val clearedColumns: Int
    )
}
