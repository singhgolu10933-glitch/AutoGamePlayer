package com.autogameplayer.blockblitz

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

data class BlockBlitzBoard(
    val rows: Int,
    val columns: Int,
    val occupied: Array<BooleanArray>
) {
    fun occupiedCount(): Int {
        return occupied.sumOf { row ->
            row.count { it }
        }
    }

    fun copyBoard(): Array<BooleanArray> {
        return Array(rows) { r ->
            occupied[r].clone()
        }
    }
}

data class BlockBlitzPiece(
    val index: Int,
    val cells: List<Pair<Int, Int>>,
    val detected: Boolean
)

data class BlockBlitzState(
    val board: BlockBlitzBoard?,
    val pieces: List<BlockBlitzPiece>,
    val confidence: Float
)

object BlockBlitzVision {

    private const val TAG = "BlockBlitzVision"

    private const val BOARD_ROWS = 8
    private const val BOARD_COLUMNS = 8

    /*
     * Block Blitz board.
     *
     * These are normalized coordinates.
     * They are intentionally slightly wider/taller
     * than the old values.
     */
    private const val BOARD_LEFT = 0.12f
    private const val BOARD_TOP = 0.19f
    private const val BOARD_RIGHT = 0.88f
    private const val BOARD_BOTTOM = 0.67f

    /*
     * Piece tray.
     */
    private const val PIECE_TOP = 0.78f
    private const val PIECE_BOTTOM = 0.93f

    /*
     * Three piece slots.
     */
    private val PIECE_SLOT_X =
        floatArrayOf(
            0.28f,
            0.50f,
            0.72f
        )

    // ============================================================
    // MAIN
    // ============================================================

    fun analyze(
        bitmap: Bitmap
    ): BlockBlitzState {

        val board =
            detectBoard(bitmap)

        val pieces =
            detectPieces(bitmap)

        val confidence =
            calculateConfidence(
                board,
                pieces
            )

        Log.d(
            TAG,
            "Vision -> " +
                    "board=${board != null}, " +
                    "occupied=${board?.occupiedCount() ?: -1}, " +
                    "pieces=${pieces.count { it.detected }}, " +
                    "confidence=$confidence"
        )

        return BlockBlitzState(
            board = board,
            pieces = pieces,
            confidence = confidence
        )
    }

    // ============================================================
    // BOARD
    // ============================================================

    private fun detectBoard(
        bitmap: Bitmap
    ): BlockBlitzBoard? {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return null
        }

        val left =
            (bitmap.width * BOARD_LEFT)
                .toInt()

        val top =
            (bitmap.height * BOARD_TOP)
                .toInt()

        val right =
            (bitmap.width * BOARD_RIGHT)
                .toInt()

        val bottom =
            (bitmap.height * BOARD_BOTTOM)
                .toInt()

        val boardWidth =
            right - left

        val boardHeight =
            bottom - top

        if (
            boardWidth <= 0 ||
            boardHeight <= 0
        ) {
            return null
        }

        val cellWidth =
            boardWidth.toFloat() /
                    BOARD_COLUMNS

        val cellHeight =
            boardHeight.toFloat() /
                    BOARD_ROWS

        val occupied =
            Array(
                BOARD_ROWS
            ) {
                BooleanArray(
                    BOARD_COLUMNS
                )
            }

        var validSamples = 0

        for (row in 0 until BOARD_ROWS) {

            for (column in 0 until BOARD_COLUMNS) {

                val centerX =
                    (
                        left +
                                (column + 0.5f) *
                                cellWidth
                        ).toInt()

                val centerY =
                    (
                        top +
                                (row + 0.5f) *
                                cellHeight
                        ).toInt()

                if (
                    centerX < 0 ||
                    centerY < 0 ||
                    centerX >= bitmap.width ||
                    centerY >= bitmap.height
                ) {
                    continue
                }

                /*
                 * Instead of a single pixel, sample
                 * several pixels around the cell center.
                 */
                occupied[row][column] =
                    cellLooksOccupied(
                        bitmap,
                        centerX,
                        centerY,
                        cellWidth,
                        cellHeight
                    )

                validSamples++
            }
        }

