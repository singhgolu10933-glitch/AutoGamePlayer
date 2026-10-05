package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal block-piece detector.
 *
 * Detects block pieces from the lower/tray area
 * of block-puzzle games.
 *
 * No game-specific coordinates are used.
 */
object UniversalPieceDetector {

    /**
     * Detects available pieces.
     *
     * The detector searches the lower part of the
     * screen and attempts to separate individual
     * colored pieces.
     */
    fun detect(
        bitmap: Bitmap,
        grid: UniversalGridDetector.DetectedGrid
    ): List<UniversalBlockPiece> {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return emptyList()
        }

        if (
            grid.cellWidth <= 0f ||
            grid.cellHeight <= 0f
        ) {
            return emptyList()
        }

        val tray =
            detectTray(
                bitmap,
                grid
            )

        if (tray == null) {
            return emptyList()
        }

        val regions =
            splitTray(
                bitmap,
                tray
            )

        val pieces =
            mutableListOf<UniversalBlockPiece>()

        for (
            index in
            regions.indices
        ) {

            val region =
                regions[index]

            val cells =
                detectCells(
                    bitmap = bitmap,
                    region = region,
                    grid = grid
                )

            if (cells.isEmpty()) {
                continue
            }

            pieces.add(
                UniversalBlockPiece(
                    id = index,
                    cells = cells
                )
            )
        }

