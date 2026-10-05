package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal grid detector.
 *
 * It does not contain coordinates for Block Blitz
 * or any other individual game.
 *
 * It tries to discover a rectangular block-puzzle
 * board from the current screenshot.
 */
object UniversalGridDetector {

    /**
     * Detected board geometry.
     *
     * Coordinates are pixel coordinates.
     */
    data class DetectedGrid(
        val rows: Int,
        val columns: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val cellWidth: Float,
        val cellHeight: Float,
        val confidence: Float
    ) {

        val width: Int
            get() = right - left

        val height: Int
            get() = bottom - top

        val centerX: Float
            get() =
                (left + right) / 2f

        val centerY: Float
            get() =
                (top + bottom) / 2f
    }

    /**
     * Detects the most likely block-puzzle grid.
     */
    fun detect(
        bitmap: Bitmap
    ): DetectedGrid? {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return null
        }

        val candidates =
            generateCandidates(bitmap)

        if (candidates.isEmpty()) {
            return null
        }

        var best:
                DetectedGrid? = null

        var bestScore =
            Float.NEGATIVE_INFINITY

        for (candidate in candidates) {

            val score =
                scoreCandidate(
                    bitmap,
                    candidate
                )

            if (
                score > bestScore
            ) {

                bestScore = score
                best = candidate
            }
        }

        if (best == null) {
            return null
        }

        val finalConfidence =
            normalizeConfidence(
                bestScore
            )

