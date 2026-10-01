package com.autogameplayer.blockblitz

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min

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

    private const val BOARD_LEFT = 0.28f
    private const val BOARD_TOP = 0.30f
    private const val BOARD_RIGHT = 0.88f
    private const val BOARD_BOTTOM = 0.70f

    private const val BOARD_ROWS = 8
    private const val BOARD_COLUMNS = 8

    // Piece tray
    private const val PIECES_TOP = 0.72f
    private const val PIECES_BOTTOM = 0.94f

    // Three approximate piece centers
    private val PIECE_CENTER_X = floatArrayOf(
        0.28f,
        0.50f,
        0.72f
    )

    // ============================================================
    // MAIN ANALYSIS
    // ============================================================

    fun analyze(bitmap: Bitmap): BlockBlitzState {

        val board = detectBoard(bitmap)

        val pieces = detectPieces(bitmap)

        val confidence =
            calculateConfidence(
                board,
                pieces
            )

        Log.d(
            TAG,
            "Vision result: " +
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
                .coerceAtLeast(0)

        val top =
            (bitmap.height * BOARD_TOP)
                .toInt()
                .coerceAtLeast(0)

        val right =
            (bitmap.width * BOARD_RIGHT)
                .toInt()
                .coerceAtMost(bitmap.width)

        val bottom =
            (bitmap.height * BOARD_BOTTOM)
                .toInt()
                .coerceAtMost(bitmap.height)

        val boardWidth = right - left
        val boardHeight = bottom - top

        if (
            boardWidth <= 0 ||
            boardHeight <= 0
        ) {
            return null
        }

        val cellWidth =
            boardWidth.toFloat() / BOARD_COLUMNS

        val cellHeight =
            boardHeight.toFloat() / BOARD_ROWS

        val occupied =
            Array(BOARD_ROWS) {
                BooleanArray(BOARD_COLUMNS)
            }

        for (row in 0 until BOARD_ROWS) {

            for (column in 0 until BOARD_COLUMNS) {

                val centerX =
                    (
                        left +
                                (column + 0.5f) *
                                cellWidth
                        )
                        .toInt()

                val centerY =
                    (
                        top +
                                (row + 0.5f) *
                                cellHeight
                        )
                        .toInt()

                occupied[row][column] =
                    sampleOccupiedCell(
                        bitmap,
                        centerX,
                        centerY,
                        cellWidth,
                        cellHeight
                    )
            }
        }

        return BlockBlitzBoard(
            rows = BOARD_ROWS,
            columns = BOARD_COLUMNS,
            occupied = occupied
        )
    }

    // ============================================================
    // BOARD CELL SAMPLING
    // ============================================================

    private fun sampleOccupiedCell(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        cellWidth: Float,
        cellHeight: Float
    ): Boolean {

        val radiusX =
            max(
                2,
                (cellWidth * 0.22f).toInt()
            )

        val radiusY =
            max(
                2,
                (cellHeight * 0.22f).toInt()
            )

        var colored = 0
        var samples = 0

        val startX =
            max(0, centerX - radiusX)

        val endX =
            min(
                bitmap.width - 1,
                centerX + radiusX
            )

        val startY =
            max(0, centerY - radiusY)

        val endY =
            min(
                bitmap.height - 1,
                centerY + radiusY
            )

        var y = startY

        while (y <= endY) {

            var x = startX

            while (x <= endX) {

                val pixel =
                    bitmap.getPixel(x, y)

                if (isLikelyOccupied(pixel)) {
                    colored++
                }

                samples++

                x += 2
            }

            y += 2
        }

        if (samples == 0) {
            return false
        }

        // Require a reasonable percentage of
        // colored pixels instead of one noisy pixel.
        return colored.toFloat() / samples >= 0.22f
    }

    // ============================================================
    // COLORED CELL TEST
    // ============================================================

    private fun isLikelyOccupied(
        pixel: Int
    ): Boolean {

        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)

        val maximum =
            maxOf(red, green, blue)

        val minimum =
            minOf(red, green, blue)

        val saturation =
            maximum - minimum

        val brightness =
            (red + green + blue) / 3

        return (
            brightness > 75 &&
                    saturation > 35
            )
    }

    // ============================================================
    // PIECE DETECTION
    // ============================================================

    private fun detectPieces(
        bitmap: Bitmap
    ): List<BlockBlitzPiece> {

        val result =
            mutableListOf<BlockBlitzPiece>()

        for (index in 0..2) {

            val centerX =
                (
                    bitmap.width *
                            PIECE_CENTER_X[index]
                    )
                    .toInt()

            val top =
                (
                    bitmap.height *
                            PIECES_TOP
                    )
                    .toInt()

            val bottom =
                (
                    bitmap.height *
                            PIECES_BOTTOM
                    )
                    .toInt()

            val cells =
                extractPieceCells(
                    bitmap,
                    centerX,
                    top,
                    bottom
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
                "Piece $index -> " +
                        "detected=$detected " +
                        "cells=$cells"
            )
        }

        return result
    }

    // ============================================================
    // PIECE CELL EXTRACTION
    // ============================================================

    private fun extractPieceCells(
        bitmap: Bitmap,
        centerX: Int,
        top: Int,
        bottom: Int
    ): List<Pair<Int, Int>> {

        val slotHalfWidth =
            (
                bitmap.width * 0.13f
                )
                .toInt()

        val left =
            max(
                0,
                centerX - slotHalfWidth
            )

        val right =
            min(
                bitmap.width - 1,
                centerX + slotHalfWidth
            )

        val safeTop =
            max(0, top)

        val safeBottom =
            min(
                bitmap.height - 1,
                bottom
            )

        if (
            right <= left ||
            safeBottom <= safeTop
        ) {
            return emptyList()
        }

        /*
         * Estimate the size of a single
         * puzzle block from the piece tray.
         */
        val estimatedBlock =
            estimateBlockSize(
                bitmap,
                left,
                right,
                safeTop,
                safeBottom
            )

        if (estimatedBlock <= 2) {
            return emptyList()
        }

        val occupiedPixels =
            findColoredPixelBounds(
                bitmap,
                left,
                right,
                safeTop,
                safeBottom
            )

        if (occupiedPixels == null) {
            return emptyList()
        }

        val minX = occupiedPixels.first
        val minY = occupiedPixels.second
        val maxX = occupiedPixels.third
        val maxY = occupiedPixels.fourth

        val width =
            maxX - minX + 1

        val height =
            maxY - minY + 1

        val gridWidth =
            (
                width.toFloat() /
                        estimatedBlock
                )
                .toInt()
                .coerceIn(1, 5)

        val gridHeight =
            (
                height.toFloat() /
                        estimatedBlock
                )
                .toInt()
                .coerceIn(1, 5)

        val cells =
            mutableListOf<Pair<Int, Int>>()

        for (row in 0 until gridHeight) {

            for (column in 0 until gridWidth) {

                val cellCenterX =
                    minX +
                            (
                                column + 0.5f
                            ) *
                            estimatedBlock

                val cellCenterY =
                    minY +
                            (
                                row + 0.5f
                            ) *
                            estimatedBlock

                if (
                    hasColoredPixelsAround(
                        bitmap,
                        cellCenterX.toInt(),
                        cellCenterY.toInt(),
                        estimatedBlock
                    )
                ) {
                    cells.add(
                        Pair(
                            row,
                            column
                        )
                    )
                }
            }
        }

        return normalizePieceCells(cells)
    }

    // ============================================================
    // ESTIMATE BLOCK SIZE
    // ============================================================

    private fun estimateBlockSize(
        bitmap: Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int
    ): Int {

        val range =
            max(
                1,
                right - left
            )

        /*
         * Block Blitz pieces generally occupy
         * only a small portion of their slot.
         *
         * This is intentionally conservative.
         */
        return (
            range / 8
        )
            .coerceIn(10, 100)
    }

    // ============================================================
    // FIND COLORED BOUNDS
    // ============================================================

    private data class PixelBounds(
        val first: Int,
        val second: Int,
        val third: Int,
        val fourth: Int
    )

    private fun findColoredPixelBounds(
        bitmap: Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int
    ): PixelBounds? {

        var minX = right
        var minY = bottom
        var maxX = left
        var maxY = top

        var found = false

        var y = top

        while (y <= bottom) {

            var x = left

            while (x <= right) {

                if (
                    isLikelyOccupied(
                        bitmap.getPixel(x, y)
                    )
                ) {

                    found = true

                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }

                x += 3
            }

            y += 3
        }

        if (!found) {
            return null
        }

        return PixelBounds(
            minX,
            minY,
            maxX,
            maxY
        )
    }

    // ============================================================
    // LOCAL CELL CHECK
    // ============================================================

    private fun hasColoredPixelsAround(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        blockSize: Int
    ): Boolean {

        val radius =
            max(
                2,
                (blockSize * 0.25f)
                    .toInt()
            )

        var colored = 0
        var samples = 0

        val left =
            max(
                0,
                centerX - radius
            )

        val right =
            min(
                bitmap.width - 1,
                centerX + radius
            )

        val top =
            max(
                0,
                centerY - radius
            )

        val bottom =
            min(
                bitmap.height - 1,
                centerY + radius
            )

        var y = top

        while (y <= bottom) {

            var x = left

            while (x <= right) {

                if (
                    isLikelyOccupied(
                        bitmap.getPixel(x, y)
                    )
                ) {
                    colored++
                }

                samples++

                x += 2
            }

            y += 2
        }

        if (samples == 0) {
            return false
        }

        return (
            colored.toFloat() /
                    samples
            ) >= 0.30f
    }

    // ============================================================
    // NORMALIZE PIECE
    // ============================================================

    private fun normalizePieceCells(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (cells.isEmpty()) {
            return emptyList()
        }

        val minRow =
            cells.minOf { it.first }

        val minColumn =
            cells.minOf { it.second }

        return cells
            .map {
                Pair(
                    it.first - minRow,
                    it.second - minColumn
                )
            }
            .distinct()
            .sortedWith(
                compareBy<Pair<Int, Int>> {
                    it.first
                }.thenBy {
                    it.second
                }
            )
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

        val detected =
            pieces.count {
                it.detected &&
                        it.cells.isNotEmpty()
            }

        var confidence = 0.50f

        confidence += 0.20f

        if (detected >= 1) {
            confidence += 0.10f
        }

        if (detected >= 2) {
            confidence += 0.10f
        }

        if (detected == 3) {
            confidence += 0.10f
        }

        return confidence.coerceIn(
            0f,
            1f
        )
    }
}
