package com.autogameplayer.blockpuzzle

class BlockRules(
    val size: Int = 9,
    val piecesPerTurn: Int = 3,
    val scorePerBlock: Int = 10,
    val scorePerLine: Int = 100,
    val targetPerLevel: Int = 2000
) {
    fun emptyBoard(): Array<BooleanArray> =
        Array(size) { BooleanArray(size) }

    fun canPlace(
        board: Array<BooleanArray>,
        shape: Shape,
        row: Int,
        col: Int
    ): Boolean {
        return shape.cells.all {
            val r = row + it.row
            val c = col + it.col
            r in 0 until size && c in 0 until size && !board[r][c]
        }
    }

    fun place(board: Array<BooleanArray>, shape: Shape, row: Int, col: Int) {
        shape.cells.forEach {
            board[row + it.row][col + it.col] = true
        }
    }

    fun clearLines(board: Array<BooleanArray>): Int {
        val fullRows = (0 until size).filter { r ->
            (0 until size).all { c -> board[r][c] }
        }
        val fullCols = (0 until size).filter { c ->
            (0 until size).all { r -> board[r][c] }
        }

        fullRows.forEach { r -> (0 until size).forEach { c -> board[r][c] = false } }
        fullCols.forEach { c -> (0 until size).forEach { r -> board[r][c] = false } }

        return fullRows.size + fullCols.size
    }

    fun apply(state: BlockState, action: Placement): BlockState? {
        if (action.pieceIndex !in state.pieces.indices) return null

        val shape = state.pieces[action.pieceIndex]
        if (!canPlace(state.board, shape, action.row, action.col)) return null

        val board = state.copyBoard()
        place(board, shape, action.row, action.col)
        val cleared = clearLines(board)

        val gain = shape.size * scorePerBlock + cleared * scorePerLine
        val newScore = state.score + gain
        val newLevel = newScore / targetPerLevel + 1

        val remaining = state.pieces.filterIndexed { index, _ ->
            index != action.pieceIndex
        }

        return state.copy(
            board = board,
            pieces = remaining,
            score = newScore,
            level = newLevel,
            moves = state.moves + 1
        )
    }

    fun legalPlacements(state: BlockState): List<Placement> {
        val result = ArrayList<Placement>()
        state.pieces.indices.forEach { pieceIndex ->
            val shape = state.pieces[pieceIndex]
            for (r in 0 until size) {
                for (c in 0 until size) {
                    if (canPlace(state.board, shape, r, c)) {
                        result += Placement(pieceIndex, r, c)
                    }
                }
            }
        }
        return result
    }

    fun hasMove(state: BlockState): Boolean =
        legalPlacements(state).isNotEmpty()
}
