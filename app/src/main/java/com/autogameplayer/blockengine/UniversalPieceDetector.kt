package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
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

        val tray =
            findTray(
                bitmap,
                grid
            )

        if (tray == null) {
            return emptyList()
        }

        val components =
            detectColoredComponents(
                bitmap,
                tray
            )

        if (components.isEmpty()) {
            return emptyList()
        }

        val mergedBlocks =
            mergeBlockComponents(
                components
            )

        if (mergedBlocks.isEmpty()) {
            return emptyList()
        }

        val groups =
            groupBlocksIntoPieces(
                mergedBlocks
            )

        if (groups.isEmpty()) {
            return emptyList()
        }

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

    private data class Tray(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

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

        val top =
            (
                grid.bottom +
                        maxInt(
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

    // ============================================================
    // FLOOD FILL
    // ============================================================

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

        val capacity =
            width * height

        val queueX =
            IntArray(
                capacity
            )

        val queueY =
            IntArray(
                capacity
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
                minInt(
                    minX,
                    x
                )

            maxX =
                maxInt(
                    maxX,
                    x
                )

            minY =
                minInt(
                    minY,
                    y
                )

            maxY =
                maxInt(
                    maxY,
                    y
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

    // ============================================================
    // PIXEL CLASSIFICATION
    // ============================================================

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
            maxInt(
                red,
                maxInt(
                    green,
                    blue
                )
            )

        val minimum =
            minInt(
                red,
                minInt(
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
    // MERGE VISUAL COMPONENTS
    // ============================================================

    private fun mergeBlockComponents(
        components: List<VisualComponent>
    ): List<BlockCell> {

        if (
            components.isEmpty()
        ) {
            return emptyList()
        }

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

        val result =
            mutableListOf<BlockCell>()

        for (
            component in
            components
        ) {

            if (
                component.width <
                blockWidth * 0.30f ||
                component.height <
                blockHeight * 0.30f
            ) {
                continue
            }

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
                                existing.width
                                    .coerceAtLeast(
                                        component.width.toFloat()
                                    ),

                            height =
                                existing.height
                                    .coerceAtLeast(
                                        component.height.toFloat()
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

        val sorted =
            blocks.sortedWith(
                compareBy<BlockCell> {
                    it.centerY
                }.thenBy {
                    it.centerX
                }
            )

        /*
         * Build connected groups based on
         * approximately one-cell spacing.
         */
        val groups =
            mutableListOf<
                    MutableList<BlockCell>
                    >()

        val unused =
            sorted.toMutableList()

        while (
            unused.isNotEmpty()
        ) {

            val seed =
                unused.removeAt(0)

            val group =
                mutableListOf<BlockCell>()

            group.add(
                seed
            )

            var changed =
                true

            while (changed) {

                changed =
                    false

                val iterator =
                    unused.iterator()

                val toRemove =
                    mutableListOf<BlockCell>()

                while (
                    iterator.hasNext()
                ) {

                    val candidate =
                        iterator.next()

                    var near =
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

                        val horizontalNeighbour =
                            dx <=
                                    cellWidth * 1.65f &&
                                    dy <=
                                    cellHeight * 0.65f

                        val verticalNeighbour =
                            dy <=
                                    cellHeight * 1.65f &&
                                    dx <=
                                    cellWidth * 0.65f

                        if (
                            horizontalNeighbour ||
                            verticalNeighbour
                        ) {

                            near =
                                true

                            break
                        }
                    }

                    if (near) {

                        toRemove.add(
                            candidate
                        )

                        changed =
                            true
                    }
                }

                if (
                    toRemove.isNotEmpty()
                ) {

                    unused.removeAll(
                        toRemove.toSet()
                    )

                    group.addAll(
                        toRemove
                    )
                }
            }

            groups.add(
                group
            )
        }

        return mergeNearbyGroups(
            groups,
            cellWidth,
            cellHeight
        )
    }

    // ============================================================
    // MERGE NEARBY GROUPS
    // ============================================================

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

    // ============================================================
    // GROUP DISTANCE
    // ============================================================

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

                /*
                 * Manhattan distance.
                 */
                val distance =
                    dx + dy

                minimumDistance =
                    min(
                        minimumDistance,
                        distance
                    )
            }
        }

        return minimumDistance <=
                (
                    cellWidth +
                            cellHeight
                    ) * 1.15f
    }

    // ============================================================
    // CONVERT TO UNIVERSAL SHAPE
    // ============================================================

    private fun convertGroupToShape(
        group: List<BlockCell>
    ): List<UniversalCell> {

        if (
            group.isEmpty()
        ) {
            return emptyList()
        }

        /*
         * Prevent accidental detection of a huge
         * tray/background region.
         */
        if (
            group.size > 25
        ) {
            return emptyList()
        }

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

        /*
         * Use the upper-left block as the
         * approximate origin.
         */
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

        val distinct =
            rawCells
                .distinct()

        return normalizeShape(
            distinct
        )
    }

    // ============================================================
    // NORMALIZE SHAPE
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
    // SAFE INTEGER HELPERS
    // ============================================================

    private fun maxInt(
        a: Int,
        b: Int
    ): Int {

        return if (
            a > b
        ) {
            a
        } else {
            b
        }
    }

    private fun minInt(
        a: Int,
        b: Int
    ): Int {

        return if (
            a < b
        ) {
            a
        } else {
            b
        }
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
