package com.autogameplayer.blockpuzzle

import com.autogameplayer.core.DecisionPolicy

data class AiWeights(
    val line: Double = 28.0,
    val mobility: Double = 12.0,
    val space: Double = 1.2,
    val fragmentation: Double = 5.0,
    val hole: Double = 2.0,
    val center: Double = 0.4
)

class BlockAi(
    private val rules: BlockRules = BlockRules(),
    private val weights: AiWeights = AiWeights(),
    private val depth: Int = 2
) : DecisionPolicy<BlockState, Placement> {

    override fun choose(
        state: BlockState,
        legalActions: List<Placement>
    ): Placement? {
        if (legalActions.isEmpty()) return null

        return legalActions.maxByOrNull { action ->
            val next = rules.apply(state, action) ?: return@maxByOrNull Double.NEGATIVE_INFINITY
            search(next, depth - 1)
        }
    }

    private fun search(state: BlockState, remainingDepth: Int): Double {
        val actions = rules.legalPlacements(state)
        if (remainingDepth <= 0 || actions.isEmpty()) {
            return evaluate(state)
        }

        // Beam pruning keeps the search fast on mobile.
        val candidates = actions
            .mapNotNull { action ->
                rules.apply(state, action)?.let { action to it }
            }
            .sortedByDescending { evaluate(it.second) }
            .take(32)

        return candidates.maxOfOrNull { (_, next) ->
            search(next, remainingDepth - 1)
        } ?: evaluate(state)
    }

    private fun evaluate(state: BlockState): Double {
        val board = state.board
        var linePotential = 0
        var fragmentation = 0
        var holes = 0
        var mobility = 0

        for (r in 0 until rules.size) {
            val count = (0 until rules.size).count { board[r][it] }
            if (count >= 6) linePotential += count * count
        }

        for (c in 0 until rules.size) {
            val count = (0 until rules.size).count { board[it][c] }
            if (count >= 6) linePotential += count * count
        }

        for (r in 0 until rules.size) {
            for (c in 0 until rules.size) {
                if (!board[r][c]) {
                    val occupiedNeighbors =
                        listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                            .count { (rr, cc) ->
                                rr in 0 until rules.size &&
                                    cc in 0 until rules.size &&
                                    board[rr][cc]
                            }
                    if (occupiedNeighbors >= 3) holes++
                }
            }
        }

        val occupied = state.occupiedCount()
        mobility = rules.legalPlacements(state).size

        for (r in 0 until rules.size) {
            for (c in 0 until rules.size) {
                if (board[r][c] && (r == 0 || r == rules.size - 1 ||
                    c == 0 || c == rules.size - 1)) {
                    fragmentation++
                }
            }
        }

        val free = rules.size * rules.size - occupied

        return state.score * 4.0 +
            linePotential * weights.line +
            mobility * weights.mobility +
            free * weights.space -
            fragmentation * weights.fragmentation -
            holes * weights.hole
    }
}
