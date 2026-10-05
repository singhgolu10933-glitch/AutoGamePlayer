package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal block-piece detector.
 *
 * This detector does NOT assume:
 * - Block Blitz
 * - fixed piece coordinates
 * - fixed piece sizes
 * - fixed colors
 *
 * It detects colored cells in the lower tray,
 * groups nearby cells into individual pieces,
 * and normalizes their shapes.
 */
object UniversalPieceDetector {

    private const val MAX_PIECES = 5

    private const val MAX_COMPONENTS = 80

    /**
     * Main entry point.
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

        /*
         * Find the lower tray.
         */
        val tray =
            findTray(
                bitmap,
                grid
            )

        if (tray == null) {
            return emptyList()
        }

        /*
         * Detect individual colored block regions.
         */
        val components =
            detectColoredComponents(
                bitmap,
                tray
            )

        if (components.isEmpty()) {
            return emptyList()
        }

        /*
         * Merge visual fragments belonging to
         * the same physical block.
         */
        val mergedBlocks =
            mergeBlockComponents(
                components
            )

        if (mergedBlocks.isEmpty()) {
            return emptyList()
        }

        /*
         * Group blocks into individual pieces.
         */
        val groups =
            groupBlocksIntoPieces(
                mergedBlocks
            )

        if (groups.isEmpty()) {
            return emptyList()
        }

        /*
         * Convert every group into a normalized
         * UniversalBlockPiece.
         */
        val pieces =
            mutableListOf<UniversalBlockPiece>()

        for (
            groupIndex in
            groups.indices
        ) {

            val group =
                groups[groupIndex]

            val shape =
                convertGroupToShape(
                    group
                )

            if (shape.isEmpty()) {
                continue
            }

            pieces.add(
                UniversalBlockPiece(
                    id = groupIndex,
                    cells = shape
                )
            )
        }

