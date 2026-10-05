package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal grid detector for block-puzzle games.
 *
 * Goals:
 * 1. Detect different board sizes.
 * 2. Work without game-specific coordinates.
 * 3. Keep CPU usage low.
 * 4. Prefer large, regular rectangular grids.
 * 5. Provide accurate cell geometry for the next vision stage.
 *
 * This detector does NOT interact with the game.
 */
data class DetectedGrid(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rows: Int,
    val columns: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    val score: Float
)

object UniversalGridDetector {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private const val MIN_ROWS = 6
    private const val MAX_ROWS = 12

    private const val MIN_COLUMNS = 6
    private const val MAX_COLUMNS = 12

    /*
     * Scan at reduced resolution.
     *
     * This is important for speed.
     * We do not inspect every pixel of a 1080p/1440p screen.
     */
    private const val SAMPLE_STEP = 8

    /*
     * Block games normally keep the board around the
     * central portion of the screen.
     */
    private const val SEARCH_LEFT = 0.05f
    private const val SEARCH_RIGHT = 0.95f
    private const val SEARCH_TOP = 0.12f
    private const val SEARCH_BOTTOM = 0.76f

    /*
     * We want the board to be reasonably large.
     */
    private const val MIN_BOARD_WIDTH_RATIO = 0.35f
    private const val MAX_BOARD_WIDTH_RATIO = 0.90f

    private const val MIN_BOARD_HEIGHT_RATIO = 0.25f
    private const val MAX_BOARD_HEIGHT_RATIO = 0.70f

    // ============================================================
    // PUBLIC API
    // ============================================================

    fun detect(
        bitmap: Bitmap
    ): DetectedGrid? {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return null
        }

        val width = bitmap.width
        val height = bitmap.height

        /*
         * Fast path:
         *
         * First test the common square-board layouts.
         * This avoids expensive candidate generation in many
         * normal block games.
         */
        val fastCandidate =
            detectFastSquareBoard(
                bitmap = bitmap,
                width = width,
                height = height
            )

        if (fastCandidate != null) {
            return fastCandidate
        }

