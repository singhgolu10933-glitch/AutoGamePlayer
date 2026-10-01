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

    private const val ROWS = 8
    private const val COLUMNS = 8

    /*
     * Calibrated from the supplied Block Blitz
     * gameplay screenshot.
     *
     * Board:
     * approximately x = 14%..87%
     *          y = 20%..66%
     */

    private const val BOARD_LEFT = 0.142f
    private const val BOARD_TOP = 0.203f
    private const val BOARD_RIGHT = 0.871f
    private const val BOARD_BOTTOM = 0.657f

    /*
     * Piece tray is approximately:
     *
     * x = 16%..84%
     * y = 78%..92%
     */

    private const val PIECE_TOP = 0.765f
    private const val PIECE_BOTTOM = 0.925f

    private val PIECE_CENTERS = floatArrayOf(
        0.237f,
        0.500f,
        0.762f
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
            "VISION -> " +
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
    // BOARD DETECTION
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
                    COLUMNS

        val cellHeight =
            boardHeight.toFloat() /
                    ROWS

        val occupied =
            Array(ROWS) {
                BooleanArray(COLUMNS)
            }

        for (row in 0 until ROWS) {

            for (column in 0 until COLUMNS) {

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

                occupied[row][column] =
                    detectBoardCell(
                        bitmap,
                        centerX,
                        centerY,
                        cellWidth,
                        cellHeight
                    )
            }
        }

        return BlockBlitzBoard(
            rows = ROWS,
            columns = COLUMNS,
            occupied = occupied
        )
    }

    // ============================================================
    // BOARD CELL
    // ============================================================

    private fun detectBoardCell(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        cellWidth: Float,
        cellHeight: Float
    ): Boolean {

        val radiusX =
            max(
                5,
                (cellWidth * 0.24f).toInt()
            )

        val radiusY =
            max(
                5,
                (cellHeight * 0.24f).toInt()
            )

        var colored = 0
        var samples = 0

        val left =
            max(
                0,
                centerX - radiusX
            )

        val right =
            min(
                bitmap.width - 1,
                centerX + radiusX
            )

        val top =
            max(
                0,
                centerY - radiusY
            )

        val bottom =
            min(
                bitmap.height - 1,
                centerY + radiusY
            )

        var y = top

        while (y <= bottom) {

            var x = left

            while (x <= right) {

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

                if (
                    isColoredBlockPixel(
                        pixel
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

        val ratio =
            colored.toFloat() /
                    samples.toFloat()

        return ratio >= 0.18f
    }

    // ============================================================
    // COLORED BLOCK PIXEL
    // ============================================================

    private fun isColoredBlockPixel(
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
         * Empty board cells are dark navy.
         * Actual blocks are brighter and/or
         * more saturated.
         */

        return (
            brightness >= 75 &&
                    saturation >= 30
            )
    }

    // ============================================================
    // PIECES
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
                            PIECE_CENTERS[index]
                    ).toInt()

            val top =
                (
                    bitmap.height *
                            PIECE_TOP
                    ).toInt()

            val bottom =
                (
                    bitmap.height *
                            PIECE_BOTTOM
                    ).toInt()

            val cells =
                extractPieceShape(
                    bitmap,
                    centerX,
                    top,
                    bottom
                )

            val detected =
                cells.isNotEmpty()

            val piece =
                BlockBlitzPiece(
                    index = index,
                    cells = cells,
                    detected = detected
                )

            result.add(piece)

            Log.d(
                TAG,
                "PIECE $index -> " +
                        "detected=$detected " +
                        "cells=$cells"
            )
        }

        return result
    }

    // ============================================================
    // PIECE SHAPE
    // ============================================================

    private fun extractPieceShape(
        bitmap: Bitmap,
        centerX: Int,
        top: Int,
        bottom: Int
    ): List<Pair<Int, Int>> {

        /*
         * Each piece gets its own horizontal slot.
         */

        val slotHalfWidth =
            (
                bitmap.width * 0.095f
                ).toInt()

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
            max(
                0,
                top
            )

        val safeBottom =
            min(
                bitmap.height - 1,
                bottom
            )

        val bounds =
            findPieceBounds(
                bitmap,
                left,
                right,
                safeTop,
                safeBottom
            )
                ?: return emptyList()

        /*
         * In the supplied screenshot each
         * small piece block is approximately
         * 35 px at 960 px screen width.
         *
         * Use a proportional value so it
         * scales with screen width.
         */

        val cellSize =
            (
                bitmap.width * 0.0365f
                )
                .toFloat()

        if (cellSize <= 2f) {
            return emptyList()
        }

        val minX =
            bounds.minX

        val minY =
            bounds.minY

        val maxX =
            bounds.maxX

        val maxY =
            bounds.maxY

        val shapeWidth =
            maxX - minX + 1

        val shapeHeight =
            maxY - minY + 1

        val gridColumns =
            (
                shapeWidth /
                        cellSize
                )
                .toInt()
                .coerceIn(
                    1,
                    5
                )

        val gridRows =
            (
                shapeHeight /
                        cellSize
                )
                .toInt()
                .coerceIn(
                    1,
                    5
                )

        val cells =
            mutableListOf<Pair<Int, Int>>()

        for (row in 0 until gridRows) {

            for (column in 0 until gridColumns) {

                val sampleX =
                    (
                        minX +
                                (column + 0.5f) *
                                cellSize
                        ).toInt()

                val sampleY =
                    (
                        minY +
                                (row + 0.5f) *
                                cellSize
                        ).toInt()

                if (
                    samplePieceCell(
                        bitmap,
                        sampleX,
                        sampleY,
                        cellSize
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

        return normalizeCells(cells)
    }

    // ============================================================
    // PIECE BOUNDS
    // ============================================================

    private data class PieceBounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    )

    private fun findPieceBounds(
        bitmap: Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int
    ): PieceBounds? {

        var minX =
            right

        var minY =
            bottom

        var maxX =
            left

        var maxY =
            top

        var found =
            false

        var y =
            top

        while (y <= bottom) {

            var x =
                left

            while (x <= right) {

                if (
                    isColoredBlockPixel(
                        bitmap.getPixel(
                            x,
                            y
                        )
                    )
                ) {

                    found = true

                    minX =
                        min(
                            minX,
                            x
                        )

                    minY =
                        min(
                            minY,
                            y
                        )

                    maxX =
                        max(
                            maxX,
                            x
                        )

                    maxY =
                        max(
                            maxY,
                            y
                        )
                }

                x += 2
            }

            y += 2
        }

        if (!found) {
            return null
        }

        return PieceBounds(
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
    }

    // ============================================================
    // PIECE CELL SAMPLE
    // ============================================================

    private fun samplePieceCell(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        cellSize: Float
    ): Boolean {

        val radius =
            max(
                4,
                (cellSize * 0.28f)
                    .toInt()
            )

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

        var colored =
            0

        var samples =
            0

        var y =
            top

        while (y <= bottom) {

            var x =
                left

            while (x <= right) {

                if (
                    isColoredBlockPixel(
                        bitmap.getPixel(
                            x,
                            y
                        )
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
                    samples.toFloat()
            ) >= 0.22f
    }

    // ============================================================
    // NORMALIZE
    // ============================================================

    private fun normalizeCells(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (cells.isEmpty()) {
            return emptyList()
        }

        val minRow =
            cells.minOf {
                it.first
            }

        val minColumn =
            cells.minOf {
                it.second
            }

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

        val detectedPieces =
            pieces.count {
                it.detected &&
                        it.cells.isNotEmpty()
            }

        /*
         * Confidence now reflects actual
         * piece-shape detection instead of
         * giving 70% just because the board
         * rectangle exists.
         */

        return when {

            detectedPieces == 3 ->
                1.0f

            detectedPieces == 2 ->
                0.85f

            detectedPieces == 1 ->
                0.70f

            else ->
                0.40f
        }
    }
}
