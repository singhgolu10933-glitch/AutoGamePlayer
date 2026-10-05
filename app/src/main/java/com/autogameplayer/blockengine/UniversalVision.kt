package com.autogameplayer.blockengine

import android.graphics.Bitmap

/**
 * Universal vision pipeline for block-puzzle games.
 *
 * Pipeline:
 *
 * Screenshot
 *     ↓
 * Grid detection
 *     ↓
 * Board cell detection
 *     ↓
 * Piece detection
 *     ↓
 * UniversalBlockState
 *
 * This class contains no Block Blitz-specific
 * coordinates or package references.
 */
object UniversalVision {

    /**
     * Main vision entry point.
     */
    fun analyze(
        bitmap: Bitmap
    ): UniversalBlockState {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {

            return emptyState()
        }

        return try {

            /*
             * STEP 1
             *
             * Detect the game board.
             */
            val grid =
                UniversalGridDetector.detect(
                    bitmap
                )

            if (grid == null) {

                return emptyState()
            }

            /*
             * STEP 2
             *
             * Detect occupied cells.
             */
            val board =
                detectBoard(
                    bitmap,
                    grid
                )

            /*
             * STEP 3
             *
             * Detect available pieces.
             */
            val pieces =
                UniversalPieceDetector.detect(
                    bitmap,
                    grid
                )

            /*
             * STEP 4
             *
             * Calculate overall confidence.
             */
            val confidence =
                calculateConfidence(
                    gridConfidence =
                        grid.confidence,
                    board = board,
                    pieces = pieces
                )

            UniversalBlockState(
                board = board,
                pieces = pieces,
                confidence = confidence,

                boardLeft =
                    grid.left.toFloat() /
                            bitmap.width.toFloat(),

                boardTop =
                    grid.top.toFloat() /
                            bitmap.height.toFloat(),

                boardRight =
                    grid.right.toFloat() /
                            bitmap.width.toFloat(),

                boardBottom =
                    grid.bottom.toFloat() /
                            bitmap.height.toFloat()
            )

        } catch (
            exception: Exception
        ) {

            /*
             * Vision should never crash the
             * screen-capture service.
             */
            emptyState()
        }
    }

    /**
     * Detects the occupied/empty state of every
     * board cell.
     */
    private fun detectBoard(
        bitmap: Bitmap,
        grid: UniversalGridDetector.DetectedGrid
    ): UniversalBoard {

        val occupied =
            Array(
                grid.rows
            ) {
                BooleanArray(
                    grid.columns
                )
            }

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

        return UniversalBoard(
            rows = grid.rows,
            columns = grid.columns,
            occupied = occupied
        )
    }

    /**
     * Determines whether a board cell contains
     * a placed block.
     *
     * This uses several visual signals rather than
     * a single fixed RGB color.
     */
    private fun isCellOccupied(
        bitmap: Bitmap,
        grid: UniversalGridDetector.DetectedGrid,
        row: Int,
        column: Int
    ): Boolean {

        val centerX =
            grid.left +
                    (
                        column +
                                0.5f
                        ) *
                    grid.cellWidth

        val centerY =
            grid.top +
                    (
                        row +
                                0.5f
                        ) *
                    grid.cellHeight

        /*
         * Sample the inner part of the cell.
         */
        val radiusX =
            grid.cellWidth *
                    0.25f

        val radiusY =
            grid.cellHeight *
                    0.25f

        var totalBrightness =
            0f

        var totalColorStrength =
            0f

        var brightSamples =
            0

        var coloredSamples =
            0

        var totalSamples =
            0

        val startX =
            (
                centerX -
                        radiusX
                )
                .toInt()

        val endX =
            (
                centerX +
                        radiusX
                )
                .toInt()

        val startY =
            (
                centerY -
                        radiusY
                )
                .toInt()

        val endY =
            (
                centerY +
                        radiusY
                )
                .toInt()

        for (
            y in
            startY..endY
        ) {

            if (
                y < 0 ||
                y >= bitmap.height
            ) {
                continue
            }

            for (
                x in
                startX..endX
            ) {

                if (
                    x < 0 ||
                    x >= bitmap.width
                ) {
                    continue
                }

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

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

                val brightness =
                    (
                        red * 0.299f +
                                green * 0.587f +
                                blue * 0.114f
                        )

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

                val colorStrength =
                    (
                        maximum -
                                minimum
                        )
                        .toFloat()

                totalBrightness +=
                    brightness

                totalColorStrength +=
                    colorStrength

                totalSamples++

                if (
                    brightness >=
                    115f
                ) {
                    brightSamples++
                }

                if (
                    colorStrength >=
                    35f &&
                    brightness >=
                    45f
                ) {
                    coloredSamples++
                }
            }
        }

        if (
            totalSamples == 0
        ) {
            return false
        }

        val averageBrightness =
            totalBrightness /
                    totalSamples

        val averageColorStrength =
            totalColorStrength /
                    totalSamples

        val brightRatio =
            brightSamples.toFloat() /
                    totalSamples.toFloat()

        val coloredRatio =
            coloredSamples.toFloat() /
                    totalSamples.toFloat()

        /*
         * A placed block generally produces
         * stronger color/brightness than the
         * empty board background.
         */
        val strongColor =
            averageColorStrength >=
                    30f &&
                    coloredRatio >=
                    0.12f

        val strongBrightness =
            averageBrightness >=
                    120f &&
                    brightRatio >=
                    0.25f

        /*
         * Very colorful cells are usually occupied.
         */
        val colorfulCell =
            averageColorStrength >=
                    45f &&
                    coloredRatio >=
                    0.08f

        return (
            strongColor ||
                    strongBrightness ||
                    colorfulCell
            )
    }

    /**
     * Calculates confidence for the complete
     * vision result.
     */
    private fun calculateConfidence(
        gridConfidence: Float,
        board: UniversalBoard,
        pieces: List<UniversalBlockPiece>
    ): Float {

        var confidence =
            gridConfidence
                .coerceIn(
                    0f,
                    1f
                )

        /*
         * A valid board is required.
         */
        if (
            !board.isValid()
        ) {

            return 0f
        }

        /*
         * Board detection bonus.
         */
        confidence +=
            0.10f

        /*
         * Piece detection.
         *
         * We don't require exactly 3 pieces
         * because some block games may use
         * different numbers of pieces.
         */
        when {
            pieces.size >= 3 -> {
                confidence +=
                    0.20f
            }

            pieces.size == 2 -> {
                confidence +=
                    0.14f
            }

            pieces.size == 1 -> {
                confidence +=
                    0.08f
            }
        }

        /*
         * Clamp to 0..1.
         */
        return confidence.coerceIn(
            0f,
            1f
        )
    }

    /**
     * Empty vision state used when detection fails.
     */
    private fun emptyState():
            UniversalBlockState {

        return UniversalBlockState(
            board = null,
            pieces = emptyList(),
            confidence = 0f
        )
    }
}
