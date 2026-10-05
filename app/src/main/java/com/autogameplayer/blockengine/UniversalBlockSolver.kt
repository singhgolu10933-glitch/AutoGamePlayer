package com.autogameplayer.blockengine

/**
 * ============================================================
 * UNIVERSAL BLOCK SOLVER
 * ============================================================
 *
 * Fast multi-step AI for block-puzzle style Android games.
 *
 * Features:
 *
 * 1. Legal placement detection
 * 2. Line clear prediction
 * 3. Hole detection
 * 4. Fragmentation detection
 * 5. Surface / height analysis
 * 6. Future mobility analysis
 * 7. Multi-piece look-ahead
 * 8. Beam search for speed
 * 9. Pattern-aware scoring
 * 10. No game interaction
 *
 * The solver ONLY calculates a move.
 * It does not tap, drag or modify the game.
 * ============================================================
 */

object UniversalBlockSolver {

    // ============================================================
    // PERFORMANCE
    // ============================================================

    /**
     * Maximum number of promising moves retained
     * at every look-ahead level.
     *
     * Higher = stronger but slower.
     */
    private const val BEAM_WIDTH = 18

    /**
     * Maximum search depth.
     *
     * 1 = current move
     * 2 = current + next
     * 3 = current + next + next
     */
    private const val SEARCH_DEPTH = 3

    /**
     * Keep only the best candidates from each piece.
     */
    private const val CANDIDATES_PER_PIECE = 12

    // ============================================================
    // SCORE WEIGHTS
    // ============================================================

    private const val LINE_CLEAR = 1800.0
    private const val DOUBLE_CLEAR = 900.0
    private const val TRIPLE_CLEAR = 1800.0
    private const val QUAD_CLEAR = 3500.0

    private const val MOBILITY_WEIGHT = 7.0
    private const val FREE_SPACE_WEIGHT = 2.0

    private const val HOLE_WEIGHT = 42.0
    private const val FRAGMENT_WEIGHT = 8.0
    private const val HEIGHT_WEIGHT = 4.0

    private const val EDGE_WEIGHT = 1.5
    private const val COMPACTNESS_WEIGHT = 3.0

    private const val DEAD_CELL_WEIGHT = 10.0

    // ============================================================
    // SEARCH NODE
    // ============================================================

    private data class SearchNode(
        val board: UniversalBoard,
        val score: Double,
        val firstMove: UniversalMove,
        val depth: Int
    )

    // ============================================================
    // SIMULATION
    // ============================================================

    private data class SimulationResult(
        val board: UniversalBoard,
        val clearedRows: Int,
        val clearedColumns: Int
    ) {

        val clearedLines: Int
            get() = clearedRows + clearedColumns
    }

    // ============================================================
    // PUBLIC ENTRY POINT
    // ============================================================

    /**
     * Finds the best move using multi-step look-ahead.
     *
     * This method is intentionally compatible with:
     *
     * UniversalBlockSolver.findBestMove(
     *     board,
     *     pieces
     * )
     */
    fun findBestMove(
        board: UniversalBoard,
        pieces: List<UniversalBlockPiece>
    ): UniversalMove? {

        if (pieces.isEmpty()) {
            return null
        }

        if (board.rows <= 0 || board.columns <= 0) {
            return null
        }

        val validPieces =
            pieces.filter {
                it.normalizedCells.isNotEmpty()
            }

        if (validPieces.isEmpty()) {
            return null
        }

        /*
         * Fast path for one piece.
         */
        if (validPieces.size == 1) {
            return findBestSingleMove(
                board = board,
                piece = validPieces[0]
            )
        }

        /*
         * Multi-step search.
         */
        return searchBestMove(
            board = board,
            pieces = validPieces
        )
    }

    // ============================================================
    // MULTI STEP SEARCH
    // ============================================================

