package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Universal vision pipeline.
 *
 * Pipeline:
 *
 * Screenshot
 *    ↓
 * Grid Detection
 *    ↓
 * Board Cell Detection
 *    ↓
 * Piece Detection
 *    ↓
 * Confidence
 *    ↓
 * UniversalBlockState
 *
 * No game-specific coordinates are used here.
 */
object UniversalVision {

    // ============================================================
    // PUBLIC ANALYSIS
    // ============================================================

    fun analyze(
        bitmap: Bitmap
    ): UniversalBlockState {

        /*
         * Invalid frame.
         */
        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return emptyState()
        }

        // ========================================================
        // 1. DETECT BOARD GRID
        // ========================================================

        val grid =
            UniversalGridDetector.detect(
                bitmap
            )

        if (grid == null) {

            return UniversalBlockState(
                board = null,
                pieces = emptyList(),
                confidence = 0f
            )
        }

        // ========================================================
        // 2. DETECT OCCUPIED BOARD CELLS
        // ========================================================

        val occupied =
            detectBoardCells(
                bitmap = bitmap,
                grid = grid
            )

        val board =
            UniversalBoard(
                rows = grid.rows,
                columns = grid.columns,
                occupied = occupied
            )

        // ========================================================
        // 3. DETECT PIECES
        // ========================================================

        val pieces =
            UniversalPieceDetector.detect(
                bitmap = bitmap,
                grid = grid
            )

        // ========================================================
        // 4. CONFIDENCE
        // ========================================================

        val gridConfidence =
            grid.score
                .coerceIn(
                    0f,
                    1f
                )

        val boardConfidence =
            calculateBoardConfidence(
                bitmap = bitmap,
                grid = grid,
                occupied = occupied
            )

        val pieceConfidence =
            calculatePieceConfidence(
                pieces = pieces
            )

        /*
         * Grid is the most important component.
         */
        val confidence =
            (
                gridConfidence * 0.45f +
                        boardConfidence * 0.30f +
                        pieceConfidence * 0.25f
                )
                .coerceIn(
                    0f,
                    1f
                )

        // ========================================================
        // 5. RETURN UNIVERSAL STATE
        // ========================================================