        if (validSamples == 0) {
            return null
        }

        return BlockBlitzBoard(
            rows = BOARD_ROWS,
            columns = BOARD_COLUMNS,
            occupied = occupied
        )
    }

    // ============================================================
    // CELL OCCUPANCY
    // ============================================================

    private fun cellLooksOccupied(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        cellWidth: Float,
        cellHeight: Float
    ): Boolean {

        val radiusX =
            (cellWidth * 0.22f)
                .toInt()
                .coerceAtLeast(2)

        val radiusY =
            (cellHeight * 0.22f)
                .toInt()
                .coerceAtLeast(2)

        var colored = 0
        var samples = 0

        val stepX =
            radiusX.coerceAtLeast(2)

        val stepY =
            radiusY.coerceAtLeast(2)

        var y =
            centerY - radiusY

        while (
            y <= centerY + radiusY
        ) {

            var x =
                centerX - radiusX

            while (
                x <= centerX + radiusX
            ) {

                if (
                    x >= 0 &&
                    y >= 0 &&
                    x < bitmap.width &&
                    y < bitmap.height
                ) {

                    val pixel =
                        bitmap.getPixel(
                            x,
                            y
                        )

                    samples++

                    if (
                        isBlockColor(pixel)
                    ) {
                        colored++
                    }
                }

                x += stepX
            }

            y += stepY
        }

        if (samples == 0) {
            return false
        }

        return colored.toFloat() /
                samples.toFloat() >
                0.20f
    }

    // ============================================================
    // PIECES
    // ============================================================

    private fun detectPieces(
        bitmap: Bitmap
    ): List<BlockBlitzPiece> {

        val result =
            mutableListOf<BlockBlitzPiece>()

        for (index in 0 until 3) {

            val centerX =
                (
                    bitmap.width *
                            PIECE_SLOT_X[index]
                    ).toInt()

            val cells =
                detectPieceCells(
                    bitmap,
                    centerX
                )

            val detected =
                cells.isNotEmpty()

            result.add(
                BlockBlitzPiece(
                    index = index,
                    cells = cells,
                    detected = detected
                )
            )

            Log.d(
                TAG,
                "Piece $index -> $cells"
            )
        }

        return result
    }

    // ============================================================
    // PIECE CELL DETECTION
    // ============================================================

    private fun detectPieceCells(
        bitmap: Bitmap,
        centerX: Int
    ): List<Pair<Int, Int>> {

        val trayTop =
            (
                bitmap.height *
                        PIECE_TOP
                ).toInt()

        val trayBottom =
            (
                bitmap.height *
                        PIECE_BOTTOM
                ).toInt()

        val trayHeight =
            trayBottom - trayTop

        if (trayHeight <= 0) {
            return emptyList()
        }

        /*
         * Block Blitz pieces are normally
         * represented using small square blocks.
         *
         * Search a 5 x 5 normalized area
         * around each slot.
         */
        val searchWidth =
            (
                bitmap.width * 0.19f
                ).toInt()

        val searchLeft =
            (
                centerX -
                        searchWidth / 2
                ).coerceAtLeast(0)

        val searchRight =
            (
                centerX +
                        searchWidth / 2
                ).coerceAtMost(
                    bitmap.width - 1
                )

        /*
         * Estimate block size from the tray.
         */
        val blockSize =
            (
                bitmap.width * 0.035f
                ).toInt()
                .coerceIn(
                    12,
                    80
                )

        /*
         * Build candidate centers.
         */
        val rawCells =
            mutableListOf<Pair<Int, Int>>()

        /*
         * We inspect a 5x5 area.
         *
         * Each candidate represents a possible
         * block location.
         */
        val columns = 5
        val rows = 5

        val usableWidth =
            searchRight -
                    searchLeft

        val cellSize =
            (
                usableWidth.toFloat() /
                        columns
                ).coerceAtLeast(
                    blockSize.toFloat()
                )

        /*
         * Find colored clusters by sampling.
         */
        for (row in 0 until rows) {

            for (column in 0 until columns) {

                val x =
                    (
                        searchLeft +
                                (column + 0.5f) *
                                cellSize
                        ).toInt()

                val y =
                    (
                        trayTop +
                                trayHeight *
                                (
                                    row + 0.5f
                                ) /
                                rows
                        ).toInt()

                if (
                    x < 0 ||
                    y < 0 ||
                    x >= bitmap.width ||
                    y >= bitmap.height
                ) {
                    continue
                }

                if (
                    regionHasBlockColor(
                        bitmap,
                        x,
                        y,
                        cellSize * 0.38f
                    )
                ) {

                    rawCells.add(
                        Pair(
                            row,
                            column
                        )
                    )
                }
            }
        }

        if (rawCells.isEmpty()) {
            return emptyList()
        }

        /*
         * Normalize the piece so that the top-left
         * occupied cell becomes 0,0.
         */
        val minRow =
            rawCells.minOf {
                it.first
            }

        val minColumn =
            rawCells.minOf {
                it.second
            }

        return rawCells
            .map {
                Pair(
                    it.first - minRow,
                    it.second - minColumn
                )
            }
            .distinct()
            .sortedWith(
                compareBy<Pair<Int, Int>>(
                    { it.first },
                    { it.second }
                )
            )
    }

    // ============================================================
    // REGION COLOR
    // ============================================================

    private fun regionHasBlockColor(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Float
    ): Boolean {

        val r =
            radius
                .toInt()
                .coerceAtLeast(3)

        var good = 0
        var total = 0

        val step =
            (r / 3)
                .coerceAtLeast(2)

        var y =
            centerY - r

        while (
            y <= centerY + r
        ) {

            var x =
                centerX - r

            while (
                x <= centerX + r
            ) {

                if (
                    x >= 0 &&
                    y >= 0 &&
                    x < bitmap.width &&
                    y < bitmap.height
                ) {

                    val pixel =
                        bitmap.getPixel(
                            x,
                            y
                        )

                    total++

                    if (
                        isBlockColor(pixel)
                    ) {
                        good++
                    }
                }

                x += step
            }

            y += step
        }

        if (total == 0) {
            return false
        }

        return good.toFloat() /
                total.toFloat() >
                0.25f
    }

    // ============================================================
    // COLOR CLASSIFICATION
    // ============================================================

    private fun isBlockColor(
        pixel: Int
    ): Boolean {

        val red =
            Color.red(pixel)

        val green =
            Color.green(pixel)

        val blue =
            Color.blue(pixel)

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

        val saturation =
            maximum - minimum

        val brightness =
            (
                red +
                        green +
                        blue
                ) / 3

        /*
         * Empty Block Blitz board is dark blue.
         * Pieces are significantly brighter
         * and more saturated.
         */
        return brightness > 75 &&
                saturation > 35
    }

    // ============================================================
    // CONFIDENCE
    // ============================================================

    private fun calculateConfidence(
        board: BlockBlitzBoard?,
        pieces: List<BlockBlitzPiece>
    ): Float {

        if (board == null) {
            return 0f
        }

        val detectedPieces =
            pieces.count {
                it.detected
            }

        var confidence =
            0.60f

        if (
            board.occupiedCount() >= 0
        ) {
            confidence += 0.10f
        }

        if (
            detectedPieces >= 1
        ) {
            confidence += 0.10f
        }

        if (
            detectedPieces >= 2
        ) {
            confidence += 0.10f
        }

        if (
            detectedPieces == 3
        ) {
            confidence += 0.10f
        }

        return confidence.coerceIn(
            0f,
            1f
        )
    }
}