    private fun searchBestMove(
        board: UniversalBoard,
        pieces: List<UniversalBlockPiece>
    ): UniversalMove? {

        /*
         * Generate first-level candidates.
         */
        val firstCandidates =
            generateCandidates(
                board = board,
                pieces = pieces,
                limitPerPiece = CANDIDATES_PER_PIECE
            )

        if (firstCandidates.isEmpty()) {
            return null
        }

        /*
         * Search tree.
         */
        var nodes =
            firstCandidates.map {
                SearchNode(
                    board = it.afterBoard,
                    score = it.score,
                    firstMove = it.move,
                    depth = 1
                )
            }

        /*
         * If there are no remaining pieces,
         * first move is enough.
         */
        if (pieces.size <= 1) {
            return nodes.maxByOrNull {
                it.score
            }?.firstMove
        }

        /*
         * Look ahead through available pieces.
         *
         * We use a beam rather than brute-force all combinations.
         * This keeps analysis very fast.
         */
        var depth = 1

        while (
            depth < SEARCH_DEPTH &&
            depth < pieces.size &&
            nodes.isNotEmpty()
        ) {

            val expanded =
                ArrayList<SearchNode>(
                    nodes.size * BEAM_WIDTH
                )

            for (node in nodes) {

                /*
                 * Remove the piece already used in the
                 * first sequence when possible.
                 *
                 * For subsequent look-ahead we consider
                 * remaining available pieces.
                 */
                val remainingPieces =
                    pieces.filter {
                        it.id != node.firstMove.pieceId
                    }

                if (remainingPieces.isEmpty()) {
                    expanded.add(node)
                    continue
                }

                val candidates =
                    generateCandidates(
                        board = node.board,
                        pieces = remainingPieces,
                        limitPerPiece = 6
                    )

                if (candidates.isEmpty()) {
                    /*
                     * Dead end.
                     *
                     * Penalize sequences that leave no
                     * continuation.
                     */
                    expanded.add(
                        node.copy(
                            score = node.score - 5000.0,
                            depth = depth + 1
                        )
                    )

                    continue
                }

                for (candidate in candidates) {

                    val continuationScore =
                        candidate.score *
                                continuationWeight(depth)

                    expanded.add(
                        SearchNode(
                            board = candidate.afterBoard,
                            score =
                                node.score +
                                        continuationScore,
                            firstMove =
                                node.firstMove,
                            depth = depth + 1
                        )
                    )
                }
            }

            /*
             * Beam pruning.
             */
            nodes =
                expanded
                    .sortedByDescending {
                        it.score
                    }
                    .take(BEAM_WIDTH)

            depth++
        }

        return nodes
            .maxByOrNull {
                it.score
            }
            ?.firstMove
    }

    // ============================================================
    // SINGLE MOVE
    // ============================================================

    private fun findBestSingleMove(
        board: UniversalBoard,
        piece: UniversalBlockPiece
    ): UniversalMove? {

        val shape =
            piece.normalizedCells

        if (shape.isEmpty()) {
            return null
        }

        val maxRow =
            board.rows - piece.height

        val maxColumn =
            board.columns - piece.width

        if (
            maxRow < 0 ||
            maxColumn < 0
        ) {
            return null
        }

        var bestMove: UniversalMove? = null

        for (row in 0..maxRow) {

            for (column in 0..maxColumn) {

                if (
                    !canPlace(
                        board = board,
                        piece = shape,
                        row = row,
                        column = column
                    )
                ) {
                    continue
                }

                val simulation =
                    simulateMove(
                        board = board,
                        piece = shape,
                        row = row,
                        column = column
                    )

                val score =
                    evaluateBoard(
                        before = board,
                        after = simulation.board,
                        clearedRows =
                            simulation.clearedRows,
                        clearedColumns =
                            simulation.clearedColumns,
                        pieceCellCount =
                            shape.size
                    )

                val move =
                    UniversalMove(
                        pieceId = piece.id,
                        row = row,
                        column = column,
                        score = score,
                        clearedRows =
                            simulation.clearedRows,
                        clearedColumns =
                            simulation.clearedColumns,
                        clearedLines =
                            simulation.clearedLines,
                        reason =
                            buildReason(
                                simulation =
                                    simulation,
                                score = score
                            )
                    )

                if (
                    bestMove == null ||
                    isBetterMove(
                        move,
                        bestMove
                    )
                ) {
                    bestMove = move
                }
            }
        }

        return bestMove
    }