        return pieces
            .take(MAX_PIECES)
    }

    // ============================================================
    // DATA CLASSES
    // ============================================================

    /**
     * Lower tray region.
     */
    private data class Tray(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    /**
     * A visual connected component.
     */
    private data class VisualComponent(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val centerX: Float,
        val centerY: Float,
        val area: Int
    ) {

        val width: Int
            get() = right - left

        val height: Int
            get() = bottom - top
    }

    /**
     * A merged physical block.
     */
    private data class BlockCell(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float
    )

    // ============================================================
    // TRAY DETECTION
    // ============================================================

    private fun findTray(
        bitmap: Bitmap,
        grid: UniversalGridDetector.DetectedGrid
    ): Tray? {

        val width =
            bitmap.width

        val height =
            bitmap.height

        /*
         * Pieces normally appear below the board.
         *
         * Start just below the detected board.
         */
        val top =
            (
                grid.bottom +
                        max(
                            20,
                            (
                                grid.cellHeight *
                                        0.15f
                                ).toInt()
                        )
                )
                .coerceAtLeast(
                    (
                        height *
                                0.60f
                        ).toInt()
                )
                .coerceAtMost(
                    height - 20
                )

        /*
         * Keep enough room for the complete tray.
         */
        val bottom =
            (
                height *
                        0.97f
                )
                .toInt()
                .coerceAtMost(
                    height - 1
                )

        if (
            bottom <= top
        ) {
            return null
        }

        /*
         * Almost full screen width.
         */
        val left =
            (
                width *
                        0.04f
                )
                .toInt()

        val right =
            (
                width *
                        0.96f
                )
                .toInt()

        if (
            right <= left
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
    // COLOR COMPONENT DETECTION
    // ============================================================

    private fun detectColoredComponents(
        bitmap: Bitmap,
        tray: Tray
    ): List<VisualComponent> {

        /*
         * Downsample the scan.
         *
         * This is considerably cheaper than checking
         * every pixel while still being accurate enough
         * for block-sized objects.
         */
        val step =
            2

        val sampleWidth =
            (
                tray.right -
                        tray.left
                ) / step

        val sampleHeight =
            (
                tray.bottom -
                        tray.top
                ) / step

        if (
            sampleWidth <= 0 ||
            sampleHeight <= 0
        ) {
            return emptyList()
        }

        val visited =
            Array(sampleHeight) {
                BooleanArray(
                    sampleWidth
                )
            }

        val components =
            mutableListOf<VisualComponent>()

        for (
            sy in
            0 until sampleHeight
        ) {

            for (
                sx in
                0 until sampleWidth
            ) {

                if (
                    visited[sy][sx]
                ) {
                    continue
                }

                val pixelX =
                    tray.left +
                            sx * step

                val pixelY =
                    tray.top +
                            sy * step

                if (
                    pixelX < 0 ||
                    pixelX >= bitmap.width ||
                    pixelY < 0 ||
                    pixelY >= bitmap.height
                ) {
                    visited[sy][sx] = true
                    continue
                }

                val pixel =
                    bitmap.getPixel(
                        pixelX,
                        pixelY
                    )

                if (
                    !looksLikePiecePixel(
                        pixel
                    )
                ) {
                    visited[sy][sx] = true
                    continue
                }

                /*
                 * Flood-fill this colored region.
                 */
                val component =
                    floodFill(
                        bitmap = bitmap,
                        tray = tray,
                        startX = sx,
                        startY = sy,
                        step = step,
                        visited = visited
                    )

                if (
                    component != null &&
                    component.area >= 8
                ) {

                    components.add(
                        component
                    )
                }

                if (
                    components.size >=
                    MAX_COMPONENTS
                ) {
                    return components
                }
            }
        }

        return components
    }

    /**
     * Flood-fill colored pixels.
     */
    private fun floodFill(
        bitmap: Bitmap,
        tray: Tray,
        startX: Int,
        startY: Int,
        step: Int,
        visited: Array<BooleanArray>
    ): VisualComponent? {

        val height =
            visited.size

        val width =
            if (
                height > 0
            ) {
                visited[0].size
            } else {
                0
            }

        if (
            startX !in 0 until width ||
            startY !in 0 until height
        ) {
            return null
        }

        val queueX =
            IntArray(
                width * height
            )

        val queueY =
            IntArray(
                width * height
            )

        var head =
            0

        var tail =
            0

        queueX[tail] =
            startX

        queueY[tail] =
            startY

        tail++

        visited[startY][startX] =
            true

        var minX =
            startX

        var maxX =
            startX

        var minY =
            startY

        var maxY =
            startY

        var sumX =
            0L

        var sumY =
            0L

        var count =
            0

        while (
            head < tail
        ) {

            val x =
                queueX[head]

            val y =
                queueY[head]

            head++

            sumX += x
            sumY += y
            count++

            minX =
                min(
                    minX,
                    x
                )

            maxX =
                max(
                    maxX,
                    x
                )

            minY =
                min(
                    minY,
                    y
                )

            maxY =
                max(
                    maxY,
                    y
                )

            /*
             * 4-direction connectivity.
             */
            val dx =
                intArrayOf(
                    1,
                    -1,
                    0,
                    0
                )

            val dy =
                intArrayOf(
                    0,
                    0,
                    1,
                    -1
                )

            for (
                direction in
                0..3
            ) {

                val nx =
                    x +
                            dx[direction]

                val ny =
                    y +
                            dy[direction]

                if (
                    nx !in 0 until width ||
                    ny !in 0 until height
                ) {
                    continue
                }

                if (
                    visited[ny][nx]
                ) {
                    continue
                }

                val px =
                    tray.left +
                            nx * step

                val py =
                    tray.top +
                            ny * step

                if (
                    px < 0 ||
                    px >= bitmap.width ||
                    py < 0 ||
                    py >= bitmap.height
                ) {
                    visited[ny][nx] = true
                    continue
                }

                val pixel =
                    bitmap.getPixel(
                        px,
                        py
                    )

                if (
                    looksLikePiecePixel(
                        pixel
                    )
                ) {

                    visited[ny][nx] =
                        true

                    if (
                        tail <
                        queueX.size
                    ) {

                        queueX[tail] =
                            nx

                        queueY[tail] =
                            ny

                        tail++
                    }

                } else {

                    visited[ny][nx] =
                        true
                }
            }
        }

        if (
            count <= 0
        ) {
            return null
        }

        val actualLeft =
            tray.left +
                    minX * step

        val actualTop =
            tray.top +
                    minY * step

        val actualRight =
            tray.left +
                    (
                        maxX + 1
                    ) * step

        val actualBottom =
            tray.top +
                    (
                        maxY + 1
                    ) * step

        /*
         * Ignore extremely large regions.
         * They are usually tray/background detection.
         */
        val componentWidth =
            actualRight -
                    actualLeft

        val componentHeight =
            actualBottom -
                    actualTop

        if (
            componentWidth > 250 ||
            componentHeight > 250
        ) {
            return null
        }

        return VisualComponent(
            left = actualLeft,
            top = actualTop,
            right = actualRight,
            bottom = actualBottom,
            centerX =
                tray.left +
                        (
                            sumX.toFloat() /
                                    count.toFloat()
                            ) *
                        step,
            centerY =
                tray.top +
                        (
                            sumY.toFloat() /
                                    count.toFloat()
                            ) *
                        step,
            area = count
        )
    }

    /**
     * Determines whether a pixel looks like
     * a colored block rather than the dark tray.
     */
    private fun looksLikePiecePixel(
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
            red * 0.299f +
                    green * 0.587f +
                    blue * 0.114f

        val saturation =
            maximum -
                    minimum

        /*
         * The tray in the supplied screenshot is
         * dark blue, while the pieces are strongly
         * colored.
         */
        return (
            saturation >= 40 &&
                    brightness >= 40
            ) ||
                (
                    saturation >= 25 &&
                            brightness >= 100
                    )
    }

    // ============================================================
    // MERGE VISUAL FRAGMENTS
    // ============================================================

    private fun mergeBlockComponents(
        components: List<VisualComponent>
    ): List<BlockCell> {

        if (
            components.isEmpty()
        ) {
            return emptyList()
        }

        /*
         * Determine typical visual block size.
         */
        val widths =
            components
                .map {
                    it.width.toFloat()
                }
                .filter {
                    it >= 8f
                }

        val heights =
            components
                .map {
                    it.height.toFloat()
                }
                .filter {
                    it >= 8f
                }

        if (
            widths.isEmpty() ||
            heights.isEmpty()
        ) {
            return emptyList()
        }

        val estimatedWidth =
            median(
                widths
            )

        val estimatedHeight =
            median(
                heights
            )

        val blockWidth =
            estimatedWidth
                .coerceAtLeast(
                    8f
                )

        val blockHeight =
            estimatedHeight
                .coerceAtLeast(
                    8f
                )

        /*
         * Components that belong to the same
         * physical cell are merged if their centers
         * are very close.
         */
        val result =
            mutableListOf<BlockCell>()

        for (
            component in
            components
        ) {

            /*
             * Ignore tiny noise.
             */
            if (
                component.width <
                blockWidth * 0.30f ||
                component.height <
                blockHeight * 0.30f
            ) {
                continue
            }

            /*
             * If a previous cell is extremely close,
             * merge it.
             */
            var merged =
                false

            for (
                index in
                result.indices
            ) {

                val existing =
                    result[index]

                val distanceX =
                    abs(
                        existing.centerX -
                                component.centerX
                    )

                val distanceY =
                    abs(
                        existing.centerY -
                                component.centerY
                    )

                if (
                    distanceX <
                    blockWidth * 0.45f &&
                    distanceY <
                    blockHeight * 0.45f
                ) {

                    result[index] =
                        BlockCell(
                            centerX =
                                (
                                    existing.centerX +
                                            component.centerX
                                    ) / 2f,

                            centerY =
                                (
                                    existing.centerY +
                                            component.centerY
                                    ) / 2f,

                            width =
                                max(
                                    existing.width,
                                    component.width
                                ),

                            height =
                                max(
                                    existing.height,
                                    component.height
                                )
                        )

                    merged = true

                    break
                }
            }

            if (!merged) {

                result.add(
                    BlockCell(
                        centerX =
                            component.centerX,

                        centerY =
                            component.centerY,

                        width =
                            component.width.toFloat(),

                        height =
                            component.height.toFloat()
                    )
                )
            }
        }

        return result
    }

    // ============================================================
    // GROUP BLOCKS INTO PIECES
    // ============================================================

    private fun groupBlocksIntoPieces(
        blocks: List<BlockCell>
    ): List<List<BlockCell>> {

        if (
            blocks.isEmpty()
        ) {
            return emptyList()
        }

        if (
            blocks.size == 1
        ) {
            return listOf(
                blocks
            )
        }

        /*
         * Estimate spacing between cells.
         */
        val cellWidth =
            median(
                blocks.map {
                    it.width
                }
            )
                .coerceAtLeast(
                    8f
                )

        val cellHeight =
            median(
                blocks.map {
                    it.height
                }
            )
                .coerceAtLeast(
                    8f
                )

        /*
         * Sort horizontally first.
         */
        val sorted =
            blocks.sortedBy {
                it.centerX
            }

        val groups =
            mutableListOf<
                    MutableList<BlockCell>
                    >()

        var current =
            mutableListOf<BlockCell>()

        for (
            block in
            sorted
        ) {

            if (
                current.isEmpty()
            ) {

                current.add(
                    block
                )

                continue
            }

            val previous =
                current.last()

            val gap =
                block.centerX -
                        previous.centerX

            /*
             * A normal neighboring cell should be
             * approximately one cell-width away.
             *
             * A large gap normally means the next
             * piece has started.
             */
            val samePiece =
                gap <=
                        cellWidth * 2.2f

            if (
                samePiece
            ) {

                current.add(
                    block
                )

            } else {

                groups.add(
                    current
                )

                current =
                    mutableListOf()

                current.add(
                    block
                )
            }
        }

        if (
            current.isNotEmpty()
        ) {

            groups.add(
                current
            )
        }

        /*
         * Horizontal grouping alone can sometimes
         * split a vertically arranged piece.
         *
         * Merge groups when their horizontal
         * distance is small enough.
         */
        return mergeNearbyGroups(
            groups,
            cellWidth,
            cellHeight
        )
    }

    /**
     * Merges groups that clearly belong to the
     * same physical piece.
     */
    private fun mergeNearbyGroups(
        groups: List<List<BlockCell>>,
        cellWidth: Float,
        cellHeight: Float
    ): List<List<BlockCell>> {

        if (
            groups.size <= 1
        ) {
            return groups
        }

        val mutable =
            groups
                .map {
                    it.toMutableList()
                }
                .toMutableList()

        var changed =
            true

        while (
            changed &&
            mutable.size > 1
        ) {

            changed =
                false

            outer@ for (
                firstIndex in
                0 until mutable.size
            ) {

                for (
                    secondIndex in
                    firstIndex + 1 until
                            mutable.size
                ) {

                    val first =
                        mutable[firstIndex]

                    val second =
                        mutable[secondIndex]

                    if (
                        groupsBelongTogether(
                            first,
                            second,
                            cellWidth,
                            cellHeight
                        )
                    ) {

                        val merged =
                            mutableListOf<BlockCell>()

                        merged.addAll(
                            first
                        )

                        merged.addAll(
                            second
                        )

                        mutable[firstIndex] =
                            merged

                        mutable.removeAt(
                            secondIndex
                        )

                        changed =
                            true

                        break@outer
                    }
                }
            }
        }

        return mutable
    }

    /**
     * Determines whether two groups are close
     * enough to be part of the same piece.
     */
    private fun groupsBelongTogether(
        first: List<BlockCell>,
        second: List<BlockCell>,
        cellWidth: Float,
        cellHeight: Float
    ): Boolean {

        if (
            first.isEmpty() ||
            second.isEmpty()
        ) {
            return false
        }

        var minimumDistance =
            Float.MAX_VALUE

        for (
            firstBlock in
            first
        ) {

            for (
                secondBlock in
                second
            ) {

                val dx =
                    abs(
                        firstBlock.centerX -
                                secondBlock.centerX
                    )

                val dy =
                    abs(
                        firstBlock.centerY -
                                secondBlock.centerY
                    )

                val distance =
                    dx + dy

                minimumDistance =
                    min(
                        minimumDistance,
                        distance
                    )
            }
        }

        /*
         * Adjacent cells can be separated
         * horizontally or vertically.
         */
        return minimumDistance <=
                (
                    cellWidth +
                            cellHeight
                    ) * 1.15f
    }

    // ============================================================
    // CONVERT GROUP TO SHAPE
    // ============================================================

    private fun convertGroupToShape(
        group: List<BlockCell>
    ): List<UniversalCell> {

        if (
            group.isEmpty()
        ) {
            return emptyList()
        }

        if (
            group.size > 25
        ) {
            return emptyList()
        }

        /*
         * Estimate tray-cell spacing.
         */
        val cellWidth =
            median(
                group.map {
                    it.width
                }
            )
                .coerceAtLeast(
                    5f
                )

        val cellHeight =
            median(
                group.map {
                    it.height
                }
            )
                .coerceAtLeast(
                    5f
                )

        val originX =
            group.minOf {
                it.centerX
            }

        val originY =
            group.minOf {
                it.centerY
            }

        val rawCells =
            mutableListOf<UniversalCell>()

        for (
            block in
            group
        ) {

            val column =
                (
                    (
                        block.centerX -
                                originX
                        ) /
                            cellWidth
                    )
                    .toInt()
                    .coerceAtLeast(
                        0
                    )

            val row =
                (
                    (
                        block.centerY -
                                originY
                        ) /
                            cellHeight
                    )
                    .toInt()
                    .coerceAtLeast(
                        0
                    )

            rawCells.add(
                UniversalCell(
                    row = row,
                    column = column
                )
            )
        }

        /*
         * Remove duplicates.
         */
        val distinct =
            rawCells
                .distinct()

        /*
         * Normalize again.
         */
        return normalizeShape(
            distinct
        )
    }

    // ============================================================
    // NORMALIZATION
    // ============================================================

    private fun normalizeShape(
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
