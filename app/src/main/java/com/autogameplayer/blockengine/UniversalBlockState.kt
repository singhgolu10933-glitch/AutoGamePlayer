package com.autogameplayer.blockengine

/**
 * ============================================================
 * UNIVERSAL BLOCK STATE
 * ============================================================
 *
 * Shared data models for:
 *
 * UniversalVision
 * UniversalPieceDetector
 * UniversalGridDetector
 * UniversalBlockSolver
 * Game Brain
 * Move Planner
 *
 * This file contains DATA MODELS ONLY.
 * It does not interact with the Android UI or game.
 * ============================================================
 */


// ============================================================
// UNIVERSAL CELL
// ============================================================

data class UniversalCell(
    val row: Int,
    val column: Int
) {

    /**
     * Manhattan distance between two cells.
     */
    fun distanceTo(
        other: UniversalCell
    ): Int {

        return kotlin.math.abs(row - other.row) +
                kotlin.math.abs(column - other.column)
    }
}


// ============================================================
// UNIVERSAL BLOCK PIECE
// ============================================================

data class UniversalBlockPiece(
    val id: Int,
    val cells: List<UniversalCell>
) {

    /**
     * Number of cells in this piece.
     */
    val cellCount: Int
        get() = cells.size

    /**
     * Minimum row.
     */
    val minRow: Int
        get() =
            cells.minOfOrNull {
                it.row
            } ?: 0

    /**
     * Maximum row.
     */
    val maxRow: Int
        get() =
            cells.maxOfOrNull {
                it.row
            } ?: 0

    /**
     * Minimum column.
     */
    val minColumn: Int
        get() =
            cells.minOfOrNull {
                it.column
            } ?: 0

    /**
     * Maximum column.
     */
    val maxColumn: Int
        get() =
            cells.maxOfOrNull {
                it.column
            } ?: 0

    /**
     * Width of piece.
     */
    val width: Int
        get() =
            if (cells.isEmpty()) {
                0
            } else {
                maxColumn - minColumn + 1
            }

    /**
     * Height of piece.
     */
    val height: Int
        get() =
            if (cells.isEmpty()) {
                0
            } else {
                maxRow - minRow + 1
            }

    /**
     * Normalized representation.
     *
     * Example:
     *
     * (3,5)
     * (4,5)
     * (4,6)
     *
     * becomes:
     *
     * (0,0)
     * (1,0)
     * (1,1)
     */
    val normalizedCells: List<UniversalCell>
        get() {

            if (cells.isEmpty()) {
                return emptyList()
            }

            val rowOffset =
                cells.minOf {
                    it.row
                }

            val columnOffset =
                cells.minOf {
                    it.column
                }

            return cells
                .map {
                    UniversalCell(
                        row = it.row - rowOffset,
                        column = it.column - columnOffset
                    )
                }
                .distinct()
                .sortedWith(
                    compareBy<UniversalCell> {
                        it.row
                    }.thenBy {
                        it.column
                    }
                )
        }

    /**
     * Check whether a cell exists in this piece.
     */
    fun contains(
        row: Int,
        column: Int
    ): Boolean {

        return cells.any {
            it.row == row &&
                    it.column == column
        }
    }

    /**
     * Rotate piece by 90 degrees.
     */
    fun rotate90(): UniversalBlockPiece {

        if (cells.isEmpty()) {
            return this
        }

        val rotated =
            cells.map {
                UniversalCell(
                    row = it.column,
                    column = -it.row
                )
            }

        val minRow =
            rotated.minOf {
                it.row
            }

        val minColumn =
            rotated.minOf {
                it.column
            }

        return copy(
            cells =
                rotated
                    .map {
                        UniversalCell(
                            row =
                                it.row -
                                        minRow,

                            column =
                                it.column -
                                        minColumn
                        )
                    }
                    .distinct()
                    .sortedWith(
                        compareBy<UniversalCell> {
                            it.row
                        }.thenBy {
                            it.column
                        }
                    )
        )
    }
}