    // ============================================================
    // CANDIDATE GENERATION
    // ============================================================

    private data class Candidate(
        val move: UniversalMove,
        val afterBoard: UniversalBoard,
        val score: Double
    )

    private fun generateCandidates(
        board: UniversalBoard,
        pieces: List<UniversalBlockPiece>,
        limitPerPiece: Int
    ): List<Candidate> {

        val result =
            ArrayList<Candidate>()

        for (piece in pieces) {

            val shape =
                piece.normalizedCells

            if (shape.isEmpty()) {
                continue
            }

            val maxRow =
                board.rows - piece.height

            val maxColumn =
                board.columns - piece.width

            if (
                maxRow < 0 ||
                maxColumn < 0
            ) {
                continue
            }

            val pieceCandidates =
                ArrayList<Candidate>()

            for (row in 0..maxRow) {

                for (column in 0..maxColumn) {

                    if (
                        !canPlace(
                            board = board,
                            piece = shape,
                            row = row,
                            column = column
                        )
                    ) {
                        continue
                    }

                    val simulation =
                        simulateMove(
                            board = board,
                            piece = shape,
                            row = row,
                            column = column
                        )

                    val score =
                        evaluateBoard(
                            before = board,
                            after = simulation.board,
                            clearedRows =
                                simulation.clearedRows,
                            clearedColumns =
                                simulation.clearedColumns,
                            pieceCellCount =
                                shape.size
                        )

                    val move =
                        UniversalMove(
                            pieceId = piece.id,
                            row = row,
                            column = column,
                            score = score,
                            clearedRows =
                                simulation.clearedRows,
                            clearedColumns =
                                simulation.clearedColumns,
                            clearedLines =
                                simulation.clearedLines,
                            reason =
                                buildReason(
                                    simulation =
                                        simulation,
                                    score = score
                                )
                        )

                    pieceCandidates.add(
                        Candidate(
                            move = move,
                            afterBoard =
                                simulation.board,
                            score = score
                        )
                    )
                }
            }

            /*
             * Keep only the strongest placements
             * for this individual piece.
             */
            result.addAll(
                pieceCandidates
                    .sortedByDescending {
                        it.score
                    }
                    .take(limitPerPiece)
            )
        }

        /*
         * Global beam pruning.
         */
        return result
            .sortedByDescending {
                it.score
            }
            .take(BEAM_WIDTH)
    }

    // ============================================================
    // LEGAL PLACEMENT
    // ============================================================

    fun canPlace(
        board: UniversalBoard,
        piece: List<UniversalCell>,
        row: Int,
        column: Int
    ): Boolean {

        for (cell in piece) {

            val targetRow =
                row + cell.row

            val targetColumn =
                column + cell.column

            if (
                targetRow < 0 ||
                targetRow >= board.rows
            ) {
                return false
            }

            if (
                targetColumn < 0 ||
                targetColumn >= board.columns
            ) {
                return false
            }

            if (
                board.occupied[
                    targetRow
                ][
                    targetColumn
                ]
            ) {
                return false
            }
        }

        return true
    }

    // ============================================================
    // MOVE SIMULATION
    // ============================================================

