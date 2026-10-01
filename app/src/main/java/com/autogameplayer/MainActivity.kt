package com.autogameplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.content.pm.PackageManager

class MainActivity : Activity() {

    companion object {
        private const val SCREEN_CAPTURE_REQUEST = 1001
        private const val PREFS = "player_settings"
        private const val VISION_PREFS = "vision_status"
    }

    data class AppEntry(
        val name: String,
        val packageName: String
    ) {
        override fun toString(): String {
            return "$name • $packageName"
        }
    }

    private val apps =
        mutableListOf<AppEntry>()

    private var selectedPackage:
            String? = null

    private lateinit var statusText: TextView
    private lateinit var observationText: TextView

    private lateinit var gameText: TextView
    private lateinit var boardText: TextView
    private lateinit var occupiedText: TextView
    private lateinit var piecesText: TextView
    private lateinit var confidenceText: TextView

    private lateinit var piece1Text: TextView
    private lateinit var piece2Text: TextView
    private lateinit var piece3Text: TextView

    private lateinit var bestMoveText: TextView
    private lateinit var bestPieceText: TextView
    private lateinit var bestTargetText: TextView
    private lateinit var bestScoreText: TextView
    private lateinit var bestReasonText: TextView

    private lateinit var appSpinner: Spinner

    private val refreshRunnable =
        object : Runnable {

            override fun run() {

                updateStatus()

                window.decorView.postDelayed(
                    this,
                    700L
                )
            }
        }

    // ============================================================
    // CREATE
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        buildUi()

        loadApps()

        loadSavedTarget()

