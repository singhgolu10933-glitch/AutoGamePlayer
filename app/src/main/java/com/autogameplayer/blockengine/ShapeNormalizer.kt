package com.autogameplayer.blockengine

object ShapeNormalizer {

    fun normalize(
        cells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {
        if (cells.isEmpty()) return emptyList()

        val minRow = cells.minOf { it.first }
        val minColumn = cells.minOf { it.second }

        return cells
            .map { Pair(it.first - minRow, it.second - minColumn) }
            .distinct()
            .sortedWith(
                compareBy<Pair<Int, Int>>(
                    { it.first },
                    { it.second }
                )
            )
    }

    fun width(
        cells: List<Pair<Int, Int>>
    ): Int {
        val normalized = normalize(cells)
        if (normalized.isEmpty()) return 0

        return normalized.maxOf { it.second } + 1
    }

    fun height(
        cells: List<Pair<Int, Int>>
    ): Int {
        val normalized = normalize(cells)
        if (normalized.isEmpty()) return 0

        return normalized.maxOf { it.first } + 1
    }

    fun toGrid(
        cells: List<Pair<Int, Int>>
    ): Array<BooleanArray> {
        val normalized = normalize(cells)

        if (normalized.isEmpty()) {
            return emptyArray()
        }

        val height = normalized.maxOf { it.first } + 1
        val width = normalized.maxOf { it.second } + 1

        val grid = Array(height) {
            BooleanArray(width)
        }

        for ((row, column) in normalized) {
            grid[row][column] = true
        }

        return grid
    }

    fun fromGrid(
        grid: Array<BooleanArray>
    ): List<Pair<Int, Int>> {

        val cells = mutableListOf<Pair<Int, Int>>()

        for (row in grid.indices) {
            for (column in grid[row].indices) {
                if (grid[row][column]) {
                    cells.add(Pair(row, column))
                }
            }
        }

        return normalize(cells)
    }
}
