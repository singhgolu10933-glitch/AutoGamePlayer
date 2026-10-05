package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fast universal block-piece detector.
 *
 * Designed for block puzzle games with:
 * - 1 to 5 pieces
 * - different piece shapes
 * - different colors
 * - different board sizes
 *
 * Performance:
 * - Uses downsampling
 * - Avoids full-resolution flood fill
 * - Uses local color-density detection
 * - Limits the tray search area
 */
object UniversalPieceDetector {

    private const val MAX_PIECES = 5

    /*
     * Scan every 3rd pixel.
     * Smaller = more accurate but slower.
     */
    private const val SAMPLE_STEP = 3

    /*
     * The pieces normally occupy the lower part
     * of the screen.
     */
    private const val TRAY_TOP_RATIO = 0.66f
    private const val TRAY_BOTTOM_RATIO = 0.96f

    /*
     * Maximum number of detected cell centers.
     */
    private const val MAX_CELLS = 40

    /**
     * Main detector.
     */
    fun detect(
        bitmap: Bitmap,
        grid: DetectedGrid
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
            calculateTray(
                bitmap,
                grid
            )
                ?: return emptyList()

        /*
         * First detect individual colored block cells.
         */
        val cells =
            detectColoredCells(
                bitmap,
                tray
            )

        if (cells.isEmpty()) {
            return emptyList()
        }

        /*
         * Group nearby cells into actual pieces.
         */
        val groups =
            groupCells(
                cells
            )

        if (groups.isEmpty()) {
            return emptyList()
        }

        val result =
            mutableListOf<UniversalBlockPiece>()

        for (
            index in
            groups.indices
        ) {

            if (
                index >= MAX_PIECES
            ) {
                break
            }

            val shape =
                normalizeGroup(
                    groups[index]
                )

            if (
                shape.isEmpty()
            ) {
                continue
            }

            /*
             * A valid block piece normally has
             * between 1 and 25 cells.
             */
            if (
                shape.size > 25
            ) {
                continue
            }

            result.add(
                UniversalBlockPiece(
                    id = index,
                    cells = shape
                )
            )
        }

        return result
    }

    // ============================================================
    // TRAY
    // ============================================================