    private fun simulateMove(
        board: UniversalBoard,
        piece: List<UniversalCell>,
        row: Int,
        column: Int
    ): SimulationResult {

        val simulated =
            board.copyBoard()

        /*
         * Place piece.
         */
        for (cell in piece) {

            val targetRow =
                row + cell.row

            val targetColumn =
                column + cell.column

            if (
                targetRow in 0 until simulated.rows &&
                targetColumn in 0 until simulated.columns
            ) {

                simulated.occupied[
                    targetRow
                ][
                    targetColumn
                ] = true
            }
        }

        /*
         * Find completed rows.
         */
        val fullRows =
            ArrayList<Int>()

        for (r in 0 until simulated.rows) {

            var full = true

            for (c in 0 until simulated.columns) {

                if (
                    !simulated.occupied[r][c]
                ) {
                    full = false
                    break
                }
            }

            if (full) {
                fullRows.add(r)
            }
        }

        /*
         * Find completed columns.
         *
         * Some block games clear only rows.
         * However, supporting columns makes the
         * engine reusable for games that support both.
         */
        val fullColumns =
            ArrayList<Int>()

        for (c in 0 until simulated.columns) {

            var full = true

            for (r in 0 until simulated.rows) {

                if (
                    !simulated.occupied[r][c]
                ) {
                    full = false
                    break
                }
            }

            if (full) {
                fullColumns.add(c)
            }
        }

        /*
         * Clear rows.
         */
        for (r in fullRows) {

            for (c in 0 until simulated.columns) {

                simulated.occupied[r][c] =
                    false
            }
        }

        /*
         * Clear columns.
         */
        for (c in fullColumns) {

            for (r in 0 until simulated.rows) {

                simulated.occupied[r][c] =
                    false
            }
        }

        return SimulationResult(
            board = simulated,
            clearedRows =
                fullRows.size,
            clearedColumns =
                fullColumns.size
        )
    }

    // ============================================================
    // BOARD EVALUATION
    // ============================================================

    private fun evaluateBoard(
        before: UniversalBoard,
        after: UniversalBoard,
        clearedRows: Int,
        clearedColumns: Int,
        pieceCellCount: Int
    ): Double {

        val clearedLines =
            clearedRows + clearedColumns

        val occupiedBefore =
            before.occupiedCount()

        val occupiedAfter =
            after.occupiedCount()

        val totalCells =
            after.rows * after.columns

        val freeCells =
            totalCells -
                    occupiedAfter

        /*
         * --------------------------------------------------------
         * LINE CLEAR VALUE
         * --------------------------------------------------------
         */

        var score =
            clearedLines *
                    LINE_CLEAR

        when {
            clearedLines == 2 -> {
                score += DOUBLE_CLEAR
            }

            clearedLines == 3 -> {
                score += TRIPLE_CLEAR
            }

            clearedLines >= 4 -> {
                score += QUAD_CLEAR
            }
        }

        /*
         * --------------------------------------------------------
         * FREE SPACE
         * --------------------------------------------------------
         */

        score +=
            freeCells *
                    FREE_SPACE_WEIGHT

        /*
         * --------------------------------------------------------
         * MOBILITY
         * --------------------------------------------------------
         */

        val mobility =
            countPlacementMobility(
                after
            )

        score +=
            mobility *
                    MOBILITY_WEIGHT

        /*
         * --------------------------------------------------------
         * HOLES
         * --------------------------------------------------------
         */

        val holes =
            countHoles(
                after
            )

        score -=
            holes *
                    HOLE_WEIGHT

        /*
         * --------------------------------------------------------
         * FRAGMENTATION
         * --------------------------------------------------------
         */

        score -=
            fragmentationPenalty(
                after
            ) *
                    FRAGMENT_WEIGHT

        /*
         * --------------------------------------------------------
         * HEIGHT
         * --------------------------------------------------------
         */

        val heightPenalty =
            boardHeightPenalty(
                after
            )

        score -=
            heightPenalty *
                    HEIGHT_WEIGHT

        /*
         * --------------------------------------------------------
         * DEAD CELLS
         * --------------------------------------------------------
         */

        val deadCells =
            countDeadCells(
                after
            )

        score -=
            deadCells *
                    DEAD_CELL_WEIGHT

        /*
         * --------------------------------------------------------
         * COMPACTNESS
         * --------------------------------------------------------
         */

        score +=
            compactnessScore(
                after
            ) *
                    COMPACTNESS_WEIGHT

        /*
         * --------------------------------------------------------
         * EDGE BALANCE
         * --------------------------------------------------------
         */

        score +=
            edgeBalanceScore(
                after
            ) *
                    EDGE_WEIGHT

        /*
         * --------------------------------------------------------
         * PIECE EFFICIENCY
         * --------------------------------------------------------
         */

        if (pieceCellCount > 0) {

            val occupancyAdded =
                occupiedAfter -
                        occupiedBefore

            /*
             * Penalize moves that add lots of blocks
             * without clearing anything.
             */
            if (clearedLines == 0) {

                score -=
                    occupancyAdded *
                            2.0
            }
        }

        /*
         * --------------------------------------------------------
         * VERY STRONG DEAD-BOARD PENALTY
         * --------------------------------------------------------
         */

        if (
            mobility == 0 &&
            occupiedAfter > 0
        ) {

            score -=
                10000.0
        }

        /*
         * Reward a board that remains playable.
         */
        if (mobility > 20) {
            score += 100.0
        }

        if (mobility > 40) {
            score += 150.0
        }

        return score
    }