        /*
         * Fallback:
         *
         * Try several grid sizes.
         */
        return detectGeneric(
            bitmap = bitmap,
            width = width,
            height = height
        )
    }

    // ============================================================
    // FAST PATH
    // ============================================================

    private fun detectFastSquareBoard(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): DetectedGrid? {

        /*
         * Most block puzzle games use a square board.
         *
         * Common sizes:
         * 8x8
         * 9x9
         * 10x10
         *
         * Try these first.
         */
        val sizes = intArrayOf(
            8,
            9,
            10
        )

        var best: DetectedGrid? = null

        for (size in sizes) {

            val candidate =
                findBestCandidateForSize(
                    bitmap = bitmap,
                    width = width,
                    height = height,
                    rows = size,
                    columns = size
                )

            if (candidate != null) {

                if (
                    best == null ||
                    candidate.score > best.score
                ) {
                    best = candidate
                }
            }
        }

        /*
         * Only accept a strong candidate.
         */
        if (
            best != null &&
            best.score >= 0.58f
        ) {
            return best
        }

        return null
    }

    // ============================================================
    // GENERIC DETECTION
    // ============================================================

    private fun detectGeneric(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): DetectedGrid? {

        var best: DetectedGrid? = null

        /*
         * We deliberately skip every second size during the first
         * pass where possible.
         *
         * This keeps detection fast.
         */
        for (rows in MIN_ROWS..MAX_ROWS) {

            for (columns in MIN_COLUMNS..MAX_COLUMNS) {

                /*
                 * Avoid obviously unusual aspect ratios.
                 *
                 * Most block puzzle boards are close to square.
                 */
                val ratio =
                    columns.toFloat() /
                            rows.toFloat()

                if (
                    ratio < 0.65f ||
                    ratio > 1.55f
                ) {
                    continue
                }

                val candidate =
                    findBestCandidateForSize(
                        bitmap = bitmap,
                        width = width,
                        height = height,
                        rows = rows,
                        columns = columns
                    )

                if (candidate == null) {
                    continue
                }

                if (
                    best == null ||
                    candidate.score > best.score
                ) {
                    best = candidate
                }
            }
        }

        return best
    }

    // ============================================================
    // SIZE SEARCH
    // ============================================================

    private fun findBestCandidateForSize(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        rows: Int,
        columns: Int
    ): DetectedGrid? {

        val searchLeft =
            (width * SEARCH_LEFT)
                .toInt()
                .coerceAtLeast(0)

        val searchRight =
            (width * SEARCH_RIGHT)
                .toInt()
                .coerceAtMost(width)

        val searchTop =
            (height * SEARCH_TOP)
                .toInt()
                .coerceAtLeast(0)

        val searchBottom =
            (height * SEARCH_BOTTOM)
                .toInt()
                .coerceAtMost(height)

        if (
            searchRight <= searchLeft ||
            searchBottom <= searchTop
        ) {
            return null
        }

        val minBoardWidth =
            width * MIN_BOARD_WIDTH_RATIO

        val maxBoardWidth =
            width * MAX_BOARD_WIDTH_RATIO

        val minBoardHeight =
            height * MIN_BOARD_HEIGHT_RATIO

        val maxBoardHeight =
            height * MAX_BOARD_HEIGHT_RATIO

        /*
         * Estimate cell size from board width.
         *
         * We use several possible board widths instead of
         * scanning every possible pixel.
         */
        val widthSteps = 12

        val widthStep =
            (
                maxBoardWidth -
                        minBoardWidth
                ) /
                    widthSteps.toFloat()

        var best: DetectedGrid? = null

        for (i in 0..widthSteps) {

            val boardWidth =
                minBoardWidth +
                        widthStep * i

            val cellWidth =
                boardWidth /
                        columns.toFloat()

            if (cellWidth < 20f) {
                continue
            }

            if (cellWidth > 180f) {
                continue
            }

            /*
             * Prefer roughly square cells.
             */
            val cellHeight =
                cellWidth

            val boardHeight =
                cellHeight *
                        rows.toFloat()

            if (
                boardHeight < minBoardHeight ||
                boardHeight > maxBoardHeight
            ) {
                continue
            }

            /*
             * Search possible board centers.
             *
             * Only a few positions are tested to keep this fast.
             */
            val centerX =
                (
                    searchLeft +
                            searchRight
                    ) / 2

            val centerY =
                (
                    searchTop +
                            searchBottom
                    ) / 2

            val candidateOffsetsX =
                intArrayOf(
                    -80,
                    -40,
                    0,
                    40,
                    80
                )

            val candidateOffsetsY =
                intArrayOf(
                    -100,
                    -50,
                    0,
                    50,
                    100
                )

            for (offsetX in candidateOffsetsX) {

                for (offsetY in candidateOffsetsY) {

                    val cx =
                        centerX +
                                offsetX

                    val cy =
                        centerY +
                                offsetY

                    val left =
                        cx -
                                boardWidth / 2f

                    val top =
                        cy -
                                boardHeight / 2f

                    val right =
                        left +
                                boardWidth

                    val bottom =
                        top +
                                boardHeight

                    if (
                        left < searchLeft ||
                        top < searchTop ||
                        right > searchRight ||
                        bottom > searchBottom
                    ) {
                        continue
                    }

                    val score =
                        scoreGrid(
                            bitmap = bitmap,
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            rows = rows,
                            columns = columns
                        )

                    if (
                        score <= 0f
                    ) {
                        continue
                    }

                    val candidate =
                        DetectedGrid(
                            left =
                                (left / width)
                                    .coerceIn(
                                        0f,
                                        1f
                                    ),

                            top =
                                (top / height)
                                    .coerceIn(
                                        0f,
                                        1f
                                    ),

                            right =
                                (right / width)
                                    .coerceIn(
                                        0f,
                                        1f
                                    ),

                            bottom =
                                (bottom / height)
                                    .coerceIn(
                                        0f,
                                        1f
                                    ),

                            rows = rows,

                            columns = columns,

                            cellWidth =
                                boardWidth /
                                        columns.toFloat(),

                            cellHeight =
                                boardHeight /
                                        rows.toFloat(),

                            score = score
                        )

                    if (
                        best == null ||
                        candidate.score >
                        best.score
                    ) {
                        best = candidate
                    }
                }
            }
        }

        return best
    }

    // ============================================================
    // GRID SCORING
    // ============================================================

    private fun scoreGrid(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        rows: Int,
        columns: Int
    ): Float {

        if (
            right <= left ||
            bottom <= top
        ) {
            return 0f
        }

        val boardWidth =
            right - left

        val boardHeight =
            bottom - top

        val cellWidth =
            boardWidth /
                    columns.toFloat()

        val cellHeight =
            boardHeight /
                    rows.toFloat()

        if (
            cellWidth <= 5f ||
            cellHeight <= 5f
        ) {
            return 0f
        }

        var contrastScore = 0f
        var darkCellScore = 0f
        var regularityScore = 0f

        var samples = 0

        /*
         * Instead of examining every pixel,
         * examine the center and edge samples of cells.
         */
        for (row in 0 until rows) {

            for (column in 0 until columns) {

                val centerX =
                    left +
                            (column + 0.5f) *
                            cellWidth

                val centerY =
                    top +
                            (row + 0.5f) *
                            cellHeight

                val x =
                    centerX
                        .toInt()
                        .coerceIn(
                            0,
                            bitmap.width - 1
                        )

                val y =
                    centerY
                        .toInt()
                        .coerceIn(
                            0,
                            bitmap.height - 1
                        )

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

                val red =
                    (pixel shr 16) and 0xFF

                val green =
                    (pixel shr 8) and 0xFF

                val blue =
                    pixel and 0xFF

                val brightness =
                    (
                        red +
                                green +
                                blue
                        ) / 3f

                /*
                 * Block-game empty cells are often darker
                 * than the surrounding bright UI.
                 */
                if (
                    brightness < 115f
                ) {
                    darkCellScore += 1f
                }

                /*
                 * Check a point near the cell boundary.
                 *
                 * Regular board cells tend to have similar
                 * boundary behavior.
                 */
                val edgeX =
                    left +
                            column * cellWidth +
                            cellWidth * 0.08f

                val edgeY =
                    top +
                            row * cellHeight +
                            cellHeight * 0.08f

                val ex =
                    edgeX
                        .toInt()
                        .coerceIn(
                            0,
                            bitmap.width - 1
                        )

                val ey =
                    edgeY
                        .toInt()
                        .coerceIn(
                            0,
                            bitmap.height - 1
                        )

                val edgePixel =
                    bitmap.getPixel(
                        ex,
                        ey
                    )

                val edgeR =
                    (edgePixel shr 16) and 0xFF

                val edgeG =
                    (edgePixel shr 8) and 0xFF

                val edgeB =
                    edgePixel and 0xFF

                val edgeBrightness =
                    (
                        edgeR +
                                edgeG +
                                edgeB
                        ) / 3f

                contrastScore +=
                    abs(
                        brightness -
                                edgeBrightness
                    ) / 255f

                samples++
            }
        }

        if (samples == 0) {
            return 0f
        }

        darkCellScore /=
            samples.toFloat()

        contrastScore /=
            samples.toFloat()

        /*
         * Check whether cell dimensions are regular.
         */
        val dimensionDifference =
            abs(
                cellWidth -
                        cellHeight
            )

        regularityScore =
            (
                1f -
                        (
                            dimensionDifference /
                                    max(
                                        cellWidth,
                                        cellHeight
                                    )
                        )
                )
                .coerceIn(
                    0f,
                    1f
                )

        /*
         * Empty board cells usually form a repeated pattern.
         */
        val darknessComponent =
            darkCellScore
                .coerceIn(
                    0f,
                    1f
                )

        val contrastComponent =
            contrastScore
                .coerceIn(
                    0f,
                    1f
                )

        /*
         * Combined score.
         *
         * Darkness is deliberately weighted more heavily because
         * Block Blitz-like boards have dark empty cells.
         */
        var score =
            darknessComponent * 0.55f +
                    regularityScore * 0.30f +
                    contrastComponent * 0.15f

        /*
         * Penalize obviously bad geometry.
         */
        val aspect =
            boardWidth /
                    boardHeight

        if (
            aspect < 0.70f ||
            aspect > 1.45f
        ) {
            score *= 0.65f
        }

        /*
         * Prefer large boards over tiny accidental rectangles.
         */
        val areaRatio =
            (
                boardWidth *
                        boardHeight
                ) /
                    (
                        bitmap.width *
                                bitmap.height
                        )
                            .toFloat()

        if (areaRatio < 0.08f) {
            score *= 0.65f
        }

        return score
            .coerceIn(
                0f,
                1f
            )
    }

    // ============================================================
    // CELL HELPERS
    // ============================================================

    /**
     * Returns the pixel coordinate of a cell center.
     */
    fun cellCenter(
        grid: DetectedGrid,
        row: Int,
        column: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Pair<Float, Float>? {

        if (
            row !in 0 until grid.rows ||
            column !in 0 until grid.columns
        ) {
            return null
        }

        val boardLeft =
            grid.left *
                    bitmapWidth

        val boardTop =
            grid.top *
                    bitmapHeight

        val x =
            boardLeft +
                    (
                        column + 0.5f
                        ) *
                    grid.cellWidth

        val y =
            boardTop +
                    (
                        row + 0.5f
                        ) *
                    grid.cellHeight

        return x to y
    }

    /**
     * Quickly returns the approximate board rectangle
     * in pixel coordinates.
     */
    fun pixelBounds(
        grid: DetectedGrid,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): FloatArray {

        return floatArrayOf(
            grid.left * bitmapWidth,
            grid.top * bitmapHeight,
            grid.right * bitmapWidth,
            grid.bottom * bitmapHeight
        )
    }
}