        return best.copy(
            confidence = finalConfidence
        )
    }

    /**
     * Generates possible grids.
     *
     * Typical block puzzle boards are square,
     * but this detector also allows rectangular
     * boards.
     */
    private fun generateCandidates(
        bitmap: Bitmap
    ): List<DetectedGrid> {

        val width =
            bitmap.width

        val height =
            bitmap.height

        val result =
            mutableListOf<DetectedGrid>()

        /*
         * Ignore a small amount of the top
         * because many games have:
         *
         * - title
         * - score
         * - coins
         * - buttons
         */
        val searchTop =
            (height * 0.08f)
                .toInt()
                .coerceIn(
                    0,
                    height - 1
                )

        /*
         * Leave the lower section for pieces.
         */
        val searchBottom =
            (height * 0.76f)
                .toInt()
                .coerceIn(
                    searchTop + 1,
                    height
                )

        /*
         * Supported grid sizes.
         *
         * This covers common block-puzzle
         * layouts such as:
         *
         * 8x8
         * 9x9
         * 10x10
         * 10x8
         * 12x12
         */
        val sizes =
            listOf(
                6 to 6,
                7 to 7,
                8 to 8,
                9 to 9,
                10 to 10,
                11 to 11,
                12 to 12,
                8 to 10,
                10 to 8,
                8 to 9,
                9 to 8,
                9 to 10,
                10 to 9,
                10 to 12,
                12 to 10
            )

        for ((rows, columns) in sizes) {

            /*
             * Board should normally occupy
             * a substantial portion of screen width.
             */
            val minimumBoardWidth =
                width * 0.45f

            val maximumBoardWidth =
                width * 0.94f

            var boardWidth =
                width * 0.72f

            /*
             * Try several board widths.
             */
            val widthSteps =
                7

            for (
                step in
                0 until widthSteps
            ) {

                val fraction =
                    0.50f +
                            step * 0.07f

                boardWidth =
                    width * fraction

                if (
                    boardWidth <
                    minimumBoardWidth ||
                    boardWidth >
                    maximumBoardWidth
                ) {
                    continue
                }

                val cellWidth =
                    boardWidth /
                            columns.toFloat()

                val boardHeight =
                    cellWidth *
                            rows.toFloat()

                if (
                    boardHeight <
                    height * 0.25f
                ) {
                    continue
                }

                if (
                    boardHeight >
                    height * 0.62f
                ) {
                    continue
                }

                /*
                 * Center the board horizontally.
                 */
                val left =
                    (
                        (width -
                                boardWidth) /
                                2f
                        )
                        .toInt()

                val right =
                    (
                        left +
                                boardWidth
                    )
                        .toInt()

                /*
                 * Try multiple vertical positions.
                 */
                val verticalPositions =
                    listOf(
                        0.16f,
                        0.19f,
                        0.22f,
                        0.25f,
                        0.28f,
                        0.31f,
                        0.34f
                    )

                for (
                    topFraction in
                    verticalPositions
                ) {

                    val top =
                        (
                            height *
                                    topFraction
                            )
                            .toInt()

                    val bottom =
                        (
                            top +
                                    boardHeight
                            )
                            .toInt()

                    if (
                        top <
                        searchTop
                    ) {
                        continue
                    }

                    if (
                        bottom >
                        searchBottom
                    ) {
                        continue
                    }

                    if (
                        right <= left ||
                        bottom <= top
                    ) {
                        continue
                    }

                    result.add(
                        DetectedGrid(
                            rows = rows,
                            columns = columns,
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            cellWidth =
                                boardWidth /
                                        columns,
                            cellHeight =
                                boardHeight /
                                        rows,
                            confidence = 0f
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Scores a possible grid.
     *
     * We look for repeated visual differences
     * between neighboring cells.
     */
    private fun scoreCandidate(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Float {

        val horizontalScore =
            horizontalBoundaryScore(
                bitmap,
                grid
            )

        val verticalScore =
            verticalBoundaryScore(
                bitmap,
                grid
            )

        val contrastScore =
            cellContrastScore(
                bitmap,
                grid
            )

        val shapeScore =
            shapeScore(grid)

        val screenScore =
            screenPositionScore(
                bitmap,
                grid
            )

        /*
         * Weighted combination.
         */
        return (
            horizontalScore * 0.30f +
                    verticalScore * 0.30f +
                    contrastScore * 0.25f +
                    shapeScore * 0.10f +
                    screenScore * 0.05f
            )
    }

    /**
     * Measures horizontal cell-to-cell changes.
     */
    private fun horizontalBoundaryScore(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Float {

        var total =
            0f

        var samples =
            0

        val rowsToCheck =
            min(
                grid.rows,
                12
            )

        val columnsToCheck =
            min(
                grid.columns - 1,
                11
            )

        for (
            row in
            0 until rowsToCheck
        ) {

            for (
                column in
                0 until columnsToCheck
            ) {

                val first =
                    sampleCellBrightness(
                        bitmap,
                        grid,
                        row,
                        column
                    )

                val second =
                    sampleCellBrightness(
                        bitmap,
                        grid,
                        row,
                        column + 1
                    )

                total +=
                    abs(
                        first - second
                    )

                samples++
            }
        }

        if (samples == 0) {
            return 0f
        }

        return (
            total /
                    samples
        )
            .coerceIn(
                0f,
                255f
            )
    }

    /**
     * Measures vertical cell-to-cell changes.
     */
    private fun verticalBoundaryScore(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Float {

        var total =
            0f

        var samples =
            0

        val rowsToCheck =
            min(
                grid.rows - 1,
                11
            )

        val columnsToCheck =
            min(
                grid.columns,
                12
            )

        for (
            row in
            0 until rowsToCheck
        ) {

            for (
                column in
                0 until columnsToCheck
            ) {

                val first =
                    sampleCellBrightness(
                        bitmap,
                        grid,
                        row,
                        column
                    )

                val second =
                    sampleCellBrightness(
                        bitmap,
                        grid,
                        row + 1,
                        column
                    )

                total +=
                    abs(
                        first - second
                    )

                samples++
            }
        }

        if (samples == 0) {
            return 0f
        }

        return (
            total /
                    samples
        )
            .coerceIn(
                0f,
                255f
            )
    }

    /**
     * Measures contrast variation across cells.
     */
    private fun cellContrastScore(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Float {

        var minimum =
            Float.MAX_VALUE

        var maximum =
            Float.MIN_VALUE

        var total =
            0f

        var samples =
            0

        val rowsToCheck =
            min(
                grid.rows,
                12
            )

        val columnsToCheck =
            min(
                grid.columns,
                12
            )

        for (
            row in
            0 until rowsToCheck
        ) {

            for (
                column in
                0 until columnsToCheck
            ) {

                val brightness =
                    sampleCellBrightness(
                        bitmap,
                        grid,
                        row,
                        column
                    )

                minimum =
                    min(
                        minimum,
                        brightness
                    )

                maximum =
                    max(
                        maximum,
                        brightness
                    )

                total +=
                    brightness

                samples++
            }
        }

        if (samples == 0) {
            return 0f
        }

        val average =
            total /
                    samples

        val range =
            maximum -
                    minimum

        /*
         * Prefer boards where cells have
         * meaningful but not extreme
         * brightness variation.
         */
        val averageFactor =
            if (
                average > 10f &&
                average < 245f
            ) {
                1f
            } else {
                0.5f
            }

        return (
            range *
                    averageFactor
            )
            .coerceIn(
                0f,
                255f
            )
    }

    /**
     * Favors reasonable board geometry.
     */
    private fun shapeScore(
        grid: DetectedGrid
    ): Float {

        if (
            grid.cellWidth <= 0f ||
            grid.cellHeight <= 0f
        ) {
            return 0f
        }

        val ratio =
            grid.cellWidth /
                    grid.cellHeight

        val difference =
            abs(
                1f - ratio
            )

        return (
            1f -
                    difference.coerceIn(
                        0f,
                        1f
                    )
            ) * 255f
    }

    /**
     * Favors boards located in the usual
     * middle portion of portrait games.
     */
    private fun screenPositionScore(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Float {

        val screenCenter =
            bitmap.height *
                    0.42f

        val boardCenter =
            grid.centerY

        val distance =
            abs(
                boardCenter -
                        screenCenter
            )

        val maximumDistance =
            bitmap.height *
                    0.35f

        if (
            maximumDistance <= 0f
        ) {
            return 0f
        }

        return (
            1f -
                    (
                        distance /
                                maximumDistance
                        )
                            .coerceIn(
                                0f,
                                1f
                            )
            ) * 255f
    }

    /**
     * Samples the center portion of a cell.
     *
     * We avoid the outer edge because many
     * games have grid borders or shadows.
     */
    private fun sampleCellBrightness(
        bitmap: Bitmap,
        grid: DetectedGrid,
        row: Int,
        column: Int
    ): Float {

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

        val radiusX =
            grid.cellWidth *
                    0.22f

        val radiusY =
            grid.cellHeight *
                    0.22f

        var total =
            0f

        var samples =
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

                /*
                 * Perceived brightness.
                 */
                val brightness =
                    (
                        red * 0.299f +
                                green * 0.587f +
                                blue * 0.114f
                        )

                total +=
                    brightness

                samples++
            }
        }

        if (samples == 0) {
            return 0f
        }

        return total /
                samples
    }

    /**
     * Converts raw candidate score into
     * a 0..1 confidence value.
     */
    private fun normalizeConfidence(
        score: Float
    ): Float {

        /*
         * The detector works with a combination
         * of several 0..255 metrics.
         */
        val normalized =
            score /
                    255f

        return normalized
            .coerceIn(
                0f,
                1f
            )
    }
}