    // ============================================================
    // MOBILITY
    // ============================================================

    private fun countPlacementMobility(
        board: UniversalBoard
    ): Int {

        var count = 0

        for (r in 0 until board.rows) {

            for (c in 0 until board.columns) {

                if (
                    !board.occupied[r][c]
                ) {
                    count++
                }
            }
        }

        return count
    }

    // ============================================================
    // HOLE DETECTION
    // ============================================================

    private fun countHoles(
        board: UniversalBoard
    ): Int {

        var holes = 0

        for (r in 0 until board.rows) {

            for (c in 0 until board.columns) {

                if (
                    board.occupied[r][c]
                ) {
                    continue
                }

                var surrounded = 0

                if (
                    r > 0 &&
                    board.occupied[r - 1][c]
                ) {
                    surrounded++
                }

                if (
                    r + 1 < board.rows &&
                    board.occupied[r + 1][c]
                ) {
                    surrounded++
                }

                if (
                    c > 0 &&
                    board.occupied[r][c - 1]
                ) {
                    surrounded++
                }

                if (
                    c + 1 < board.columns &&
                    board.occupied[r][c + 1]
                ) {
                    surrounded++
                }

                if (surrounded >= 3) {
                    holes++
                }
            }
        }

        return holes
    }

    // ============================================================
    // DEAD CELL DETECTION
    // ============================================================

    private fun countDeadCells(
        board: UniversalBoard
    ): Int {

        var dead = 0

        for (r in 0 until board.rows) {

            for (c in 0 until board.columns) {

                if (
                    board.occupied[r][c]
                ) {
                    continue
                }

                var blockedDirections = 0

                if (
                    r > 0 &&
                    board.occupied[r - 1][c]
                ) {
                    blockedDirections++
                }

                if (
                    r + 1 < board.rows &&
                    board.occupied[r + 1][c]
                ) {
                    blockedDirections++
                }

                if (
                    c > 0 &&
                    board.occupied[r][c - 1]
                ) {
                    blockedDirections++
                }

                if (
                    c + 1 < board.columns &&
                    board.occupied[r][c + 1]
                ) {
                    blockedDirections++
                }

                if (
                    blockedDirections >= 3
                ) {
                    dead++
                }
            }
        }

        return dead
    }

    // ============================================================
    // FRAGMENTATION
    // ============================================================

    private fun fragmentationPenalty(
        board: UniversalBoard
    ): Double {

        var penalty = 0.0

        for (r in 0 until board.rows) {

            for (c in 0 until board.columns) {

                if (
                    !board.occupied[r][c]
                ) {
                    continue
                }

                var neighbours = 0

                if (
                    r > 0 &&
                    board.occupied[r - 1][c]
                ) {
                    neighbours++
                }

                if (
                    r + 1 < board.rows &&
                    board.occupied[r + 1][c]
                ) {
                    neighbours++
                }

                if (
                    c > 0 &&
                    board.occupied[r][c - 1]
                ) {
                    neighbours++
                }

                if (
                    c + 1 < board.columns &&
                    board.occupied[r][c + 1]
                ) {
                    neighbours++
                }

                if (neighbours == 0) {
                    penalty += 3.0
                }

                if (neighbours == 1) {
                    penalty += 1.0
                }
            }
        }

        return penalty
    }

