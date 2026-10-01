package com.autogameplayer.blockblitz

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    // ------------------------------------------------------------
    // CALIBRATED BOARD
    // ------------------------------------------------------------

    private const val BOARD_LEFT = 0.142f
    private const val BOARD_TOP = 0.203f
    private const val BOARD_RIGHT = 0.871f
    private const val BOARD_BOTTOM = 0.657f

    // ------------------------------------------------------------
    // PIECE TRAY
    // ------------------------------------------------------------

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

        val boardCellSize =
            if (board != null) {
                calculateBoardCellSize(
                    bitmap
                )
            } else {
                0f
            }

        val pieces =
            detectPieces(
                bitmap,
                boardCellSize
            )

        val confidence =
            calculateConfidence(
                board,
                pieces
            )

        Log.d(
            TAG,
            "VISION RESULT -> " +
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

        if (
            right <= left ||
            bottom <= top
        ) {
            return null
        }

        val boardWidth =
            right - left

        val boardHeight =
            bottom - top

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
                (cellWidth * 0.25f)
                    .toInt()
            )

        val radiusY =
            max(
                5,
                (cellHeight * 0.25f)
                    .toInt()
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

                if (
                    isBlockPixel(
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

        val ratio =
            colored.toFloat() /
                    samples.toFloat()

        return ratio >= 0.18f
    }

    // ============================================================
    // BLOCK PIXEL
    // ============================================================

    private fun isBlockPixel(
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

        return (
            brightness >= 75 &&
                    saturation >= 30
            )
    }

    // ============================================================
    // BOARD CELL SIZE
    // ============================================================

    private fun calculateBoardCellSize(
        bitmap: Bitmap
    ): Float {

        val boardWidth =
            bitmap.width *
                    (
                        BOARD_RIGHT -
                                BOARD_LEFT
                        )

        return boardWidth /
                COLUMNS.toFloat()
    }

    // ============================================================
    // PIECES
    // ============================================================

    private fun detectPieces(
        bitmap: Bitmap,
        boardCellSize: Float
    ): List<BlockBlitzPiece> {

        val result =
            mutableListOf<BlockBlitzPiece>()

        /*
         * The piece blocks in the supplied
         * screenshot are substantially smaller
         * than board cells.
         *
         * Approximate piece block size:
         * 0.40 × board cell size.
         */

        val pieceCellSize =
            (
                boardCellSize * 0.40f
                )
                .coerceAtLeast(8f)

        for (index in 0..2) {

            val centerX =
                (
                    bitmap.width *
                            PIECE_CENTERS[index]
                    )
                    .toInt()

            val top =
                (
                    bitmap.height *
                            PIECE_TOP
                    )
                    .toInt()

            val bottom =
                (
                    bitmap.height *
                            PIECE_BOTTOM
                    )
                    .toInt()

            val cells =
                extractPieceShape(
                    bitmap = bitmap,
                    centerX = centerX,
                    top = top,
                    bottom = bottom,
                    pieceCellSize = pieceCellSize
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
                "PIECE ${index + 1} -> " +
                        "detected=$detected " +
                        "cells=$cells"
            )
        }

        return result
    }

    // ============================================================
    // EXTRACT PIECE
    // ============================================================

    private fun extractPieceShape(
        bitmap: Bitmap,
        centerX: Int,
        top: Int,
        bottom: Int,
        pieceCellSize: Float
    ): List<Pair<Int, Int>> {

        /*
         * Each piece has a separate tray slot.
         */

        val slotHalfWidth =
            (
                bitmap.width * 0.105f
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
            findColoredBounds(
                bitmap,
                left,
                right,
                safeTop,
                safeBottom
            )
                ?: return emptyList()

        val width =
            bounds.maxX -
                    bounds.minX +
                    1

        val height =
            bounds.maxY -
                    bounds.minY +
                    1

        if (
            width <= 0 ||
            height <= 0
        ) {
            return emptyList()
        }

        /*
         * Estimate number of cells.
         *
         * Round rather than truncate so:
         *
         * 4 blocks -> 4
         * 3 blocks -> 3
         * 2 blocks -> 2
         */

        val columns =
            (
                width /
                        pieceCellSize
                )
                .roundToInt()
                .coerceIn(
                    1,
                    5
                )

        val rows =
            (
                height /
                        pieceCellSize
                )
                .roundToInt()
                .coerceIn(
                    1,
                    5
                )

        /*
         * If the estimated size is wildly
         * inconsistent, reject it.
         */

        if (
            columns < 1 ||
            columns > 5 ||
            rows < 1 ||
            rows > 5
        ) {
            return emptyList()
        }

        /*
         * Reconstruct cells using the
         * detected bounding-box center.
         */

        val detectedCells =
            mutableListOf<Pair<Int, Int>>()

        for (row in 0 until rows) {

            for (column in 0 until columns) {

                val sampleX =
                    (
                        bounds.minX +
                                (
                                    column + 0.5f
                                    ) *
                                pieceCellSize
                        )
                        .toInt()

                val sampleY =
                    (
                        bounds.minY +
                                (
                                    row + 0.5f
                                    ) *
                                pieceCellSize
                        )
                        .toInt()

                if (
                    samplePieceCell(
                        bitmap,
                        sampleX,
                        sampleY,
                        pieceCellSize
                    )
                ) {

                    detectedCells.add(
                        Pair(
                            row,
                            column
                        )
                    )
                }
            }
        }

        val normalized =
            normalizeCells(
                detectedCells
            )

        /*
         * A valid piece should have at least
         * one and at most 25 cells.
         */

        if (
            normalized.isEmpty() ||
            normalized.size > 25
        ) {
            return emptyList()
        }

        return normalized
    }

    // ============================================================
    // BOUNDS
    // ============================================================

    private data class PieceBounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    )

    private fun findColoredBounds(
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
                    isBlockPixel(
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
            minX,
            minY,
            maxX,
            maxY
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
                3,
                (
                    cellSize * 0.28f
                    ).toInt()
            )

        var colored =
            0

        var samples =
            0

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

        var y =
            top

        while (y <= bottom) {

            var x =
                left

            while (x <= right) {

                if (
                    isBlockPixel(
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
            ) >= 0.20f
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

        return when {

            detectedPieces == 3 ->
                1.0f

            detectedPieces == 2 ->
                0.80f

            detectedPieces == 1 ->
                0.60f

            else ->
                0.35f
        }
    }
}
