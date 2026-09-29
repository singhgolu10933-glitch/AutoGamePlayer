package com.autogameplayer.blockpuzzle

data class Cell(val row: Int, val col: Int)

data class Shape(
    val id: String,
    val cells: List<Cell>
) {
    val size: Int get() = cells.size
    val width: Int get() = (cells.maxOfOrNull { it.col } ?: 0) + 1
    val height: Int get() = (cells.maxOfOrNull { it.row } ?: 0) + 1
}

data class Placement(
    val pieceIndex: Int,
    val row: Int,
    val col: Int
)

data class BlockState(
    val board: Array<BooleanArray>,
    val pieces: List<Shape>,
    val score: Int = 0,
    val level: Int = 1,
    val moves: Int = 0
) {
    fun copyBoard(): Array<BooleanArray> =
        Array(board.size) { r -> board[r].clone() }

    fun occupiedCount(): Int =
        board.sumOf { row -> row.count { it } }
}
