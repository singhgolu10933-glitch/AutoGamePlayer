package com.autogameplayer.blockpuzzle

import com.autogameplayer.core.GameModule
import kotlin.random.Random

class BlockGame(
    private val rules: BlockRules = BlockRules(),
    private val random: Random = Random.Default
) : GameModule<BlockState, Placement> {

    override val id: String = "block-puzzle-001"

    override val name: String = "Block Puzzle 001"

    private var running = false

    private var state: BlockState = createState()

    override fun initialize() {
        running = true
        state = createState()
    }

    override fun snapshot(): BlockState {
        return state.copy(
            board = state.copyBoard()
        )
    }

    override fun legalActions(
        state: BlockState
    ): List<Placement> {
        return rules.legalPlacements(state)
    }

    override fun execute(action: Placement): Boolean {
        if (!running) return false

        val nextState = rules.apply(state, action)
            ?: return false

        state = nextState

        if (state.pieces.isEmpty()) {
            state = state.copy(
                pieces = randomPieces()
            )
        }

        return true
    }

    override fun stop() {
        running = false
    }

    fun reset() {
        running = false
        state = createState()
    }

    private fun createState(): BlockState {
        return BlockState(
            board = rules.emptyBoard(),
            pieces = randomPieces()
        )
    }

    private fun randomPieces(): List<Shape> {
        return List(rules.piecesPerTurn) {
            Shapes.catalog.random(random)
        }
    }
}
