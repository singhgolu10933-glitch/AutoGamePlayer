package com.autogameplayer.blockblitz

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs

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

    /*
     * These are normalized screen coordinates.
     *
     * They describe the approximate Block Blitz
     * board area seen on the test device.
     *
     * They can later be calibrated automatically.
     */
    private const val BOARD_LEFT = 0.28f
    private const val BOARD_TOP = 0.30f
    private const val BOARD_RIGHT = 0.88f
    private const val BOARD_BOTTOM = 0.70f

    private const val BOARD_ROWS = 8
    private const val BOARD_COLUMNS = 8

    fun analyze(bitmap: Bitmap): BlockBlitzState {

        val board = detectBoard(bitmap)

        val pieces = detectPieces(bitmap)

        val confidence =
            calculateConfidence(board, pieces)

        Log.d(
            TAG,
            "Vision: boardCells=" +
                    "${board?.occupiedCount() ?: -1}, " +
                    "pieces=${pieces.count { it.detected }}, " +
                    "confidence=$confidence"
        )

        return BlockBlitzState(
            board = board,
            pieces = pieces,
            confidence = confidence
        )
    }

    private fun detectBoard(
        bitmap: Bitmap
    ): BlockBlitzBoard? {

        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        val left =
            (bitmap.width * BOARD_LEFT).toInt()

        val top =
            (bitmap.height * BOARD_TOP).toInt()

        val right =
            (bitmap.width * BOARD_RIGHT).toInt()

        val bottom =
            (bitmap.height * BOARD_BOTTOM).toInt()

        val boardWidth =
            right - left

        val boardHeight =
            bottom - top

        if (boardWidth <= 0 || boardHeight <= 0) {
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

        var samples = 0
        var detected = 0

        for (row in 0 until BOARD_ROWS) {

            for (column in 0 until BOARD_COLUMNS) {

                val centerX =
                    left +
                            ((column + 0.5f) * cellWidth)
                                .toInt()

                val centerY =
                    top +
                            ((row + 0.5f) * cellHeight)
                                .toInt()

                if (
                    centerX < 0 ||
                    centerY < 0 ||
                    centerX >= bitmap.width ||
                    centerY >= bitmap.height
                ) {
                    continue
                }

                val pixel =
                    bitmap.getPixel(
                        centerX,
                        centerY
                    )

                val occupiedCell =
                    isLikelyOccupied(pixel)

                occupied[row][column] =
                    occupiedCell

                samples++

                if (occupiedCell) {
                    detected++
                }
            }
        }

        if (samples == 0) {
            return null
        }

        return BlockBlitzBoard(
            rows = BOARD_ROWS,
            columns = BOARD_COLUMNS,
            occupied = occupied
        )
    }

    private fun isLikelyOccupied(
        pixel: Int
    ): Boolean {

        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        val max =
            maxOf(r, g, b)

        val min =
            minOf(r, g, b)

        val saturation =
            max - min

        val brightness =
            (r + g + b) / 3

        /*
         * Empty board cells are generally dark.
         *
         * Colored blocks have:
         * - stronger RGB separation
         * - higher brightness
         */
        if (brightness > 75 && saturation > 35) {
            return true
        }

        return false
    }

    private fun detectPieces(
        bitmap: Bitmap
    ): List<BlockBlitzPiece> {

        /*
         * The three pieces are located in the
         * lower part of the Block Blitz screen.
         *
         * For this first version we only determine
         * whether each piece slot contains a
         * recognizable colored object.
         *
         * Exact shape recognition comes next.
         */

        val result = mutableListOf<BlockBlitzPiece>()

        val yStart =
            (bitmap.height * 0.72f).toInt()

        val yEnd =
            (bitmap.height * 0.94f).toInt()

        val slotCenters =
            listOf(
                bitmap.width * 0.28f,
                bitmap.width * 0.50f,
                bitmap.width * 0.72f
            )

        for (index in 0 until 3) {

            val centerX =
                slotCenters[index].toInt()

            val detected =
                hasColoredPixels(
                    bitmap,
                    centerX,
                    yStart,
                    yEnd
                )

            result.add(
                BlockBlitzPiece(
                    index = index,
                    cells = emptyList(),
                    detected = detected
                )
            )
        }

        return result
    }

    private fun hasColoredPixels(
        bitmap: Bitmap,
        centerX: Int,
        top: Int,
        bottom: Int
    ): Boolean {

        val halfWidth =
            (bitmap.width * 0.10f).toInt()

        val left =
            (centerX - halfWidth)
                .coerceAtLeast(0)

        val right =
            (centerX + halfWidth)
                .coerceAtMost(bitmap.width - 1)

        val safeTop =
            top.coerceAtLeast(0)

        val safeBottom =
            bottom.coerceAtMost(bitmap.height - 1)

        var coloredPixels = 0

        /*
         * Sample every few pixels to keep CPU usage low.
         */
        var y = safeTop

        while (y <= safeBottom) {

            var x = left

            while (x <= right) {

                val pixel =
                    bitmap.getPixel(x, y)

                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val max =
                    maxOf(r, g, b)

                val min =
                    minOf(r, g, b)

                val saturation =
                    max - min

                val brightness =
                    (r + g + b) / 3

                if (
                    brightness > 80 &&
                    saturation > 40
                ) {
                    coloredPixels++
                }

                if (coloredPixels >= 20) {
                    return true
                }

                x += 8
            }

            y += 8
        }

        return false
    }

    private fun calculateConfidence(
        board: BlockBlitzBoard?,
        pieces: List<BlockBlitzPiece>
    ): Float {

        if (board == null) {
            return 0f
        }

        val pieceCount =
            pieces.count { it.detected }

        var confidence = 0.5f

        if (board.occupiedCount() >= 0) {
            confidence += 0.2f
        }

        if (pieceCount > 0) {
            confidence += 0.1f
        }

        if (pieceCount >= 2) {
            confidence += 0.1f
        }

        if (pieceCount == 3) {
            confidence += 0.1f
        }

        return confidence.coerceIn(0f, 1f)
    }
}
