package com.autogameplayer

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.autogameplayer.blockpuzzle.*
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var game: BlockGame
    private lateinit var gameView: BlockPuzzleView
    private lateinit var scoreText: TextView
    private lateinit var statusText: TextView
    private lateinit var autoButton: Button

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var autoPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        game = BlockGame()
        game.initialize()

        buildUi()
    }

    private fun buildUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 12, 18))
            setPadding(20, 20, 20, 20)
        }

        val title = TextView(this).apply {
            text = "AUTO GAME PLAYER"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                -1,
                55
            )
        )

        scoreText = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        root.addView(
            scoreText,
            LinearLayout.LayoutParams(-1, 45)
        )

        gameView = BlockPuzzleView()

        root.addView(
            gameView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        statusText = TextView(this).apply {
            text = "Select a piece, then tap a board cell"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(-1, 45)
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val resetButton = makeButton("RESET")

        autoButton = makeButton("AUTO PLAY")

        buttons.addView(
            resetButton,
            LinearLayout.LayoutParams(
                0,
                58,
                1f
            ).apply {
                setMargins(4, 4, 4, 4)
            }
        )

        buttons.addView(
            autoButton,
            LinearLayout.LayoutParams(
                0,
                58,
                1f
            ).apply {
                setMargins(4, 4, 4, 4)
            }
        )

        root.addView(buttons)

        resetButton.setOnClickListener {
            stopAuto()
            game.reset()
            game.initialize()
            statusText.text = "New game started"
            gameView.selectedPiece = -1
            gameView.invalidate()
            updateInfo()
        }

        autoButton.setOnClickListener {
            if (autoPlaying) {
                stopAuto()
            } else {
                startAuto()
            }
        }

        setContentView(root)

        updateInfo()
    }

    private fun makeButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)

            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(Color.rgb(35, 75, 140))
            }
        }
    }

    private fun updateInfo() {
        val state = game.snapshot()

        scoreText.text =
            "SCORE  ${state.score}     LEVEL  ${state.level}     MOVES  ${state.moves}"

        if (!BlockRules().hasMove(state)) {
            statusText.text = "GAME OVER"
        }
    }

    private fun startAuto() {

        autoPlaying = true
        autoButton.text = "STOP AUTO"
        statusText.text = "AI is playing..."

        autoStep()
    }

    private fun stopAuto() {

        autoPlaying = false
        autoButton.text = "AUTO PLAY"
        statusText.text = "Auto Play stopped"
    }

    private fun autoStep() {

        if (!autoPlaying) return

        val state = game.snapshot()
        val rules = BlockRules()

        if (!rules.hasMove(state)) {
            stopAuto()
            statusText.text = "GAME OVER"
            gameView.invalidate()
            return
        }

        executor.execute {

            try {

                val ai = BlockAi()

                val actions = game.legalActions(state)

                val action = ai.choose(
                    state,
                    actions
                )

                if (action != null) {

                    game.execute(action)

                    runOnUiThread {

                        updateInfo()
                        gameView.invalidate()

                        if (autoPlaying) {
                            handler.postDelayed(
                                { autoStep() },
                                180
                            )
                        }
                    }

                } else {

                    runOnUiThread {
                        stopAuto()
                        statusText.text = "No legal move"
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    stopAuto()
                    statusText.text =
                        "AI error: ${e.message ?: "unknown"}"
                }
            }
        }
    }

    override fun onDestroy() {

        stopAuto()
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()

        super.onDestroy()
    }

    inner class BlockPuzzleView : View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var selectedPiece = -1

        private var boardLeft = 0f
        private var boardTop = 0f
        private var cellSize = 0f

        private var pieceTop = 0f

        private val boardColor =
            Color.rgb(25, 29, 40)

        private val emptyColor =
            Color.rgb(42, 47, 62)

        private val gridColor =
            Color.rgb(65, 70, 88)

        private val pieceColors = intArrayOf(
            Color.rgb(0, 210, 255),
            Color.rgb(255, 170, 0),
            Color.rgb(120, 90, 255)
        )

        init {
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {

            super.onDraw(canvas)

            val state = game.snapshot()

            val width = measuredWidth.toFloat()
            val height = measuredHeight.toFloat()

            val boardSize =
                minOf(width - 30f, height * 0.63f)

            cellSize = boardSize / 9f

            boardLeft =
                (width - boardSize) / 2f

            boardTop = 20f

            pieceTop =
                boardTop + boardSize + 35f

            drawBoard(
                canvas,
                state
            )

            drawPieces(
                canvas,
                state
            )

            updateInfo()
        }

        private fun drawBoard(
            canvas: Canvas,
            state: BlockState
        ) {

            paint.style = Paint.Style.FILL
            paint.color = boardColor

            canvas.drawRoundRect(
                boardLeft - 6,
                boardTop - 6,
                boardLeft + cellSize * 9 + 6,
                boardTop + cellSize * 9 + 6,
                18f,
                18f,
                paint
            )

            for (r in 0 until 9) {

                for (c in 0 until 9) {

                    val left =
                        boardLeft + c * cellSize + 2

                    val top =
                        boardTop + r * cellSize + 2

                    val right =
                        boardLeft + (c + 1) * cellSize - 2

                    val bottom =
                        boardTop + (r + 1) * cellSize - 2

                    paint.color =
                        if (state.board[r][c]) {
                            pieceColors[(r + c) % pieceColors.size]
                        } else {
                            emptyColor
                        }

                    canvas.drawRoundRect(
                        left,
                        top,
                        right,
                        bottom,
                        7f,
                        7f,
                        paint
                    )

                    if (!state.board[r][c]) {

                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        paint.color = gridColor

                        canvas.drawRoundRect(
                            left,
                            top,
                            right,
                            bottom,
                            7f,
                            7f,
                            paint
                        )

                        paint.style = Paint.Style.FILL
                    }
                }
            }
        }

        private fun drawPieces(
            canvas: Canvas,
            state: BlockState
        ) {

            val slotWidth =
                measuredWidth / 3f

            for (index in state.pieces.indices) {

                val shape = state.pieces[index]

                val centerX =
                    slotWidth * index +
                    slotWidth / 2f

                val scale =
                    minOf(
                        25f,
                        slotWidth / 7f
                    )

                val shapeWidth =
                    shape.width * scale

                val shapeHeight =
                    shape.height * scale

                val startX =
                    centerX - shapeWidth / 2f

                val startY =
                    pieceTop

                if (index == selectedPiece) {

                    paint.color =
                        Color.WHITE

                    paint.style =
                        Paint.Style.STROKE

                    paint.strokeWidth = 4f

                    canvas.drawRoundRect(
                        centerX - slotWidth / 2f + 8,
                        startY - 12,
                        centerX + slotWidth / 2f - 8,
                        startY + 95,
                        15f,
                        15f,
                        paint
                    )

                    paint.style =
                        Paint.Style.FILL
                }

                shape.cells.forEach { cell ->

                    val left =
                        startX + cell.col * scale

                    val top =
                        startY + cell.row * scale

                    paint.color =
                        pieceColors[index % pieceColors.size]

                    canvas.drawRoundRect(
                        left + 2,
                        top + 2,
                        left + scale - 2,
                        top + scale - 2,
                        6f,
                        6f,
                        paint
                    )
                }
            }
        }

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    val x = event.x
                    val y = event.y

                    if (y >= pieceTop) {

                        val index =
                            (x / (measuredWidth / 3f))
                                .toInt()

                        val state =
                            game.snapshot()

                        if (
                            index >= 0 &&
                            index < state.pieces.size
                        ) {

                            selectedPiece = index

                            statusText.text =
                                "Piece ${index + 1} selected — tap board"

                            invalidate()

                            return true
                        }
                    }

                    return true
                }

                MotionEvent.ACTION_UP -> {

                    if (selectedPiece >= 0) {

                        val col =
                            ((event.x - boardLeft) /
                                    cellSize).toInt()

                        val row =
                            ((event.y - boardTop) /
                                    cellSize).toInt()

                        val state =
                            game.snapshot()

                        if (
                            row in 0 until 9 &&
                            col in 0 until 9
                        ) {

                            val action =
                                Placement(
                                    selectedPiece,
                                    row,
                                    col
                                )

                            if (game.execute(action)) {

                                statusText.text =
                                    "Piece placed!"

                                selectedPiece = -1

                                updateInfo()
                                invalidate()

                                return true

                            } else {

                                statusText.text =
                                    "Cannot place here"
                            }
                        }
                    }

                    return true
                }
            }

            return true
        }
    }
}
