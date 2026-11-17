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

    val cellCount: Int
        get() = cells.size

    val minRow: Int
        get() = cells.minOfOrNull {
            it.row
        } ?: 0

    val maxRow: Int
        get() = cells.maxOfOrNull {
            it.row
        } ?: 0

    val minColumn: Int
        get() = cells.minOfOrNull {
            it.column
        } ?: 0

    val maxColumn: Int
        get() = cells.maxOfOrNull {
            it.column
        } ?: 0

    val width: Int
        get() =
            if (cells.isEmpty()) {
                0
            } else {
                maxColumn - minColumn + 1
            }

    val height: Int
        get() =
            if (cells.isEmpty()) {
                0
            } else {
                maxRow - minRow + 1
            }

    /**
     * Normalized shape.
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
                        row =
                            it.row - rowOffset,
                        column =
                            it.column - columnOffset
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
     * Rotate piece 90 degrees clockwise.
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
                                it.row - minRow,
                            column =
                                it.column - minColumn
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

    fun isInside(
        row: Int,
        column: Int
    ): Boolean {

        return row >= 0 &&
                row < rows &&
                column >= 0 &&
                column < columns
    }

    fun isOccupied(
        row: Int,
        column: Int
    ): Boolean {

        if (!isInside(row, column)) {
            return false
        }

        return occupied[row][column]
    }

    fun isEmpty(
        row: Int,
        column: Int
    ): Boolean {

        return isInside(row, column) &&
                !occupied[row][column]
    }

    /**
     * Number of occupied cells.
     *
     * IMPORTANT:
     * This is a PROPERTY, not a function.
     *
     * Correct:
     * board.occupiedCount
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

    val emptyCount: Int
        get() {

            return rows * columns -
                    occupiedCount
        }

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

    val confidence: Float = 0f,

    /**
     * Detected board bounds in screenshot coordinates.
     */
    val boardLeft: Float = 0f,

    val boardTop: Float = 0f,

    val boardRight: Float = 0f,

    val boardBottom: Float = 0f

) {

    /**
     * Confidence converted to percentage.
     *
     * Example:
     * 0.87f -> 87
     */
    val confidencePercent: Int
        get() {

            return (
                confidence
                    .coerceIn(0f, 1f) *
                        100f
                )
                .toInt()
        }

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
     * Confidence threshold check.
     */
    fun hasConfidence(
        minimum: Float
    ): Boolean {

        return confidence >= minimum
    }

    /**
     * Board width in screenshot coordinates.
     */
    val boardWidth: Float
        get() =
            (boardRight - boardLeft)
                .coerceAtLeast(0f)

    /**
     * Board height in screenshot coordinates.
     */
    val boardHeight: Float
        get() =
            (boardBottom - boardTop)
                .coerceAtLeast(0f)

    /**
     * Whether board bounds are valid.
     */
    val hasBoardBounds: Boolean
        get() {

            return boardRight > boardLeft &&
                    boardBottom > boardTop
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

    val isUsable: Boolean
        get() {

            return success &&
                    state != null &&
                    state.isUsable
        }
}