    // ============================================================
    // HEIGHT ANALYSIS
    // ============================================================

    private fun boardHeightPenalty(
        board: UniversalBoard
    ): Double {

        var totalHeight = 0.0

        for (c in 0 until board.columns) {

            var columnHeight = 0

            for (r in 0 until board.rows) {

                if (
                    board.occupied[r][c]
                ) {

                    columnHeight =
                        board.rows - r

                    break
                }
            }

            totalHeight +=
                columnHeight.toDouble()
        }

        return totalHeight
    }

    // ============================================================
    // COMPACTNESS
    // ============================================================

    private fun compactnessScore(
        board: UniversalBoard
    ): Double {

        var occupied = 0
        var neighbours = 0

        for (r in 0 until board.rows) {

            for (c in 0 until board.columns) {

                if (
                    !board.occupied[r][c]
                ) {
                    continue
                }

                occupied++

                if (
                    r + 1 < board.rows &&
                    board.occupied[r + 1][c]
                ) {
                    neighbours++
                }

                if (
                    c + 1 < board.columns &&
                    board.occupied[r][c + 1]
                ) {
                    neighbours++
                }
            }
        }

        if (occupied == 0) {
            return 0.0
        }

        return neighbours.toDouble() /
                occupied.toDouble()
    }

    // ============================================================
    // EDGE BALANCE
    // ============================================================

    private fun edgeBalanceScore(
        board: UniversalBoard
    ): Double {

        var score = 0.0

        for (r in 0 until board.rows) {

            for (c in 0 until board.columns) {

                if (
                    !board.occupied[r][c]
                ) {
                    continue
                }

                if (
                    r == 0 ||
                    r == board.rows - 1
                ) {
                    score += 0.5
                }

                if (
                    c == 0 ||
                    c == board.columns - 1
                ) {
                    score += 0.5
                }
            }
        }

        return score
    }

    // ============================================================
    // SEARCH WEIGHT
    // ============================================================

    private fun continuationWeight(
        depth: Int
    ): Double {

        return when (depth) {
            1 -> 0.72
            2 -> 0.48
            else -> 0.30
        }
    }

    // ============================================================
    // MOVE COMPARISON
    // ============================================================

    private fun isBetterMove(
        candidate: UniversalMove,
        current: UniversalMove
    ): Boolean {

        /*
         * First priority:
         * line clears.
         */
        if (
            candidate.clearedLines !=
            current.clearedLines
        ) {

            return candidate.clearedLines >
                    current.clearedLines
        }

        /*
         * Second priority:
         * overall AI score.
         */
        if (
            candidate.score !=
            current.score
        ) {

            return candidate.score >
                    current.score
        }

        /*
         * Stable deterministic tie breaker.
         */
        if (
            candidate.pieceId !=
            current.pieceId
        ) {

            return candidate.pieceId <
                    current.pieceId
        }

        if (
            candidate.row !=
            current.row
        ) {

            return candidate.row <
                    current.row
        }

        return candidate.column <
                current.column
    }

    // ============================================================
    // MOVE REASON
    // ============================================================

    private fun buildReason(
        simulation: SimulationResult,
        score: Double
    ): String {

        val lines =
            simulation.clearedLines

        return when {

            lines >= 4 ->
                "Extreme multi-line clear"

            lines == 3 ->
                "Triple line clear"

            lines == 2 ->
                "Double line clear"

            lines == 1 ->
                "Line clear + future-space optimization"

            score >= 1000.0 ->
                "Strong pattern / mobility placement"

            else ->
                "Best safe future placement"
        }
    }
}