        window.decorView.post(
            refreshRunnable
        )
    }

    // ============================================================
    // UI
    // ============================================================

    private fun buildUi() {

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    32,
                    32,
                    32,
                    32
                )

                setBackgroundColor(
                    Color.WHITE
                )
            }

        val scroll =
            ScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL
            }

        content.addView(
            textView(
                "AUTO GAME PLAYER",
                26f,
                true
            )
        )

        content.addView(
            textView(
                "Universal Android Game AI",
                15f,
                false
            )
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // ACCESSIBILITY
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "CONTROL ACCESS"
            )
        )

        statusText =
            textView(
                "Accessibility Service: Checking...",
                15f,
                true
            )

        content.addView(
            statusText
        )

        content.addView(
            Button(this).apply {

                text =
                    "OPEN ACCESSIBILITY SETTINGS"

                setOnClickListener {

                    startActivity(
                        Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                    )
                }
            }
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // TARGET
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "TARGET GAME"
            )
        )

        appSpinner =
            Spinner(this)

        content.addView(
            appSpinner
        )

        content.addView(
            Button(this).apply {

                text =
                    "SELECT TARGET GAME"

                setOnClickListener {
                    saveSelectedTarget()
                }
            }
        )

        content.addView(
            Button(this).apply {

                text =
                    "OPEN SELECTED GAME"

                setOnClickListener {
                    launchSelectedGame()
                }
            }
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // SCREEN
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "SCREEN OBSERVATION"
            )
        )

        observationText =
            textView(
                "Screen Observation: STOPPED",
                15f,
                true
            )

        content.addView(
            observationText
        )

        content.addView(
            Button(this).apply {

                text =
                    "START SCREEN OBSERVATION"

                setOnClickListener {
                    requestScreenCapture()
                }
            }
        )

        content.addView(
            Button(this).apply {

                text =
                    "STOP SCREEN OBSERVATION"

                setOnClickListener {

                    ScreenCaptureService
                        .stopCapture(
                            this@MainActivity
                        )
                }
            }
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // VISION
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "VISION STATUS"
            )
        )

        gameText =
            textView(
                "Game: Waiting",
                15f,
                false
            )

        boardText =
            textView(
                "Board: NOT DETECTED",
                15f,
                false
            )

        occupiedText =
            textView(
                "Occupied Cells: --",
                15f,
                false
            )

        piecesText =
            textView(
                "Pieces Detected: -- / 3",
                15f,
                false
            )

        confidenceText =
            textView(
                "Confidence: --%",
                15f,
                false
            )

        content.addView(gameText)
        content.addView(boardText)
        content.addView(occupiedText)
        content.addView(piecesText)
        content.addView(confidenceText)

        content.addView(
            spacer(12)
        )

        // --------------------------------------------------------
        // PIECE SHAPES
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "DETECTED PIECE SHAPES"
            )
        )

        piece1Text =
            textView(
                "Piece 1: --",
                15f,
                true
            )

        piece2Text =
            textView(
                "Piece 2: --",
                15f,
                true
            )

        piece3Text =
            textView(
                "Piece 3: --",
                15f,
                true
            )

        content.addView(
            piece1Text
        )

        content.addView(
            piece2Text
        )

        content.addView(
            piece3Text
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // AI
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "AI DECISION"
            )
        )

        bestMoveText =
            textView(
                "BEST MOVE: WAITING",
                18f,
                true
            )

        bestPieceText =
            textView(
                "Piece: --",
                15f,
                false
            )

        bestTargetText =
            textView(
                "Target: --",
                15f,
                false
            )

        bestScoreText =
            textView(
                "Score: --",
                15f,
                false
            )

        bestReasonText =
            textView(
                "Reason: --",
                15f,
                false
            )

        content.addView(bestMoveText)
        content.addView(bestPieceText)
        content.addView(bestTargetText)
        content.addView(bestScoreText)
        content.addView(bestReasonText)

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // ENGINE
        // --------------------------------------------------------

        content.addView(
            sectionTitle(
                "PLAYER ENGINE"
            )
        )

        content.addView(
            textView(
                "Mode: Normal / Human-like",
                15f,
                false
            )
        )

        content.addView(
            textView(
                "Automatic Action: OFF",
                15f,
                true
            )
        )

        content.addView(
            textView(
                "Extreme Automation: Disabled",
                15f,
                false
            )
        )

        content.addView(
            spacer(20)
        )

        content.addView(
            Button(this).apply {

                text =
                    "STOP PLAYER"

                setOnClickListener {

                    AutoPlayerAccessibilityService
                        .stopPlayer()

                    ScreenCaptureService
                        .stopCapture(
                            this@MainActivity
                        )
                }
            }
        )

        scroll.addView(
            content
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    // ============================================================
    // TEXT
    // ============================================================

    private fun textView(
        text: String,
        size: Float,
        bold: Boolean
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                size

            setTextColor(
                Color.rgb(
                    30,
                    30,
                    30
                )
            )

            if (bold) {

                setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
                )
            }

            setPadding(
                0,
                7,
                0,
                7
            )
        }
    }

    private fun sectionTitle(
        title: String
    ): TextView {

        return textView(
            title,
            18f,
            true
        )
    }

    private fun spacer(
        height: Int
    ): View {

        return View(this).apply {

            layoutParams =
                LinearLayout.LayoutParams(
                    1,
                    height
                )
        }
    }

    // ============================================================
    // APPS
    // ============================================================

    private fun loadApps() {

        apps.clear()

        val intent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {

                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val activities =
            packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.MATCH_ALL
                )

        for (info in activities) {

            val appInfo =
                info.activityInfo
                    ?.applicationInfo
                    ?: continue

            if (
                appInfo.packageName ==
                packageName
            ) {
                continue
            }

            apps.add(
                AppEntry(
                    name =
                        appInfo
                            .loadLabel(
                                packageManager
                            )
                            .toString(),

                    packageName =
                        appInfo.packageName
                )
            )
        }

        apps.sortBy {
            it.name.lowercase()
        }

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                apps
            )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        appSpinner.adapter =
            adapter
    }

    // ============================================================
    // SAVED TARGET
    // ============================================================

    private fun loadSavedTarget() {

        val prefs =
            getSharedPreferences(
                PREFS,
                MODE_PRIVATE
            )

        selectedPackage =
            prefs.getString(
                "target_package",
                null
            )

        val index =
            apps.indexOfFirst {
                it.packageName ==
                        selectedPackage
            }

        if (index >= 0) {

            appSpinner.setSelection(
                index
            )
        }
    }

    // ============================================================
    // SAVE TARGET
    // ============================================================

    private fun saveSelectedTarget() {

        val position =
            appSpinner.selectedItemPosition

        if (
            position < 0 ||
            position >= apps.size
        ) {
            return
        }

        val selected =
            apps[position]

        selectedPackage =
            selected.packageName

        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putString(
                "target_package",
                selected.packageName
            )
            .putString(
                "target_name",
                selected.name
            )
            .apply()

        gameText.text =
            "Game: ${selected.name}"
    }

    // ============================================================
    // LAUNCH
    // ============================================================

    private fun launchSelectedGame() {

        val pkg =
            selectedPackage
                ?: getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
                )
                    .getString(
                        "target_package",
                        null
                    )

        if (
            pkg.isNullOrEmpty()
        ) {
            return
        }

        val launchIntent =
            packageManager
                .getLaunchIntentForPackage(
                    pkg
                )

        if (launchIntent != null) {

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(
                launchIntent
            )

            AutoPlayerAccessibilityService
                .startPlayer()
        }
    }

    // ============================================================
    // SCREEN CAPTURE
    // ============================================================

    private fun requestScreenCapture() {

        val manager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            SCREEN_CAPTURE_REQUEST
        )
    }

    @Deprecated("Deprecated Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode !=
            SCREEN_CAPTURE_REQUEST
        ) {
            return
        }

        if (
            resultCode != RESULT_OK ||
            data == null
        ) {
            return
        }

        ScreenCaptureService
            .startCapture(
                this,
                resultCode,
                data
            )
    }

    // ============================================================
    // STATUS
    // ============================================================

    private fun updateStatus() {

        statusText.text =
            if (
                isAccessibilityServiceEnabled()
            ) {
                "Accessibility Service: ENABLED"
            } else {
                "Accessibility Service: DISABLED"
            }

        observationText.text =
            if (
                ScreenCaptureService
                    .isCapturing()
            ) {
                "Screen Observation: RUNNING"
            } else {
                "Screen Observation: STOPPED"
            }

        updateVisionStatus()
    }

    // ============================================================
    // VISION UI
    // ============================================================

    private fun updateVisionStatus() {

        val prefs =
            getSharedPreferences(
                VISION_PREFS,
                MODE_PRIVATE
            )

        val available =
            prefs.getBoolean(
                "available",
                false
            )

        if (!available) {

            boardText.text =
                "Board: NOT DETECTED"

            occupiedText.text =
                "Occupied Cells: --"

            piecesText.text =
                "Pieces Detected: -- / 3"

            confidenceText.text =
                "Confidence: --%"

            piece1Text.text =
                "Piece 1: --"

            piece2Text.text =
                "Piece 2: --"

            piece3Text.text =
                "Piece 3: --"

            bestMoveText.text =
                "BEST MOVE: WAITING"

            bestPieceText.text =
                "Piece: --"

            bestTargetText.text =
                "Target: --"

            bestScoreText.text =
                "Score: --"

            bestReasonText.text =
                "Reason: --"

            return
        }

        val occupied =
            prefs.getInt(
                "occupied_cells",
                -1
            )

        val pieces =
            prefs.getInt(
                "pieces_detected",
                0
            )

        val confidence =
            prefs.getInt(
                "confidence",
                0
            )

        boardText.text =
            "Board: DETECTED"

        occupiedText.text =
            "Occupied Cells: $occupied"

        piecesText.text =
            "Pieces Detected: $pieces / 3"

        confidenceText.text =
            "Confidence: $confidence%"

        // --------------------------------------------------------
        // SHAPES
        // --------------------------------------------------------

        piece1Text.text =
            formatPiece(
                prefs,
                0
            )

        piece2Text.text =
            formatPiece(
                prefs,
                1
            )

        piece3Text.text =
            formatPiece(
                prefs,
                2
            )

        // --------------------------------------------------------
        // BEST MOVE
        // --------------------------------------------------------

        val availableMove =
            prefs.getBoolean(
                "best_move_available",
                false
            )

        if (!availableMove) {

            bestMoveText.text =
                "BEST MOVE: NO LEGAL MOVE"

            bestPieceText.text =
                "Piece: --"

            bestTargetText.text =
                "Target: --"

            bestScoreText.text =
                "Score: --"

            bestReasonText.text =
                "Reason: No legal placement"

            return
        }

        val piece =
            prefs.getInt(
                "best_move_piece",
                -1
            )

        val row =
            prefs.getInt(
                "best_move_row",
                -1
            )

        val column =
            prefs.getInt(
                "best_move_column",
                -1
            )

        val score =
            prefs.getFloat(
                "best_move_score",
                0f
            )

        val reason =
            prefs.getString(
                "best_move_reason",
                "--"
            )

        bestMoveText.text =
            "BEST MOVE: READY"

        bestPieceText.text =
            "Piece: ${piece + 1}"

        bestTargetText.text =
            "Target: Row $row, Column $column"

        bestScoreText.text =
            "Score: %.2f".format(
                score
            )

        bestReasonText.text =
            "Reason: $reason"
    }

    // ============================================================
    // FORMAT PIECE
    // ============================================================

    private fun formatPiece(
        prefs:
            android.content.SharedPreferences,
        index: Int
    ): String {

        val detected =
            prefs.getBoolean(
                "piece_${index}_detected",
                false
            )

        val shape =
            prefs.getString(
                "piece_${index}_shape",
                ""
            )
                ?: ""

        if (
            !detected ||
            shape.isBlank()
        ) {
            return "Piece ${index + 1}: NOT DETECTED"
        }

        val cells =
            shape.split(";")
                .mapNotNull {

                    val parts =
                        it.split(",")

                    if (
                        parts.size != 2
                    ) {
                        null
                    } else {

                        Pair(
                            parts[0].toIntOrNull()
                                ?: return@mapNotNull null,

                            parts[1].toIntOrNull()
                                ?: return@mapNotNull null
                        )
                    }
                }

        if (cells.isEmpty()) {
            return "Piece ${index + 1}: INVALID"
        }

        val maxRow =
            cells.maxOf {
                it.first
            }

        val maxColumn =
            cells.maxOf {
                it.second
            }

        val rows =
            maxRow + 1

        val columns =
            maxColumn + 1

        val grid =
            Array(rows) {
                CharArray(columns) {
                    '.'
                }
            }

        for ((row, column) in cells) {

            if (
                row in 0 until rows &&
                column in 0 until columns
            ) {
                grid[row][column] =
                    '■'
            }
        }

        val visual =
            grid.joinToString(
                separator = "\n"
            ) {
                String(it)
            }

        return "Piece ${index + 1}:\n$visual"
    }

    // ============================================================
    // ACCESSIBILITY
    // ============================================================

    private fun isAccessibilityServiceEnabled():
            Boolean {

        val expected =
            "$packageName/" +
                    AutoPlayerAccessibilityService
                        ::class.java.name

        val enabled =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure
                    .ENABLED_ACCESSIBILITY_SERVICES
            )
                ?: return false

        return enabled
            .split(":")
            .any {
                it.equals(
                    expected,
                    ignoreCase = true
                )
            }
    }

    // ============================================================
    // RESUME
    // ============================================================

    override fun onResume() {

        super.onResume()

        updateStatus()

        window.decorView.post(
            refreshRunnable
        )
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        window.decorView.removeCallbacks(
            refreshRunnable
        )

        super.onDestroy()
    }
}