        return UniversalBlockState(
            board = board,
            pieces = pieces,
            confidence = confidence,

            boardLeft =
                grid.left,

            boardTop =
                grid.top,

            boardRight =
                grid.right,

            boardBottom =
                grid.bottom
        )
    }

    // ============================================================
    // EMPTY STATE
    // ============================================================

    private fun emptyState():
            UniversalBlockState {

        return UniversalBlockState(
            board = null,
            pieces = emptyList(),
            confidence = 0f
        )
    }

    // ============================================================
    // BOARD CELL DETECTION
    // ============================================================

    private fun detectBoardCells(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Array<BooleanArray> {

        val occupied =
            Array(
                grid.rows
            ) {
                BooleanArray(
                    grid.columns
                )
            }

        /*
         * For each cell we inspect a small number of points
         * around the center instead of scanning the complete cell.
         *
         * This keeps analysis fast.
         */
        for (
            row in
            0 until grid.rows
        ) {

            for (
                column in
                0 until grid.columns
            ) {

                occupied[row][column] =
                    isCellOccupied(
                        bitmap = bitmap,
                        grid = grid,
                        row = row,
                        column = column
                    )
            }
        }

        return occupied
    }

    // ============================================================
    // CELL OCCUPANCY
    // ============================================================

    private fun isCellOccupied(
        bitmap: Bitmap,
        grid: DetectedGrid,
        row: Int,
        column: Int
    ): Boolean {

        val center =
            UniversalGridDetector.cellCenter(
                grid = grid,
                row = row,
                column = column,
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height
            )
                ?: return false

        val centerX =
            center.first

        val centerY =
            center.second

        /*
         * Sample center + four inner points.
         */
        val offsets =
            arrayOf(
                0f to 0f,

                -0.22f to 0f,
                0.22f to 0f,

                0f to -0.22f,
                0f to 0.22f
            )

        var occupiedSamples =
            0

        var totalSamples =
            0

        for (
            offset in
            offsets
        ) {

            val x =
                (
                    centerX +
                            offset.first *
                            grid.cellWidth
                    )
                    .toInt()

            val y =
                (
                    centerY +
                            offset.second *
                            grid.cellHeight
                    )
                    .toInt()

            if (
                x < 0 ||
                x >= bitmap.width ||
                y < 0 ||
                y >= bitmap.height
            ) {
                continue
            }

            val pixel =
                bitmap.getPixel(
                    x,
                    y
                )

            totalSamples++

            if (
                looksOccupied(
                    pixel
                )
            ) {
                occupiedSamples++
            }
        }

        if (
            totalSamples == 0
        ) {
            return false
        }

        /*
         * At least 2/5 samples should look like a block.
         */
        return occupiedSamples >=
                2
    }

    // ============================================================
    // PIXEL CLASSIFICATION
    // ============================================================

    private fun looksOccupied(
        pixel: Int
    ): Boolean {

        val red =
            (
                pixel shr 16
            ) and 0xFF

        val green =
            (
                pixel shr 8
            ) and 0xFF

        val blue =
            pixel and 0xFF

        val maximum =
            maxOf(
                red,
                green,
                blue
            )

        val minimum =
            minOf(
                red,
                green,
                blue
            )

        val brightness =
            (
                red * 0.299f +
                        green * 0.587f +
                        blue * 0.114f
                )

        val saturation =
            maximum -
                    minimum

        /*
         * Strongly colored block.
         *
         * Blue / green / red / orange / purple etc.
         */
        if (
            saturation >= 55 &&
            brightness >= 50
        ) {
            return true
        }

        /*
         * Bright block highlights.
         */
        if (
            brightness >= 155 &&
            saturation >= 30
        ) {
            return true
        }

        /*
         * Golden / beige special blocks can have
         * comparatively low saturation.
         */
        if (
            red >= 120 &&
            green >= 90 &&
            brightness >= 105 &&
            saturation >= 25
        ) {
            return true
        }

        return false
    }

    // ============================================================
    // BOARD CONFIDENCE
    // ============================================================

    private fun calculateBoardConfidence(
        bitmap: Bitmap,
        grid: DetectedGrid,
        occupied: Array<BooleanArray>
    ): Float {

        if (
            grid.rows <= 0 ||
            grid.columns <= 0
        ) {
            return 0f
        }

        val totalCells =
            grid.rows *
                    grid.columns

        if (
            totalCells <= 0
        ) {
            return 0f
        }

        var occupiedCount =
            0

        for (
            row in
            occupied
        ) {

            for (
                cell in
                row
            ) {

                if (cell) {
                    occupiedCount++
                }
            }
        }

        /*
         * An empty board is valid.
         *
         * Therefore occupancy itself must NOT determine
         * whether the board exists.
         */

        val occupancyRatio =
            occupiedCount.toFloat() /
                    totalCells.toFloat()

        /*
         * Normal block games usually have a reasonable amount
         * of empty space.
         */
        val occupancyScore =
            when {

                occupancyRatio <= 0.90f ->
                    1f

                occupancyRatio <= 0.97f ->
                    0.75f

                else ->
                    0.45f
            }

        /*
         * Square/regular geometry confidence.
         */
        val geometryDifference =
            abs(
                grid.cellWidth -
                        grid.cellHeight
            )

        val geometryScore =
            (
                1f -
                        geometryDifference /
                        maxOf(
                            grid.cellWidth,
                            grid.cellHeight
                        )
                )
                .coerceIn(
                    0f,
                    1f
                )

        return (
            occupancyScore * 0.45f +
                    geometryScore * 0.55f
            )
            .coerceIn(
                0f,
                1f
            )
    }

    // ============================================================
    // PIECE CONFIDENCE
    // ============================================================

    private fun calculatePieceConfidence(
        pieces: List<UniversalBlockPiece>
    ): Float {

        if (
            pieces.isEmpty()
        ) {
            /*
             * No pieces can happen during:
             * - loading
             * - animation
             * - game over
             * - transition
             *
             * Therefore it is not a complete vision failure.
             */
            return 0.25f
        }

        /*
         * Most block games provide 3 pieces.
         * Other games may provide 1-5.
         */
        val countScore =
            when {

                pieces.size >= 3 ->
                    1f

                pieces.size == 2 ->
                    0.75f

                pieces.size == 1 ->
                    0.45f

                else ->
                    0.20f
            }

        /*
         * Validate that each detected shape is reasonable.
         */
        var validShapes =
            0

        for (
            piece in
            pieces
        ) {

            if (
                piece.cells.isNotEmpty() &&
                piece.cells.size <= 25
            ) {
                validShapes++
            }
        }

        val shapeScore =
            if (
                pieces.isNotEmpty()
            ) {

                validShapes.toFloat() /
                        pieces.size.toFloat()

            } else {
                0f
            }

        return (
            countScore * 0.55f +
                    shapeScore * 0.45f
            )
            .coerceIn(
                0f,
                1f
            )
    }

    // ============================================================
    // FAST BOARD OCCUPANCY HELPER
    // ============================================================

    /**
     * Returns the number of occupied cells.
     *
     * Useful for diagnostics.
     */
    fun occupiedCount(
        state: UniversalBlockState
    ): Int {

        return state.board
            ?.occupiedCount()
            ?: 0
    }

    // ============================================================
    // BOARD SIZE HELPER
    // ============================================================

    fun boardSize(
        state: UniversalBlockState
    ): String {

        val board =
            state.board
                ?: return "UNKNOWN"

        return "${board.rows}x${board.columns}"
    }
}
