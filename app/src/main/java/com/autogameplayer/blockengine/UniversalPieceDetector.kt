package com.autogameplayer.blockengine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal fast piece detector.
 *
 * Detects block-puzzle pieces from the area below the detected board.
 *
 * Strategy:
 *
 * Screenshot
 *     ↓
 * Board geometry
 *     ↓
 * Estimate tray region
 *     ↓
 * Detect colored block cells
 *     ↓
 * Group cells into piece slots
 *     ↓
 * Normalize shape
 *
 * Designed to work with different block-puzzle games
 * without hardcoded Block Blitz coordinates.
 */
object UniversalPieceDetector {

    // ============================================================
    // TUNING
    // ============================================================

    /*
     * Most block games currently provide 3 pieces.
     *
     * The detector can still return fewer/more when the UI
     * is different.
     */
    private const val DEFAULT_SLOT_COUNT = 3

    /*
     * Maximum number of cells allowed in one piece.
     */
    private const val MAX_PIECE_CELLS = 25

    /*
     * Minimum block-like area.
     */
    private const val MIN_COMPONENT_AREA = 20

    /*
     * Maximum connected-component search pixels.
     *
     * This keeps analysis fast.
     */
    private const val MAX_SCAN_PIXELS = 900_000

    // ============================================================
    // PUBLIC ENTRY
    // ============================================================

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
            grid.rows <= 0 ||
            grid.columns <= 0
        ) {
            return emptyList()
        }

        // ========================================================
        // 1. FIND TRAY
        // ========================================================

        val trayTop =
            calculateTrayTop(
                bitmap = bitmap,
                grid = grid
            )

        val trayBottom =
            calculateTrayBottom(
                bitmap = bitmap,
                trayTop = trayTop
            )

        if (
            trayBottom <= trayTop
        ) {
            return emptyList()
        }

        // ========================================================
        // 2. ESTIMATE PIECE CELL SIZE
        // ========================================================

        val estimatedCellSize =
            estimatePieceCellSize(
                grid = grid
            )

        // ========================================================
        // 3. DETECT COLORED COMPONENTS
        // ========================================================

        val components =
            detectComponents(
                bitmap = bitmap,
                trayTop = trayTop,
                trayBottom = trayBottom,
                estimatedCellSize = estimatedCellSize
            )

        if (
            components.isEmpty()
        ) {
            return emptyList()
        }

        // ========================================================
        // 4. GROUP COMPONENTS INTO PIECE SLOTS
        // ========================================================

        val groups =
            groupIntoSlots(
                components = components,
                bitmapWidth = bitmap.width,
                slotCount = DEFAULT_SLOT_COUNT
            )

        // ========================================================
        // 5. CONVERT EACH GROUP INTO A BLOCK SHAPE
        // ========================================================

        val result =
            ArrayList<UniversalBlockPiece>(
                DEFAULT_SLOT_COUNT
            )

        for (
            index in
            0 until DEFAULT_SLOT_COUNT
        ) {

            val group =
                groups.getOrNull(
                    index
                )
                    ?: continue

            if (
                group.isEmpty()
            ) {
                continue
            }

            val cells =
                convertToShape(
                    components = group,
                    estimatedCellSize = estimatedCellSize
                )

            if (
                cells.isEmpty()
            ) {
                continue
            }

            val normalized =
                normalizeCells(
                    cells
                )

            if (
                normalized.isEmpty()
            ) {
                continue
            }

            if (
                normalized.size >
                MAX_PIECE_CELLS
            ) {
                continue
            }

            result.add(
                UniversalBlockPiece(
                    id = index,
                    cells = normalized
                )
            )
        }

        /*
         * Keep piece IDs stable.
         */
        return result.sortedBy {
            it.id
        }
    }

    // ============================================================
    // TRAY TOP
    // ============================================================

    private fun calculateTrayTop(
        bitmap: Bitmap,
        grid: DetectedGrid
    ): Int {

        /*
         * The tray normally starts shortly below the board.
         *
         * We do NOT use a fixed pixel coordinate.
         */
        val boardBottom =
            (
                grid.bottom *
                        bitmap.height
                )
                .toInt()

        val gap =
            max(
                12,
                (
                    grid.cellHeight *
                            0.55f
                    )
                    .toInt()
            )

        return (
            boardBottom +
                    gap
            )
            .coerceAtLeast(
                0
            )
            .coerceAtMost(
                bitmap.height - 1
            )
    }

    // ============================================================
    // TRAY BOTTOM
    // ============================================================

    private fun calculateTrayBottom(
        bitmap: Bitmap,
        trayTop: Int
    ): Int {

        /*
         * Ignore very bottom UI/buttons/ads.
         *
         * The actual tray is normally contained in the
         * lower-middle portion of the screen.
         */
        val screenHeight =
            bitmap.height

        val maximumBottom =
            (
                screenHeight *
                        0.94f
                )
                .toInt()

        return max(
            trayTop + 1,
            maximumBottom
        )
            .coerceAtMost(
                screenHeight
            )
    }

    // ============================================================
    // PIECE CELL SIZE
    // ============================================================

    private fun estimatePieceCellSize(
        grid: DetectedGrid
    ): Float {

        /*
         * Tray blocks are usually smaller than board cells.
         *
         * 0.30 - 0.55 of board cell size covers many games.
         */
        val base =
            min(
                grid.cellWidth,
                grid.cellHeight
            )

        return (
            base *
                    0.42f
            )
            .coerceAtLeast(
                8f
            )
    }

    // ============================================================
    // COMPONENT DATA
    // ============================================================

    private data class Component(
        val centerX: Float,
        val centerY: Float,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val area: Int
    ) {

        val width: Int
            get() = right - left + 1

        val height: Int
            get() = bottom - top + 1
    }

    // ============================================================
    // COLORED COMPONENT DETECTION
    // ============================================================

    private fun detectComponents(
        bitmap: Bitmap,
        trayTop: Int,
        trayBottom: Int,
        estimatedCellSize: Float
    ): List<Component> {

        val width =
            bitmap.width

        val height =
            bitmap.height

        if (
            width <= 0 ||
            height <= trayTop
        ) {
            return emptyList()
        }

        /*
         * Avoid scanning an enormous bitmap pixel-by-pixel.
         *
         * At normal phone resolutions this is still fast.
         */
        val scanWidth =
            width

        val scanHeight =
            trayBottom -
                    trayTop

        if (
            scanWidth *
                    scanHeight >
            MAX_SCAN_PIXELS
        ) {
            return detectComponentsDownsampled(
                bitmap = bitmap,
                trayTop = trayTop,
                trayBottom = trayBottom,
                estimatedCellSize = estimatedCellSize
            )
        }

        val visited =
            BooleanArray(
                scanWidth *
                        scanHeight
            )

        val components =
            ArrayList<Component>()

        /*
         * Step size 2 is substantially faster while still
         * preserving block-shaped cells.
         */
        val step =
            if (
                estimatedCellSize >= 20f
            ) {
                2
            } else {
                1
            }

        for (
            y in trayTop until trayBottom step step
        ) {

            for (
                x in 0 until scanWidth step step
            ) {

                val localX =
                    x

                val localY =
                    y -
                            trayTop

                val index =
                    localY *
                            scanWidth +
                            localX

                if (
                    index < 0 ||
                    index >= visited.size ||
                    visited[index]
                ) {
                    continue
                }

                visited[index] = true

                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

                if (
                    !isPiecePixel(
                        pixel
                    )
                ) {
                    continue
                }

                val component =
                    floodFill(
                        bitmap = bitmap,
                        startX = x,
                        startY = y,
                        trayTop = trayTop,
                        trayBottom = trayBottom,
                        visited = visited,
                        width = scanWidth,
                        estimatedCellSize = estimatedCellSize,
                        step = step
                    )

                if (
                    component != null
                ) {
                    components.add(
                        component
                    )
                }
            }
        }

        return filterComponents(
            components = components,
            estimatedCellSize = estimatedCellSize
        )
    }

    // ============================================================
    // DOWNSAMPLED COMPONENT DETECTION
    // ============================================================

    private fun detectComponentsDownsampled(
        bitmap: Bitmap,
        trayTop: Int,
        trayBottom: Int,
        estimatedCellSize: Float
    ): List<Component> {

        val width =
            bitmap.width

        val step =
            3

        val scanWidth =
            width / step + 1

        val scanHeight =
            (
                trayBottom -
                        trayTop
                ) /
                    step +
                    1

        val visited =
            BooleanArray(
                scanWidth *
                        scanHeight
            )

        val components =
            ArrayList<Component>()

        for (
            sy in
            0 until scanHeight
        ) {

            val y =
                trayTop +
                        sy *
                        step

            if (
                y >= trayBottom
            ) {
                break
            }

            for (
                sx in
                0 until scanWidth
            ) {

                val x =
                    sx *
                            step

                if (
                    x >= width
                ) {
                    break
                }

                val index =
                    sy *
                            scanWidth +
                            sx

                if (
                    visited[index]
                ) {
                    continue
                }

                visited[index] = true

                if (
                    !isPiecePixel(
                        bitmap.getPixel(
                            x,
                            y
                        )
                    )
                ) {
                    continue
                }

                val component =
                    floodFillDownsampled(
                        bitmap = bitmap,
                        startX = x,
                        startY = y,
                        trayTop = trayTop,
                        trayBottom = trayBottom,
                        visited = visited,
                        scanWidth = scanWidth,
                        scanHeight = scanHeight,
                        estimatedCellSize = estimatedCellSize,
                        step = step
                    )

                if (
                    component != null
                ) {
                    components.add(
                        component
                    )
                }
            }
        }

        return filterComponents(
            components = components,
            estimatedCellSize = estimatedCellSize
        )
    }

    // ============================================================
    // FLOOD FILL
    // ============================================================

    private fun floodFill(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        trayTop: Int,
        trayBottom: Int,
        visited: BooleanArray,
        width: Int,
        estimatedCellSize: Float,
        step: Int
    ): Component? {

        val height =
            trayBottom -
                    trayTop

        val queueX =
            IntArray(
                4096
            )

        val queueY =
            IntArray(
                4096
            )

        var head =
            0

        var tail =
            0

        val startLocalY =
            startY -
                    trayTop

        val startIndex =
            startLocalY *
                    width +
                    startX

        if (
            startIndex < 0 ||
            startIndex >= visited.size
        ) {
            return null
        }

        queueX[tail] =
            startX

        queueY[tail] =
            startY

        tail++

        var minX =
            startX

        var maxX =
            startX

        var minY =
            startY

        var maxY =
            startY

        var area =
            0

        while (
            head < tail
        ) {

            val x =
                queueX[head]

            val y =
                queueY[head]

            head++

            area++

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
             * Four-neighbour flood fill.
             */
            val nx =
                intArrayOf(
                    x + step,
                    x - step,
                    x,
                    x
                )

            val ny =
                intArrayOf(
                    y,
                    y,
                    y + step,
                    y - step
                )

            for (
                i in 0..3
            ) {

                val px =
                    nx[i]

                val py =
                    ny[i]

                if (
                    px < 0 ||
                    px >= bitmap.width ||
                    py < trayTop ||
                    py >= trayBottom
                ) {
                    continue
                }

                val localY =
                    py -
                            trayTop

                val index =
                    localY *
                            width +
                            px

                if (
                    index < 0 ||
                    index >= visited.size
                ) {
                    continue
                }

                if (
                    visited[index]
                ) {
                    continue
                }

                visited[index] =
                    true

                if (
                    !isPiecePixel(
                        bitmap.getPixel(
                            px,
                            py
                        )
                    )
                ) {
                    continue
                }

                if (
                    tail >=
                    queueX.size
                ) {
                    continue
                }

                queueX[tail] =
                    px

                queueY[tail] =
                    py

                tail++
            }
        }

        if (
            area < MIN_COMPONENT_AREA
        ) {
            return null
        }

        val componentWidth =
            maxX -
                    minX +
                    1

        val componentHeight =
            maxY -
                    minY +
                    1

        /*
         * Reject huge UI elements.
         */
        if (
            componentWidth >
            estimatedCellSize * 2.8f ||
            componentHeight >
            estimatedCellSize * 2.8f
        ) {
            return null
        }

        return Component(
            centerX =
                (
                    minX +
                            maxX
                    ) /
                        2f,

            centerY =
                (
                    minY +
                            maxY
                    ) /
                        2f,

            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY,
            area = area
        )
    }

    // ============================================================
    // DOWNSAMPLED FLOOD FILL
    // ============================================================

    private fun floodFillDownsampled(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        trayTop: Int,
        trayBottom: Int,
        visited: BooleanArray,
        scanWidth: Int,
        scanHeight: Int,
        estimatedCellSize: Float,
        step: Int
    ): Component? {

        val queueX =
            IntArray(
                2048
            )

        val queueY =
            IntArray(
                2048
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

        var minX =
            startX

        var maxX =
            startX

        var minY =
            startY

        var maxY =
            startY

        var area =
            0

        while (
            head < tail
        ) {

            val x =
                queueX[head]

            val y =
                queueY[head]

            head++

            area++

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

            val directions =
                arrayOf(
                    intArrayOf(step, 0),
                    intArrayOf(-step, 0),
                    intArrayOf(0, step),
                    intArrayOf(0, -step)
                )

            for (
                direction in
                directions
            ) {

                val px =
                    x +
                            direction[0]

                val py =
                    y +
                            direction[1]

                if (
                    px < 0 ||
                    px >= bitmap.width ||
                    py < trayTop ||
                    py >= trayBottom
                ) {
                    continue
                }

                val sx =
                    px /
                            step

                val sy =
                    (
                        py -
                                trayTop
                        ) /
                            step

                if (
                    sx < 0 ||
                    sx >= scanWidth ||
                    sy < 0 ||
                    sy >= scanHeight
                ) {
                    continue
                }

                val index =
                    sy *
                            scanWidth +
                            sx

                if (
                    index < 0 ||
                    index >= visited.size ||
                    visited[index]
                ) {
                    continue
                }

                visited[index] =
                    true

                if (
                    !isPiecePixel(
                        bitmap.getPixel(
                            px,
                            py
                        )
                    )
                ) {
                    continue
                }

                if (
                    tail >=
                    queueX.size
                ) {
                    continue
                }

                queueX[tail] =
                    px

                queueY[tail] =
                    py

                tail++
            }
        }

        if (
            area < 5
        ) {
            return null
        }

        val componentWidth =
            maxX -
                    minX +
                    1

        val componentHeight =
            maxY -
                    minY +
                    1

        if (
            componentWidth >
            estimatedCellSize * 3.0f ||
            componentHeight >
            estimatedCellSize * 3.0f
        ) {
            return null
        }

        return Component(
            centerX =
                (
                    minX +
                            maxX
                    ) /
                        2f,

            centerY =
                (
                    minY +
                            maxY
                    ) /
                        2f,

            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY,
            area = area
        )
    }

    // ============================================================
    // PIECE PIXEL CLASSIFIER
    // ============================================================

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
         * Main colored block detection.
         */
        if (
            saturation >= 45 &&
            brightness >= 45
        ) {
            return true
        }

        /*
         * Bright yellow / orange / white-gold blocks.
         */
        if (
            brightness >= 120 &&
            saturation >= 20
        ) {
            return true
        }

        /*
         * Purple / blue / red / green blocks usually
         * have strong channel differences.
         */
        if (
            maximum >= 115 &&
            saturation >= 35
        ) {
            return true
        }

        return false
    }

    // ============================================================
    // COMPONENT FILTER
    // ============================================================

    private fun filterComponents(
        components: List<Component>,
        estimatedCellSize: Float
    ): List<Component> {

        if (
            components.isEmpty()
        ) {
            return emptyList()
        }

        val result =
            ArrayList<Component>()

        for (
            component in
            components
        ) {

            val width =
                component.width
                    .toFloat()

            val height =
                component.height
                    .toFloat()

            /*
             * A single tray block is normally approximately
             * square.
             */
            val aspect =
                max(
                    width,
                    height
                ) /
                        max(
                            1f,
                            min(
                                width,
                                height
                            )
                        )

            if (
                aspect > 2.2f
            ) {
                continue
            }

            /*
             * Reject tiny text/icon noise.
             */
            if (
                width <
                estimatedCellSize * 0.15f ||
                height <
                estimatedCellSize * 0.15f
            ) {
                continue
            }

            /*
             * Reject giant controls.
             */
            if (
                width >
                estimatedCellSize * 1.6f ||
                height >
                estimatedCellSize * 1.6f
            ) {
                continue
            }

            result.add(
                component
            )
        }

        return result
    }

    // ============================================================
    // GROUP INTO THREE PIECE SLOTS
    // ============================================================

    private fun groupIntoSlots(
        components: List<Component>,
        bitmapWidth: Int,
        slotCount: Int
    ): List<List<Component>> {

        val groups =
            Array(
                slotCount
            ) {
                ArrayList<Component>()
            }

        if (
            components.isEmpty()
        ) {
            return groups.map {
                it.toList()
            }
        }

        /*
         * Sort by horizontal position.
         */
        val sorted =
            components.sortedBy {
                it.centerX
            }

        /*
         * Instead of fixed coordinates, divide the screen
         * into equal logical regions.
         *
         * This works for different screen sizes.
         */
        for (
            component in
            sorted
        ) {

            val normalizedX =
                (
                    component.centerX /
                            bitmapWidth.toFloat()
                    )
                    .coerceIn(
                        0f,
                        0.9999f
                    )

            val slot =
                (
                    normalizedX *
                            slotCount
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        slotCount - 1
                    )

            groups[slot].add(
                component
            )
        }

        /*
         * If slot assignment becomes uneven,
         * redistribute by X clusters.
         */
        val emptySlots =
            groups.count {
                it.isEmpty()
            }

        if (
            emptySlots > 0 &&
            sorted.size >= slotCount
        ) {

            for (
                group in
                groups
            ) {
                group.clear()
            }

            /*
             * Equal X clustering.
             */
            for (
                i in
                sorted.indices
            ) {

                val slot =
                    (
                        i.toFloat() *
                                slotCount /
                                sorted.size.toFloat()
                        )
                        .toInt()
                        .coerceIn(
                            0,
                            slotCount - 1
                        )

                groups[slot].add(
                    sorted[i]
                )
            }
        }

        return groups.map {
            it.sortedWith(
                compareBy<Component> {
                    it.centerY
                }.thenBy {
                    it.centerX
                }
            )
        }
    }

    // ============================================================
    // COMPONENTS -> SHAPE CELLS
    // ============================================================

    private fun convertToShape(
        components: List<Component>,
        estimatedCellSize: Float
    ): List<Pair<Int, Int>> {

        if (
            components.isEmpty()
        ) {
            return emptyList()
        }

        /*
         * Use component centers.
         */
        val centers =
            components.map {
                it.centerX to
                        it.centerY
            }

        /*
         * Find average distance between detected cells.
         *
         * Tray cells are smaller than board cells.
         */
        val spacing =
            estimateSpacing(
                centers = centers,
                fallback = estimatedCellSize
            )

        if (
            spacing <= 1f
        ) {
            return emptyList()
        }

        val minX =
            centers.minOf {
                it.first
            }

        val minY =
            centers.minOf {
                it.second
            }

        val shape =
            ArrayList<Pair<Int, Int>>()

        for (
            center in
            centers
        ) {

            val normalizedColumn =
                (
                    (
                        center.first -
                                minX
                        ) /
                            spacing
                    )
                    .toInt()

            val normalizedRow =
                (
                    (
                        center.second -
                                minY
                        ) /
                            spacing
                    )
                    .toInt()

            shape.add(
                normalizedRow to
                        normalizedColumn
            )
        }

        /*
         * Remove duplicates.
         */
        return shape
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
    // SPACING ESTIMATION
    // ============================================================

    private fun estimateSpacing(
        centers: List<Pair<Float, Float>>,
        fallback: Float
    ): Float {

        if (
            centers.size <= 1
        ) {
            return fallback
        }

        val distances =
            ArrayList<Float>()

        for (
            i in
            centers.indices
        ) {

            for (
                j in
                i + 1 until centers.size
            ) {

                val dx =
                    abs(
                        centers[i].first -
                                centers[j].first
                    )

                val dy =
                    abs(
                        centers[i].second -
                                centers[j].second
                    )

                val distance =
                    when {

                        dx > fallback * 0.35f &&
                                dy < fallback * 0.65f ->
                            dx

                        dy > fallback * 0.35f &&
                                dx < fallback * 0.65f ->
                            dy

                        else ->
                            Float.MAX_VALUE
                    }

                if (
                    distance !=
                    Float.MAX_VALUE
                ) {
                    distances.add(
                        distance
                    )
                }
            }
        }

        if (
            distances.isEmpty()
        ) {
            return fallback
        }

        distances.sort()

        /*
         * Median nearest-cell distance.
         */
        val median =
            distances[
                distances.size / 2
            ]

        return median
            .coerceIn(
                fallback * 0.45f,
                fallback * 1.35f
            )
    }

    // ============================================================
    // NORMALIZATION
    // ============================================================

    private fun normalizeCells(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {

        if (
            cells.isEmpty()
        ) {
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
                (
                    it.first -
                            minRow
                    ) to
                        (
                            it.second -
                                    minColumn
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
}