// ============================================================
// UNIVERSAL BOARD
// ============================================================

data class UniversalBoard(
    val rows: Int,
    val columns: Int,
    val occupied: Array<BooleanArray>
) {

    companion object {

        /**
         * Creates an empty board.
         */
        fun empty(
            rows: Int,
            columns: Int
        ): UniversalBoard {

            return UniversalBoard(
                rows = rows,
                columns = columns,
                occupied =
                    Array(rows) {
                        BooleanArray(columns)
                    }
            )
        }
    }

    /**
     * Check whether coordinate is inside board.
     */
    fun isInside(
        row: Int,
        column: Int
    ): Boolean {

        return row >= 0 &&
                row < rows &&
                column >= 0 &&
                column < columns
    }

    /**
     * Check whether cell is occupied.
     */
    fun isOccupied(
        row: Int,
        column: Int
    ): Boolean {

        if (!isInside(row, column)) {
            return false
        }

        return occupied[row][column]
    }

    /**
     * Check whether cell is empty.
     */
    fun isEmpty(
        row: Int,
        column: Int
    ): Boolean {

        return isInside(row, column) &&
                !occupied[row][column]
    }

    /**
     * ========================================================
     * IMPORTANT:
     * Compatibility function required by UniversalBlockSolver.
     *
     * Solver calls:
     *
     * board.occupiedCount()
     * ========================================================
     */
    fun occupiedCount(): Int {

        var count = 0

        for (row in 0 until rows) {

            for (column in 0 until columns) {

                if (occupied[row][column]) {
                    count++
                }
            }
        }

        return count
    }

    /**
     * Number of empty cells.
     */
    fun emptyCount(): Int {

        return rows * columns -
                occupiedCount()
    }

    /**
     * Safe deep copy of board.
     */
    fun copyBoard(): UniversalBoard {

        val copied =
            Array(rows) { row ->
                occupied[row].clone()
            }

        return UniversalBoard(
            rows = rows,
            columns = columns,
            occupied = copied
        )
    }
}


// ============================================================
// UNIVERSAL MOVE
// ============================================================

/**
 * Represents a calculated move.
 *
 * This is the output of UniversalBlockSolver.
 *
 * The solver DOES NOT execute this move.
 */
data class UniversalMove(
    val pieceId: Int,
    val row: Int,
    val column: Int,
    val score: Double,
    val clearedRows: Int = 0,
    val clearedColumns: Int = 0,
    val clearedLines: Int = 0,
    val reason: String = ""
) {

    /**
     * Whether this move clears at least one line.
     */
    val clearsLine: Boolean
        get() = clearedLines > 0

    /**
     * Total number of cleared rows and columns.
     */
    val totalClears: Int
        get() =
            clearedRows +
                    clearedColumns
}


// ============================================================
// UNIVERSAL BLOCK STATE
// ============================================================

data class UniversalBlockState(
    val board: UniversalBoard?,
    val pieces: List<UniversalBlockPiece>,
    val confidence: Float = 0f
) {

    /**
     * Whether detected state can be used.
     */
    val isUsable: Boolean
        get() {

            return board != null &&
                    pieces.isNotEmpty() &&
                    confidence > 0f
        }

    /**
     * Number of detected pieces.
     */
    val pieceCount: Int
        get() =
            pieces.size

    /**
     * Confidence threshold check.
     */
    fun hasConfidence(
        minimum: Float
    ): Boolean {

        return confidence >= minimum
    }
}


// ============================================================
// UNIVERSAL VISION RESULT
// ============================================================

data class UniversalVisionResult(
    val state: UniversalBlockState?,
    val success: Boolean,
    val confidence: Float,
    val message: String = ""
) {

    /**
     * Whether vision produced a usable state.
     */
    val isUsable: Boolean
        get() {

            return success &&
                    state != null &&
                    state.isUsable
        }
}
