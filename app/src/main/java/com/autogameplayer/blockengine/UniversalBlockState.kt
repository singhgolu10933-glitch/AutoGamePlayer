package com.autogameplayer.blockengine

/**
 * A single cell on a block-puzzle board.
 */
data class UniversalCell(
    val row: Int,
    val column: Int
)

/**
 * A detected block piece.
 *
 * The cells are relative coordinates belonging to this piece.
 * Example:
 *
 * X
 * X
 * X
 *
 * becomes:
 * (0,0)
 * (1,0)
 * (2,0)
 */
data class UniversalBlockPiece(
    val id: Int,
    val cells: List<UniversalCell>
) {

    /**
     * Returns the piece normalized so that its
     * minimum row and column are both zero.
     */
    val normalizedCells: List<UniversalCell>
        get() {
            if (cells.isEmpty()) {
                return emptyList()
            }

            val minRow =
                cells.minOf { it.row }

            val minColumn =
                cells.minOf { it.column }

            return cells
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
        }

    /**
     * Width of the normalized piece.
     */
    val width: Int
        get() {
            val normalized = normalizedCells

            if (normalized.isEmpty()) {
                return 0
            }

            return normalized.maxOf {
                it.column
            } + 1
        }

    /**
     * Height of the normalized piece.
     */
    val height: Int
        get() {
            val normalized = normalizedCells

            if (normalized.isEmpty()) {
                return 0
            }

            return normalized.maxOf {
                it.row
            } + 1
        }

    /**
     * Number of blocks/cells in this piece.
     */
    val cellCount: Int
        get() = normalizedCells.size
}

/**
 * Universal block-puzzle board.
 *
 * occupied[row][column] == true
 * means that cell is occupied.
 */
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

            val safeRows =
                rows.coerceAtLeast(1)

            val safeColumns =
                columns.coerceAtLeast(1)

            return UniversalBoard(
                rows = safeRows,
                columns = safeColumns,
                occupied = Array(
                    safeRows
                ) {
                    BooleanArray(
                        safeColumns
                    )
                }
            )
        }

        /**
         * Creates a board from an existing
         * Boolean matrix.
         */
        fun from(
            source: Array<BooleanArray>
        ): UniversalBoard {

            if (source.isEmpty()) {
                return empty(
                    rows = 1,
                    columns = 1
                )
            }

            val rows =
                source.size

            val columns =
                source.maxOfOrNull {
                    it.size
                } ?: 1

            val board =
                Array(rows) {
                    BooleanArray(columns)
                }

            for (row in source.indices) {

                for (
                    column in
                    source[row].indices
                ) {

                    board[row][column] =
                        source[row][column]
                }
            }

            return UniversalBoard(
                rows = rows,
                columns = columns,
                occupied = board
            )
        }
    }

    /**
     * Safely reads a board cell.
     */
    fun isOccupied(
        row: Int,
        column: Int
    ): Boolean {

        if (
            row < 0 ||
            row >= rows ||
            column < 0 ||
            column >= columns
        ) {
            return false
        }

        if (
            row >= occupied.size ||
            column >= occupied[row].size
        ) {
            return false
        }

        return occupied[row][column]
    }

    /**
     * Creates a deep copy of the board.
     */
    fun copyBoard(): UniversalBoard {

        val copied =
            Array(rows) { row ->

                BooleanArray(columns) { column ->

                    isOccupied(
                        row,
                        column
                    )
                }
            }

        return UniversalBoard(
            rows = rows,
            columns = columns,
            occupied = copied
        )
    }

    /**
     * Counts occupied cells.
     */
    fun occupiedCount(): Int {

        var count = 0

        for (row in 0 until rows) {

            for (
                column in
                0 until columns
            ) {

                if (
                    isOccupied(
                        row,
                        column
                    )
                ) {
                    count++
                }
            }
        }

        return count
    }

    /**
     * Counts empty cells.
     */
    fun emptyCount(): Int {

        return (
            rows * columns
        ) - occupiedCount()
    }

    /**
     * Checks whether the board dimensions
     * are valid.
     */
    fun isValid(): Boolean {

        if (
            rows <= 0 ||
            columns <= 0
        ) {
            return false
        }

        if (
            occupied.size != rows
        ) {
            return false
        }

        for (row in occupied) {

            if (
                row.size != columns
            ) {
                return false
            }
        }

        return true
    }
}

/**
 * Complete universal block-game state.
 *
 * This is the common format used by:
 *
 * Screen
 *   ↓
 * UniversalVision
 *   ↓
 * UniversalBlockState
 *   ↓
 * UniversalBlockSolver
 */
data class UniversalBlockState(
    val board: UniversalBoard?,
    val pieces: List<UniversalBlockPiece>,
    val confidence: Float,

    /**
     * Board position in normalized screen coordinates.
     */
    val boardLeft: Float = 0f,

    val boardTop: Float = 0f,

    val boardRight: Float = 1f,

    val boardBottom: Float = 1f
) {

    /**
     * Returns true when a usable board
     * has been detected.
     */
    val boardDetected: Boolean
        get() =
            board != null &&
                    board.isValid()

    /**
     * Number of detected pieces.
     */
    val detectedPieceCount: Int
        get() =
            pieces.count {
                it.normalizedCells.isNotEmpty()
            }

    /**
     * Returns confidence as percentage.
     */
    val confidencePercent: Int
        get() =
            (
                confidence * 100f
            )
                .toInt()
                .coerceIn(
                    0,
                    100
                )

    /**
     * Returns true when this state
     * contains enough information for
     * the solver to attempt a move.
     */
    val isUsable: Boolean
        get() =
            boardDetected &&
                    pieces.isNotEmpty() &&
                    confidence > 0f
}

/**
 * Result returned by vision processing.
 *
 * Keeping this separate makes it easier to
 * expand the vision system later without
 * changing the solver API.
 */
data class UniversalVisionResult(
    val state: UniversalBlockState,
    val boardDetected: Boolean,
    val piecesDetected: Int,
    val confidence: Float
)
