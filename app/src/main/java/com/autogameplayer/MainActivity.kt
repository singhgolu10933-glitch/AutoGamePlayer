package com.autogameplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class MainActivity : Activity() {

    companion object {
        private const val SCREEN_CAPTURE_REQUEST = 1001
        private const val PREFS = "player_settings"
        private const val VISION_PREFS = "vision_status"
    }

    private lateinit var statusText: TextView
    private lateinit var observationText: TextView

    private lateinit var gameText: TextView
    private lateinit var boardText: TextView
    private lateinit var occupiedText: TextView
    private lateinit var piecesText: TextView
    private lateinit var confidenceText: TextView

    private lateinit var bestMoveText: TextView
    private lateinit var bestPieceText: TextView
    private lateinit var bestTargetText: TextView
    private lateinit var bestScoreText: TextView
    private lateinit var bestReasonText: TextView

    private lateinit var appSpinner: Spinner

    private val apps =
        mutableListOf<AppEntry>()

    private var selectedPackage: String? = null

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

    data class AppEntry(
        val name: String,
        val packageName: String
    ) {
        override fun toString(): String {
            return "$name • $packageName"
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

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

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

        val accessibilityButton =
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

        content.addView(
            accessibilityButton
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // TARGET GAME
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

        val saveTargetButton =
            Button(this).apply {

                text =
                    "SELECT TARGET GAME"

                setOnClickListener {

                    saveSelectedTarget()

                }
            }

        content.addView(
            saveTargetButton
        )

        val launchButton =
            Button(this).apply {

                text =
                    "OPEN SELECTED GAME"

                setOnClickListener {

                    launchSelectedGame()

                }
            }

        content.addView(
            launchButton
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // SCREEN OBSERVATION
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

        val startCaptureButton =
            Button(this).apply {

                text =
                    "START SCREEN OBSERVATION"

                setOnClickListener {

                    requestScreenCapture()

                }
            }

        content.addView(
            startCaptureButton
        )

        val stopCaptureButton =
            Button(this).apply {

                text =
                    "STOP SCREEN OBSERVATION"

                setOnClickListener {

                    ScreenCaptureService
                        .stopCapture(
                            this@MainActivity
                        )

                    updateStatus()
                }
            }

        content.addView(
            stopCaptureButton
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

        content.addView(
            gameText
        )

        content.addView(
            boardText
        )

        content.addView(
            occupiedText
        )

        content.addView(
            piecesText
        )

        content.addView(
            confidenceText
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // AI DECISION
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
                "Target: Row --, Column --",
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

        content.addView(
            bestMoveText
        )

        content.addView(
            bestPieceText
        )

        content.addView(
            bestTargetText
        )

        content.addView(
            bestScoreText
        )

        content.addView(
            bestReasonText
        )

        content.addView(
            spacer(18)
        )

        // --------------------------------------------------------
        // PLAYER ENGINE
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

        // --------------------------------------------------------
        // STOP
        // --------------------------------------------------------

        val stopButton =
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

                    updateStatus()
                }
            }

        content.addView(
            stopButton
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
    // TEXT VIEW
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
    // LOAD APPS
    // ============================================================

    private fun loadApps() {

        apps.clear()

        val packageManager =
            packageManager

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

        val currentPackage =
            packageName

        for (info in activities) {

            val appInfo =
                info.activityInfo
                    ?.applicationInfo
                    ?: continue

            val pkg =
                appInfo.packageName

            if (
                pkg == currentPackage
            ) {
                continue
            }

            val name =
                appInfo.loadLabel(
                    packageManager
                )
                    .toString()

            apps.add(
                AppEntry(
                    name = name,
                    packageName = pkg
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

        if (
            selectedPackage == null
        ) {
            return
        }

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

        statusText.text =
            "Accessibility Service: Target selected"
    }

    // ============================================================
    // LAUNCH GAME
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
    // SCREEN CAPTURE REQUEST
    // ============================================================

    private fun requestScreenCapture() {

        val manager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val intent =
            manager.createScreenCaptureIntent()

        startActivityForResult(
            intent,
            SCREEN_CAPTURE_REQUEST
        )
    }

    // ============================================================
    // CAPTURE RESULT
    // ============================================================

    @Deprecated("Deprecated in Android API")
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

            observationText.text =
                "Screen Observation: Permission denied"

            return
        }

        ScreenCaptureService
            .startCapture(
                this,
                resultCode,
                data
            )

        observationText.text =
            "Screen Observation: STARTING..."
    }

    // ============================================================
    // STATUS UPDATE
    // ============================================================

    private fun updateStatus() {

        val accessibilityEnabled =
            isAccessibilityServiceEnabled()

        statusText.text =
            if (accessibilityEnabled) {

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
    // VISION STATUS
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

            bestMoveText.text =
                "BEST MOVE: WAITING"

            bestPieceText.text =
                "Piece: --"

            bestTargetText.text =
                "Target: Row --, Column --"

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
            if (occupied >= 0) {

                "Board: DETECTED"

            } else {

                "Board: NOT DETECTED"
            }

        occupiedText.text =
            "Occupied Cells: $occupied"

        piecesText.text =
            "Pieces Detected: $pieces / 3"

        confidenceText.text =
            "Confidence: $confidence%"

        // --------------------------------------------------------
        // BEST MOVE
        // --------------------------------------------------------

        val bestAvailable =
            prefs.getBoolean(
                "best_move_available",
                false
            )

        if (!bestAvailable) {

            bestMoveText.text =
                "BEST MOVE: NO LEGAL MOVE"

            bestPieceText.text =
                "Piece: --"

            bestTargetText.text =
                "Target: --"

            bestScoreText.text =
                "Score: --"

            bestReasonText.text =
                "Reason: No valid placement detected"

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
    // ACCESSIBILITY CHECK
    // ============================================================

    private fun isAccessibilityServiceEnabled():
            Boolean {

        val expectedComponent =
            "$packageName/" +
                    AutoPlayerAccessibilityService::class.java.name

        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
                ?: return false

        return enabledServices
            .split(':')
            .any {
                it.equals(
                    expectedComponent,
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
