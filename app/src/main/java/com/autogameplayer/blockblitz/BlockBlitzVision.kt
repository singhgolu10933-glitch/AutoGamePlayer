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

            row.count { cell ->
                cell
            }
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

    private const val TAG =
        "BlockBlitzVision"

    /*
     * Approximate Block Blitz board area.
     *
     * These values are normalized screen
     * coordinates and can be calibrated later.
     */

    private const val BOARD_LEFT =
        0.28f

    private const val BOARD_TOP =
        0.30f

    private const val BOARD_RIGHT =
        0.88f

    private const val BOARD_BOTTOM =
        0.70f

    private const val BOARD_ROWS =
        8

    private const val BOARD_COLUMNS =
        8

    // =============================================================
    // MAIN ANALYSIS
    // =============================================================

    fun analyze(
        bitmap: Bitmap
    ): BlockBlitzState {

        val board =
            detectBoard(
                bitmap
            )

        val pieces =
            detectPieces(
                bitmap
            )

        val confidence =
            calculateConfidence(
                board,
                pieces
            )

        Log.d(
            TAG,
            "Vision result: " +
                    "occupied=" +
                    "${board?.occupiedCount() ?: -1}, " +
                    "pieces=" +
                    pieces.count {
                        it.detected
                    } +
                    ", confidence=" +
                    confidence
        )

        return BlockBlitzState(
            board = board,
            pieces = pieces,
            confidence = confidence
        )
    }

    // =============================================================
    // BOARD DETECTION
    // =============================================================

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
            (
                bitmap.width *
                        BOARD_LEFT
                ).toInt()

        val top =
            (
                bitmap.height *
                        BOARD_TOP
                ).toInt()

        val right =
            (
                bitmap.width *
                        BOARD_RIGHT
                ).toInt()

        val bottom =
            (
                bitmap.height *
                        BOARD_BOTTOM
                ).toInt()

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

        var samples =
            0

        for (
            row in
            0 until BOARD_ROWS
        ) {

            for (
                column in
                0 until BOARD_COLUMNS
            ) {

                val centerX =
                    left +
                            (
                                (
                                    column + 0.5f
                                ) *
                                        cellWidth
                                ).toInt()

                val centerY =
                    top +
                            (
                                (
                                    row + 0.5f
                                ) *
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

                val pixel =
                    bitmap.getPixel(
                        centerX,
                        centerY
                    )

                occupied[row][column] =
                    isLikelyOccupied(
                        pixel
                    )

                samples++
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

    // =============================================================
    // CELL DETECTION
    // =============================================================

    private fun isLikelyOccupied(
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
         * Colored blocks generally have
         * higher brightness and saturation
         * than the dark empty board.
         */

        if (
            brightness > 75 &&
            saturation > 35
        ) {

            return true
        }

        return false
    }

    // =============================================================
    // PIECE DETECTION
    // =============================================================

    private fun detectPieces(
        bitmap: Bitmap
    ): List<BlockBlitzPiece> {

        val result =
            mutableListOf<BlockBlitzPiece>()

        /*
         * Approximate lower piece area.
         */

        val yStart =
            (
                bitmap.height *
                        0.72f
                ).toInt()

        val yEnd =
            (
                bitmap.height *
                        0.94f
                ).toInt()

        val slotCenters =
            listOf(
                bitmap.width * 0.28f,
                bitmap.width * 0.50f,
                bitmap.width * 0.72f
            )

        for (
            index in 0 until 3
        ) {

            val centerX =
                slotCenters[index]
                    .toInt()

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

    // =============================================================
    // COLORED PIXEL SEARCH
    // =============================================================

    private fun hasColoredPixels(
        bitmap: Bitmap,
        centerX: Int,
        top: Int,
        bottom: Int
    ): Boolean {

        val halfWidth =
            (
                bitmap.width *
                        0.10f
                ).toInt()

        val left =
            (
                centerX -
                        halfWidth
                ).coerceAtLeast(0)

        val right =
            (
                centerX +
                        halfWidth
                ).coerceAtMost(
                    bitmap.width - 1
                )

        val safeTop =
            top.coerceAtLeast(0)

        val safeBottom =
            bottom.coerceAtMost(
                bitmap.height - 1
            )

        var coloredPixels =
            0

        var y =
            safeTop

        while (
            y <= safeBottom
        ) {

            var x =
                left

            while (
                x <= right
            ) {

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

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

                if (
                    brightness > 80 &&
                    saturation > 40
                ) {

                    coloredPixels++
                }

                if (
                    coloredPixels >= 20
                ) {

                    return true
                }

                x += 8
            }

            y += 8
        }

        return false
    }

    // =============================================================
    // CONFIDENCE
    // =============================================================

    private fun calculateConfidence(
        board: BlockBlitzBoard?,
        pieces: List<BlockBlitzPiece>
    ): Float {

        if (board == null) {
            return 0f
        }

        val pieceCount =
            pieces.count {
                it.detected
            }

        var confidence =
            0.5f

        if (
            board.occupiedCount() >= 0
        ) {

            confidence +=
                0.2f
        }

        if (
            pieceCount > 0
        ) {

            confidence +=
                0.1f
        }

        if (
            pieceCount >= 2
        ) {

            confidence +=
                0.1f
        }

        if (
            pieceCount == 3
        ) {

            confidence +=
                0.1f
        }

        return confidence.coerceIn(
            0f,
            1f
        )
    }
}