    private data class Tray(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private fun calculateTray(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Tray? {

        val width =
            bitmap.width

        val height =
            bitmap.height

        /*
         * Use the detected board width as the horizontal
         * reference, but allow the complete tray width.
         */
        val left =
            (
                width *
                        0.05f
                )
                .toInt()
                .coerceAtLeast(0)

        val right =
            (
                width *
                        0.95f
                )
                .toInt()
                .coerceAtMost(
                    width - 1
                )

        /*
         * Start below the board.
         *
         * If board detection gives a useful bottom,
         * prefer that.
         */
        val boardBottom =
            grid.bottom *
                    height.toFloat()

        val top =
            max(
                (
                    height *
                            TRAY_TOP_RATIO
                    ).toInt(),

                (
                    boardBottom +
                            grid.cellHeight * 1.2f
                    ).toInt()
            )
                .coerceAtMost(
                    height - 20
                )

        val bottom =
            (
                height *
                        TRAY_BOTTOM_RATIO
                )
                .toInt()
                .coerceAtMost(
                    height - 1
                )

        if (
            right <= left ||
            bottom <= top
        ) {
            return null
        }

        return Tray(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }

    // ============================================================
    // DETECT COLORED CELLS
    // ============================================================

    private data class DetectedCell(
        val centerX: Float,
        val centerY: Float,
        val size: Float
    )

    private fun detectColoredCells(
        bitmap: Bitmap,
        tray: Tray
    ): List<DetectedCell> {

        /*
         * First pass:
         * Find colored pixels.
         */
        val points =
            mutableListOf<Pair<Int, Int>>()

        var y =
            tray.top

        while (
            y < tray.bottom
        ) {

            var x =
                tray.left

            while (
                x < tray.right
            ) {

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

                if (
                    isStrongPiecePixel(
                        pixel
                    )
                ) {

                    points.add(
                        x to y
                    )

                    if (
                        points.size >=
                        5000
                    ) {
                        break
                    }
                }

                x += SAMPLE_STEP
            }

            if (
                points.size >=
                5000
            ) {
                break
            }

            y += SAMPLE_STEP
        }

        if (
            points.isEmpty()
        ) {
            return emptyList()
        }

        /*
         * Build horizontal and vertical density maps.
         */
        val densityStep =
            6

        val densityWidth =
            (
                tray.right -
                        tray.left
                ) /
                    densityStep +
                    1

        val densityHeight =
            (
                tray.bottom -
                        tray.top
                ) /
                    densityStep +
                    1

        val xDensity =
            IntArray(
                densityWidth
            )

        val yDensity =
            IntArray(
                densityHeight
            )

        for (
            point in
            points
        ) {

            val px =
                (
                    point.first -
                            tray.left
                    ) /
                        densityStep

            val py =
                (
                    point.second -
                            tray.top
                    ) /
                        densityStep

            if (
                px in xDensity.indices
            ) {
                xDensity[px]++
            }

            if (
                py in yDensity.indices
            ) {
                yDensity[py]++
            }
        }

        /*
         * Locate colored regions using density.
         */
        val xRegions =
            findDensityRegions(
                xDensity,
                threshold = 2
            )

        val yRegions =
            findDensityRegions(
                yDensity,
                threshold = 2
            )

        if (
            xRegions.isEmpty() ||
            yRegions.isEmpty()
        ) {
            return fallbackCellDetection(
                points,
                tray
            )
        }

        /*
         * Combine density regions into candidate cells.
         */
        val candidates =
            mutableListOf<DetectedCell>()

        for (
            xr in
            xRegions
        ) {

            for (
                yr in
                yRegions
            ) {

                val left =
                    tray.left +
                            xr.first *
                            densityStep

                val right =
                    tray.left +
                            (
                                xr.second + 1
                            ) *
                            densityStep

                val top =
                    tray.top +
                            yr.first *
                            densityStep

                val bottom =
                    tray.top +
                            (
                                yr.second + 1
                            ) *
                            densityStep

                val cellWidth =
                    right -
                            left

                val cellHeight =
                    bottom -
                            top

                /*
                 * Block cells are approximately square.
                 */
                if (
                    cellWidth < 12 ||
                    cellHeight < 12
                ) {
                    continue
                }

                if (
                    cellWidth > 90 ||
                    cellHeight > 90
                ) {
                    continue
                }

                val ratio =
                    cellWidth.toFloat() /
                            cellHeight.toFloat()

                if (
                    ratio < 0.55f ||
                    ratio > 1.80f
                ) {
                    continue
                }

                val centerX =
                    (
                        left +
                                right
                        ) / 2f

                val centerY =
                    (
                        top +
                                bottom
                        ) / 2f

                /*
                 * Verify the center is actually colored.
                 */
                val cx =
                    centerX
                        .toInt()
                        .coerceIn(
                            0,
                            bitmap.width - 1
                        )

                val cy =
                    centerY
                        .toInt()
                        .coerceIn(
                            0,
                            bitmap.height - 1
                        )

                if (
                    !isStrongPiecePixel(
                        bitmap.getPixel(
                            cx,
                            cy
                        )
                    )
                ) {
                    continue
                }

                candidates.add(
                    DetectedCell(
                        centerX = centerX,
                        centerY = centerY,
                        size =
                            (
                                cellWidth +
                                        cellHeight
                                ) / 2f
                    )
                )

                if (
                    candidates.size >=
                    MAX_CELLS
                ) {
                    break
                }
            }

            if (
                candidates.size >=
                MAX_CELLS
            ) {
                break
            }
        }

        /*
         * Remove duplicate/overlapping candidates.
         */
        return removeDuplicateCells(
            candidates
        )
    }

    // ============================================================
    // DENSITY REGIONS
    // ============================================================

    private fun findDensityRegions(
        density: IntArray,
        threshold: Int
    ): List<Pair<Int, Int>> {

        val regions =
            mutableListOf<Pair<Int, Int>>()

        var start =
            -1

        for (
            index in
            density.indices
        ) {

            val active =
                density[index] >=
                        threshold

            if (
                active &&
                start < 0
            ) {

                start =
                    index
            }

            if (
                !active &&
                start >= 0
            ) {

                regions.add(
                    start to
                            index - 1
                )

                start =
                    -1
            }
        }

        if (
            start >= 0
        ) {

            regions.add(
                start to
                        density.lastIndex
            )
        }

        /*
         * Merge tiny gaps.
         *
         * Borders/shadows can produce 1-2 empty
         * sampling columns inside one block.
         */
        return mergeSmallGaps(
            regions
        )
    }

    private fun mergeSmallGaps(
        regions: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (
            regions.size <= 1
        ) {
            return regions
        }

        val result =
            mutableListOf<Pair<Int, Int>>()

        var current =
            regions.first()

        for (
            index in
            1 until regions.size
        ) {

            val next =
                regions[index]

            val gap =
                next.first -
                        current.second -
                        1

            if (
                gap <= 1
            ) {

                current =
                    current.first to
                            next.second

            } else {

                result.add(
                    current
                )

                current =
                    next
            }
        }

        result.add(
            current
        )

        return result
    }

    // ============================================================
    // FALLBACK
    // ============================================================

    private fun fallbackCellDetection(
        points: List<Pair<Int, Int>>,
        tray: Tray
    ): List<DetectedCell> {

        if (
            points.isEmpty()
        ) {
            return emptyList()
        }

        /*
         * Estimate a typical block size from
         * the spatial spread.
         */
        val minX =
            points.minOf {
                it.first
            }

        val maxX =
            points.maxOf {
                it.first
            }

        val minY =
            points.minOf {
                it.second
            }

        val maxY =
            points.maxOf {
                it.second
            }

        val spanX =
            maxX -
                    minX

        val spanY =
            maxY -
                    minY

        val estimatedSize =
            max(
                16f,
                min(
                    70f,
                    (
                        min(
                            spanX,
                            max(
                                spanY,
                                1
                            )
                        ) /
                            6f
                        )
                    )
            )

        val candidates =
            mutableListOf<DetectedCell>()

        /*
         * Cluster colored pixels into rough blocks.
         */
        val used =
            BooleanArray(
                points.size
            )

        for (
            i in
            points.indices
        ) {

            if (
                used[i]
            ) {
                continue
            }

            val seed =
                points[i]

            var sumX =
                0f

            var sumY =
                0f

            var count =
                0

            for (
                j in
                points.indices
            ) {

                if (
                    used[j]
                ) {
                    continue
                }

                val point =
                    points[j]

                if (
                    abs(
                        point.first -
                                seed.first
                    ) <=
                    estimatedSize * 0.65f &&
                    abs(
                        point.second -
                                seed.second
                    ) <=
                    estimatedSize * 0.65f
                ) {

                    used[j] =
                        true

                    sumX +=
                        point.first

                    sumY +=
                        point.second

                    count++
                }
            }

            if (
                count < 2
            ) {
                continue
            }

            candidates.add(
                DetectedCell(
                    centerX =
                        sumX /
                                count,

                    centerY =
                        sumY /
                                count,

                    size =
                        estimatedSize
                )
            )

            if (
                candidates.size >=
                MAX_CELLS
            ) {
                break
            }
        }

        return removeDuplicateCells(
            candidates
        )
    }

    // ============================================================
    // REMOVE DUPLICATES
    // ============================================================

    private fun removeDuplicateCells(
        cells: List<DetectedCell>
    ): List<DetectedCell> {

        if (
            cells.isEmpty()
        ) {
            return emptyList()
        }

        val sorted =
            cells.sortedBy {
                it.centerX
            }

        val result =
            mutableListOf<DetectedCell>()

        for (
            cell in
            sorted
        ) {

            var duplicate =
                false

            for (
                existing in
                result
            ) {

                val distanceX =
                    abs(
                        cell.centerX -
                                existing.centerX
                    )

                val distanceY =
                    abs(
                        cell.centerY -
                                existing.centerY
                    )

                val tolerance =
                    min(
                        cell.size,
                        existing.size
                    ) * 0.45f

                if (
                    distanceX < tolerance &&
                    distanceY < tolerance
                ) {

                    duplicate =
                        true

                    break
                }
            }

            if (!duplicate) {

                result.add(
                    cell
                )
            }

            if (
                result.size >=
                MAX_CELLS
            ) {
                break
            }
        }

        return result
    }

    // ============================================================
    // GROUP CELLS INTO PIECES
    // ============================================================

    private fun groupCells(
        cells: List<DetectedCell>
    ): List<List<DetectedCell>> {

        if (
            cells.isEmpty()
        ) {
            return emptyList()
        }

        if (
            cells.size == 1
        ) {
            return listOf(
                cells
            )
        }

        /*
         * Estimate typical cell size.
         */
        val typicalSize =
            median(
                cells.map {
                    it.size
                }
            )
                .coerceAtLeast(
                    10f
                )

        val unused =
            cells.toMutableList()

        val groups =
            mutableListOf<
                    MutableList<DetectedCell>
                    >()

        /*
         * Connected-component grouping.
         *
         * Two cells belong to the same piece when they
         * are horizontally or vertically adjacent.
         */
        while (
            unused.isNotEmpty()
        ) {

            val seed =
                unused.removeAt(0)

            val group =
                mutableListOf<DetectedCell>()

            group.add(
                seed
            )

            var changed =
                true

            while (changed) {

                changed =
                    false

                val toAdd =
                    mutableListOf<DetectedCell>()

                for (
                    candidate in
                    unused
                ) {

                    var connected =
                        false

                    for (
                        existing in
                        group
                    ) {

                        val dx =
                            abs(
                                candidate.centerX -
                                        existing.centerX
                            )

                        val dy =
                            abs(
                                candidate.centerY -
                                        existing.centerY
                            )

                        /*
                         * Horizontal neighbour.
                         */
                        val horizontal =
                            dx <=
                                    typicalSize * 1.75f &&
                                    dy <=
                                    typicalSize * 0.55f

                        /*
                         * Vertical neighbour.
                         */
                        val vertical =
                            dy <=
                                    typicalSize * 1.75f &&
                                    dx <=
                                    typicalSize * 0.55f

                        if (
                            horizontal ||
                            vertical
                        ) {

                            connected =
                                true

                            break
                        }
                    }

                    if (
                        connected
                    ) {

                        toAdd.add(
                            candidate
                        )
                    }
                }

                if (
                    toAdd.isNotEmpty()
                ) {

                    unused.removeAll(
                        toAdd.toSet()
                    )

                    group.addAll(
                        toAdd
                    )

                    changed =
                        true
                }
            }

            groups.add(
                group
            )
        }

        /*
         * Sort pieces from left to right.
         */
        return groups
            .sortedBy {
                it.minOf {
                    cell ->
                    cell.centerX
                }
            }
            .take(
                MAX_PIECES
            )
    }

    // ============================================================
    // NORMALIZE SHAPE
    // ============================================================

    private fun normalizeGroup(
        group: List<DetectedCell>
    ): List<UniversalCell> {

        if (
            group.isEmpty()
        ) {
            return emptyList()
        }

        /*
         * Determine grid spacing.
         */
        val size =
            median(
                group.map {
                    it.size
                }
            )
                .coerceAtLeast(
                    10f
                )

        val minX =
            group.minOf {
                it.centerX
            }

        val minY =
            group.minOf {
                it.centerY
            }

        val raw =
            mutableListOf<UniversalCell>()

        for (
            cell in
            group
        ) {

            val column =
                (
                    (
                        cell.centerX -
                                minX
                        ) /
                            size
                    )
                    .toInt()
                    .coerceAtLeast(
                        0
                    )

            val row =
                (
                    (
                        cell.centerY -
                                minY
                        ) /
                            size
                    )
                    .toInt()
                    .coerceAtLeast(
                        0
                    )

            raw.add(
                UniversalCell(
                    row = row,
                    column = column
                )
            )
        }

        val distinct =
            raw.distinct()

        if (
            distinct.isEmpty()
        ) {
            return emptyList()
        }

        val minimumRow =
            distinct.minOf {
                it.row
            }

        val minimumColumn =
            distinct.minOf {
                it.column
            }

        return distinct
            .map {

                UniversalCell(
                    row =
                        it.row -
                                minimumRow,

                    column =
                        it.column -
                                minimumColumn
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

    // ============================================================
    // COLOR DETECTION
    // ============================================================

    private fun isStrongPiecePixel(
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
         * Strong colored pixels.
         */
        if (
            saturation >= 55 &&
            brightness >= 45
        ) {
            return true
        }

        /*
         * Bright yellow/orange/white-ish block
         * highlights can have lower saturation.
         */
        if (
            brightness >= 145 &&
            saturation >= 30
        ) {
            return true
        }

        return false
    }

    // ============================================================
    // MEDIAN
    // ============================================================

    private fun median(
        values: List<Float>
    ): Float {

        if (
            values.isEmpty()
        ) {
            return 0f
        }

        val sorted =
            values.sorted()

        val middle =
            sorted.size / 2

        return if (
            sorted.size % 2 == 0
        ) {

            (
                sorted[middle - 1] +
                        sorted[middle]
                ) / 2f

        } else {

            sorted[middle]
        }
    }
}
