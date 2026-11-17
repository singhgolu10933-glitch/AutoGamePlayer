package com.autogameplayer.blockengine

/**
 * ============================================================
 * UNIVERSAL BLOCK STATE
 * ============================================================
 *
 * Common data models used by:
 *
 * UniversalVision
 * UniversalPieceDetector
 * UniversalGridDetector
 * Game Brain
 * Move Planner
 *
 * These models are intentionally independent
 * from any specific game.
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
     * Minimum row occupied by the piece.
     */
    val minRow: Int
        get() = cells.minOfOrNull {
            it.row
        } ?: 0

    /**
     * Maximum row occupied by the piece.
     */
    val maxRow: Int
        get() = cells.maxOfOrNull {
            it.row
        } ?: 0

    /**
     * Minimum column occupied by the piece.
     */
    val minColumn: Int
        get() = cells.minOfOrNull {
            it.column
        } ?: 0

    /**
     * Maximum column occupied by the piece.
     */
    val maxColumn: Int
        get() = cells.maxOfOrNull {
            it.column
        } ?: 0

    /**
     * Width of the piece.
     */
    val width: Int
        get() = if (cells.isEmpty()) {
            0
        } else {
            maxColumn - minColumn + 1
        }

    /**
     * Height of the piece.
     */
    val height: Int
        get() = if (cells.isEmpty()) {
            0
        } else {
            maxRow - minRow + 1
        }

    /**
     * Returns normalized cells.
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
     * Checks whether the piece contains
     * a particular relative cell.
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
     * Returns a rotated version of this piece.
     *
     * Rotation:
     *
     * (row, column)
     * →
     * (column, -row)
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
            cells = rotated
                .map {
                    UniversalCell(
                        row = it.row - minRow,
                        column = it.column - minColumn
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

    /**
     * Safe empty-board factory.
     */
    companion object {

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
     * Checks whether a coordinate is inside the board.
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
     * Returns whether a cell is occupied.
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
     * Returns whether a cell is empty.
     */
    fun isEmpty(
        row: Int,
        column: Int
    ): Boolean {

        return isInside(row, column) &&
                !occupied[row][column]
    }

    /**
     * Number of occupied cells.
     */
    val occupiedCount: Int
        get() {

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
    val emptyCount: Int
        get() {

            return rows * columns -
                    occupiedCount
        }

    /**
     * Creates a safe copy of the board.
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
// UNIVERSAL BLOCK STATE
// ============================================================

data class UniversalBlockState(
    val board: UniversalBoard?,
    val pieces: List<UniversalBlockPiece>,
    val confidence: Float = 0f
) {

    /**
     * Whether the detected state is usable.
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
        get() = pieces.size

    /**
     * Returns the highest-confidence
     * interpretation threshold check.
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