        return pieces
            .take(5)
    }

    /**
     * Tray region in screen coordinates.
     */
    private data class TrayRegion(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    /**
     * Finds the area below the detected board
     * where loose pieces are normally displayed.
     */
    private fun detectTray(
        bitmap: Bitmap,
        grid: UniversalGridDetector.DetectedGrid
    ): TrayRegion? {

        val screenWidth =
            bitmap.width

        val screenHeight =
            bitmap.height

        /*
         * Normally pieces appear below the board.
         */
        val minimumTop =
            max(
                grid.bottom + 10,
                (
                    screenHeight *
                            0.58f
                    ).toInt()
            )

        val maximumTop =
            (
                screenHeight *
                        0.82f
                ).toInt()

        if (
            minimumTop >=
            screenHeight
        ) {
            return null
        }

        val top =
            min(
                minimumTop,
                maximumTop
            )

        val bottom =
            (
                screenHeight *
                        0.97f
                ).toInt()
                .coerceAtMost(
                    screenHeight - 1
                )

        if (
            bottom <= top
        ) {
            return null
        }

        /*
         * Use a broad horizontal region.
         */
        val left =
            (
                screenWidth *
                        0.04f
                ).toInt()

        val right =
            (
                screenWidth *
                        0.96f
                ).toInt()

        if (
            right <= left
        ) {
            return null
        }

        return TrayRegion(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }

    /**
     * Splits the tray into broad candidate
     * regions.
     *
     * Most block puzzle games show 3 pieces,
     * but some can show more.
     */
    private fun splitTray(
        bitmap: Bitmap,
        tray: TrayRegion
    ): List<TrayRegion> {

        val width =
            tray.right -
                    tray.left

        if (width <= 0) {
            return emptyList()
        }

        /*
         * First try 3 slots.
         */
        val threeSlotRegions =
            createSlots(
                tray,
                3
            )

        val detectedThree =
            threeSlotRegions.filter {
                regionContainsPiece(
                    bitmap,
                    it
                )
            }

        if (detectedThree.isNotEmpty()) {
            return detectedThree
        }

        /*
         * Some games use 4 pieces.
         */
        val fourSlotRegions =
            createSlots(
                tray,
                4
            )

        val detectedFour =
            fourSlotRegions.filter {
                regionContainsPiece(
                    bitmap,
                    it
                )
            }

        if (detectedFour.isNotEmpty()) {
            return detectedFour
        }

        /*
         * Some games use 5 pieces.
         */
        val fiveSlotRegions =
            createSlots(
                tray,
                5
            )

        val detectedFive =
            fiveSlotRegions.filter {
                regionContainsPiece(
                    bitmap,
                    it
                )
            }

        if (detectedFive.isNotEmpty()) {
            return detectedFive
        }

        /*
         * Last fallback:
         * split the tray into smaller regions.
         */
        return createSlots(
            tray,
            3
        )
    }

    /**
     * Creates equally spaced tray slots.
     */
    private fun createSlots(
        tray: TrayRegion,
        count: Int
    ): List<TrayRegion> {

        if (count <= 0) {
            return emptyList()
        }

        val width =
            tray.right -
                    tray.left

        val slotWidth =
            width.toFloat() /
                    count.toFloat()

        val result =
            mutableListOf<TrayRegion>()

        /*
         * Keep some horizontal padding inside
         * each slot so neighboring pieces don't
         * merge together.
         */
        val padding =
            (
                slotWidth *
                        0.10f
                ).toInt()

        for (
            index in
            0 until count
        ) {

            val rawLeft =
                (
                    tray.left +
                            index *
                            slotWidth
                    ).toInt()

            val rawRight =
                (
                    tray.left +
                            (
                                index + 1
                            ) *
                            slotWidth
                    ).toInt()

            val left =
                (
                    rawLeft +
                            padding
                    )
                    .coerceAtLeast(
                        tray.left
                    )

            val right =
                (
                    rawRight -
                            padding
                    )
                    .coerceAtMost(
                        tray.right
                    )

            if (
                right <= left
            ) {
                continue
            }

            result.add(
                TrayRegion(
                    left = left,
                    top = tray.top,
                    right = right,
                    bottom = tray.bottom
                )
            )
        }

        return result
    }

    /**
     * Checks whether a slot contains colored/
     * bright pixels that could represent a piece.
     */
    private fun regionContainsPiece(
        bitmap: Bitmap,
        region: TrayRegion
    ): Boolean {

        var coloredPixels =
            0

        var sampledPixels =
            0

        /*
         * Sample every few pixels rather than
         * scanning the entire region.
         */
        val stepX =
            max(
                2,
                (
                    (
                        region.right -
                                region.left
                        ) /
                            30
                    )
            )

        val stepY =
            max(
                2,
                (
                    (
                        region.bottom -
                                region.top
                        ) /
                            25
                    )
            )

        for (
            y in
            region.top until region.bottom
                step stepY
        ) {

            for (
                x in
                region.left until region.right
                    step stepX
            ) {

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

                sampledPixels++

                if (
                    isPiecePixel(
                        pixel
                    )
                ) {
                    coloredPixels++
                }
            }
        }

        if (
            sampledPixels <= 0
        ) {
            return false
        }

        val ratio =
            coloredPixels.toFloat() /
                    sampledPixels.toFloat()

        return ratio >=
                0.035f
    }

    /**
     * Detects relative cells inside a piece.
     */
    private fun detectCells(
        bitmap: Bitmap,
        region: TrayRegion,
        grid: UniversalGridDetector.DetectedGrid
    ): List<UniversalCell> {

        val cellSize =
            min(
                grid.cellWidth,
                grid.cellHeight
            )

        if (
            cellSize <= 2f
        ) {
            return emptyList()
        }

        /*
         * We scan the region using approximate
         * board-cell-sized samples.
         */
        val regionWidth =
            (
                region.right -
                        region.left
                )
                .toFloat()

        val regionHeight =
            (
                region.bottom -
                        region.top
                )
                .toFloat()

        if (
            regionWidth <= 0f ||
            regionHeight <= 0f
        ) {
            return emptyList()
        }

        /*
         * Determine a safe maximum piece grid.
         *
         * Most block pieces fit within 5x5.
         */
        val maxColumns =
            5

        val maxRows =
            5

        /*
         * Approximate center of the candidate.
         */
        val centerX =
            (
                region.left +
                        region.right
                ) / 2f

        val centerY =
            (
                region.top +
                        region.bottom
                ) / 2f

        /*
         * Estimate how many cells could fit.
         */
        val estimatedColumns =
            (
                regionWidth /
                        cellSize
                )
                .toInt()
                .coerceIn(
                    1,
                    maxColumns
                )

        val estimatedRows =
            (
                regionHeight /
                        cellSize
                )
                .toInt()
                .coerceIn(
                    1,
                    maxRows
                )

        /*
         * We don't assume that the piece starts
         * at the exact region center. Try several
         * offsets and select the strongest result.
         */
        var bestCells =
            emptyList<UniversalCell>()

        var bestScore =
            0f

        val offsetRange =
            -2..2

        for (
            offsetY in
            offsetRange
        ) {

            for (
                offsetX in
                offsetRange
            ) {

                val candidate =
                    samplePieceGrid(
                        bitmap = bitmap,
                        centerX =
                            centerX +
                                    offsetX *
                                    cellSize *
                                    0.18f,
                        centerY =
                            centerY +
                                    offsetY *
                                    cellSize *
                                    0.18f,
                        cellSize = cellSize,
                        rows =
                            estimatedRows,
                        columns =
                            estimatedColumns
                    )

                val score =
                    pieceShapeScore(
                        candidate
                    )

                if (
                    score >
                    bestScore
                ) {

                    bestScore =
                        score

                    bestCells =
                        candidate
                }
            }
        }

        /*
         * Very tiny or noisy detections are
         * rejected.
         */
        if (
            bestCells.isEmpty()
        ) {
            return emptyList()
        }

        if (
            bestCells.size > 25
        ) {
            return emptyList()
        }

        return normalizeCells(
            bestCells
        )
    }

    /**
     * Samples an approximate piece grid.
     */
    private fun samplePieceGrid(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        cellSize: Float,
        rows: Int,
        columns: Int
    ): List<UniversalCell> {

        if (
            rows <= 0 ||
            columns <= 0
        ) {
            return emptyList()
        }

        val totalWidth =
            columns *
                    cellSize

        val totalHeight =
            rows *
                    cellSize

        val startX =
            centerX -
                    totalWidth /
                    2f

        val startY =
            centerY -
                    totalHeight /
                    2f

        val cells =
            mutableListOf<UniversalCell>()

        for (
            row in
            0 until rows
        ) {

            for (
                column in
                0 until columns
            ) {

                val cellCenterX =
                    startX +
                            (
                                column +
                                        0.5f
                                ) *
                            cellSize

                val cellCenterY =
                    startY +
                            (
                                row +
                                        0.5f
                                ) *
                            cellSize

                val brightness =
                    sampleBrightness(
                        bitmap,
                        cellCenterX,
                        cellCenterY,
                        cellSize
                    )

                val colorStrength =
                    sampleColorStrength(
                        bitmap,
                        cellCenterX,
                        cellCenterY,
                        cellSize
                    )

                if (
                    isLikelyPieceCell(
                        brightness,
                        colorStrength
                    )
                ) {

                    cells.add(
                        UniversalCell(
                            row = row,
                            column = column
                        )
                    )
                }
            }
        }

        return cells
    }

    /**
     * Calculates brightness around a point.
     */
    private fun sampleBrightness(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        cellSize: Float
    ): Float {

        val radius =
            (
                cellSize *
                        0.20f
                )
                .coerceAtLeast(
                    2f
                )

        val startX =
            (
                centerX -
                        radius
                ).toInt()

        val endX =
            (
                centerX +
                        radius
                ).toInt()

        val startY =
            (
                centerY -
                        radius
                ).toInt()

        val endY =
            (
                centerY +
                        radius
                ).toInt()

        var total =
            0f

        var count =
            0

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

                total +=
                    red * 0.299f +
                            green * 0.587f +
                            blue * 0.114f

                count++
            }
        }

        if (
            count == 0
        ) {
            return 0f
        }

        return total /
                count.toFloat()
    }

    /**
     * Measures how strongly colored a pixel area is.
     */
    private fun sampleColorStrength(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        cellSize: Float
    ): Float {

        val radius =
            (
                cellSize *
                        0.20f
                )
                .coerceAtLeast(
                    2f
                )

        val startX =
            (
                centerX -
                        radius
                ).toInt()

        val endX =
            (
                centerX +
                        radius
                ).toInt()

        val startY =
            (
                centerY -
                        radius
                ).toInt()

        val endY =
            (
                centerY +
                        radius
                ).toInt()

        var total =
            0f

        var count =
            0

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

                val maximum =
                    max(
                        red,
                        max(
                            green,
                            blue
                        )
                    )

                val minimum =
                    min(
                        red,
                        min(
                            green,
                            blue
                        )
                    )

                total +=
                    (
                        maximum -
                                minimum
                        )
                        .toFloat()

                count++
            }
        }

        if (
            count == 0
        ) {
            return 0f
        }

        return total /
                count.toFloat()
    }

    /**
     * Determines whether a pixel area
     * looks like a colored block.
     */
    private fun isPiecePixel(
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
            max(
                red,
                max(
                    green,
                    blue
                )
            )

        val minimum =
            min(
                red,
                min(
                    green,
                    blue
                )
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
         * Colored pieces usually have either:
         *
         * - noticeable saturation
         * - relatively high brightness
         */
        return (
            saturation >= 35 &&
                    brightness >= 45
            ) ||
                brightness >= 145
    }

    /**
     * Determines whether a sampled cell
     * looks like an actual piece block.
     */
    private fun isLikelyPieceCell(
        brightness: Float,
        colorStrength: Float
    ): Boolean {

        return (
            colorStrength >= 20f &&
                    brightness >= 35f
            ) ||
                brightness >= 155f
    }

    /**
     * Scores how plausible a detected shape is.
     */
    private fun pieceShapeScore(
        cells: List<UniversalCell>
    ): Float {

        if (
            cells.isEmpty()
        ) {
            return 0f
        }

        if (
            cells.size > 25
        ) {
            return 0f
        }

        val minRow =
            cells.minOf {
                it.row
            }

        val maxRow =
            cells.maxOf {
                it.row
            }

        val minColumn =
            cells.minOf {
                it.column
            }

        val maxColumn =
            cells.maxOf {
                it.column
            }

        val height =
            maxRow -
                    minRow +
                    1

        val width =
            maxColumn -
                    minColumn +
                    1

        if (
            height <= 0 ||
            width <= 0
        ) {
            return 0f
        }

        val boundingArea =
            height *
                    width

        val density =
            cells.size.toFloat() /
                    boundingArea.toFloat()

        /*
         * Block pieces generally have
         * reasonably compact shapes.
         */
        var score =
            cells.size *
                    10f

        score +=
            density *
                    100f

        /*
         * Prefer shapes with 1..25 cells.
         */
        if (
            cells.size in 1..25
        ) {
            score += 50f
        }

        /*
         * Penalize very sparse shapes.
         */
        if (
            density < 0.25f
        ) {
            score -= 60f
        }

        return score
    }

    /**
     * Normalizes a detected shape to start at
     * row 0 / column 0.
     */
    private fun normalizeCells(
        cells: List<UniversalCell>
    ): List<UniversalCell> {

        if (
            cells.isEmpty()
        ) {
            return emptyList()
        }

        val minRow =
            cells.minOf {
                it.row
            }

        val minColumn =
            cells.minOf {
                it.column
            }

        return cells
            .map {
                UniversalCell(
                    row =
                        it.row -
                                minRow,
                    column =
                        it.column -
                                minColumn
                )
            }
            .distinct()
            .sortedWith(
                compareBy<UniversalCell> {
                    it.row
                }.thenBy {
                    it.column
                }
            )
    }
}
