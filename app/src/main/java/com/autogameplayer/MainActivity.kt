package com.autogameplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val SCREEN_CAPTURE_REQUEST = 5001
    }

    private lateinit var serviceStatus: TextView
    private lateinit var observationStatus: TextView
    private lateinit var selectedGameText: TextView
    private lateinit var gameSpinner: Spinner

    private lateinit var visionGameStatus: TextView
    private lateinit var visionBoardStatus: TextView
    private lateinit var visionOccupiedStatus: TextView
    private lateinit var visionPiecesStatus: TextView
    private lateinit var visionConfidenceStatus: TextView
    private lateinit var visionActionStatus: TextView

    private val preferences by lazy {
        getSharedPreferences(
            "player_settings",
            MODE_PRIVATE
        )
    }

    private val visionPreferences by lazy {
        getSharedPreferences(
            "vision_status",
            MODE_PRIVATE
        )
    }

    private var apps = emptyList<AppInfo>()

    private val visionHandler =
        Handler(Looper.getMainLooper())

    private val visionUpdater =
        object : Runnable {

            override fun run() {

                updateVisionStatus()

                visionHandler.postDelayed(
                    this,
                    1000L
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        createInterface()

        loadLaunchableApps()
        updateServiceStatus()
        updateSelectedGame()
        updateObservationStatus()
        updateVisionStatus()
    }

    override fun onResume() {
        super.onResume()

        updateServiceStatus()
        updateSelectedGame()
        updateObservationStatus()
        updateVisionStatus()

        visionHandler.removeCallbacks(
            visionUpdater
        )

        visionHandler.post(
            visionUpdater
        )
    }

    override fun onPause() {

        visionHandler.removeCallbacks(
            visionUpdater
        )

        super.onPause()
    }

    override fun onDestroy() {

        visionHandler.removeCallbacks(
            visionUpdater
        )

        super.onDestroy()
    }

    // =============================================================
    // USER INTERFACE
    // =============================================================

    private fun createInterface() {

        val scrollView =
            ScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.TOP

                setPadding(
                    32,
                    40,
                    32,
                    40
                )
            }

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        val title =
            TextView(this).apply {

                text =
                    "AUTO GAME PLAYER"

                textSize = 28f

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    15,
                    0,
                    10
                )
            }

        val subtitle =
            TextView(this).apply {

                text =
                    "Universal AI Game Player"

                textSize = 19f

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    25
                )
            }

        val description =
            TextView(this).apply {

                text =
                    "Select any launchable Android game or app. " +
                    "The player will observe the selected screen " +
                    "and later use the AI decision engine."

                textSize = 16f

                setPadding(
                    0,
                    0,
                    0,
                    25
                )
            }

        // ---------------------------------------------------------
        // ACCESSIBILITY
        // ---------------------------------------------------------

        serviceStatus =
            TextView(this).apply {

                textSize = 17f

                setPadding(
                    0,
                    15,
                    0,
                    15
                )
            }

        val accessibilityButton =
            Button(this).apply {

                text =
                    "ENABLE GAME CONTROL"

                setOnClickListener {

                    try {

                        startActivity(
                            Intent(
                                Settings
                                    .ACTION_ACCESSIBILITY_SETTINGS
                            )
                        )

                    } catch (_: Exception) {

                        startActivity(
                            Intent(
                                Settings.ACTION_SETTINGS
                            )
                        )
                    }
                }
            }

        val refreshButton =
            Button(this).apply {

                text =
                    "REFRESH APPS & STATUS"

                setOnClickListener {

                    loadLaunchableApps()

                    updateServiceStatus()
                    updateSelectedGame()
                    updateObservationStatus()
                    updateVisionStatus()
                }
            }

        // ---------------------------------------------------------
        // TARGET GAME
        // ---------------------------------------------------------

        val gameTitle =
            TextView(this).apply {

                text =
                    "SELECT TARGET GAME / APP"

                textSize = 20f

                setPadding(
                    0,
                    30,
                    0,
                    10
                )
            }

        gameSpinner =
            Spinner(this)

        selectedGameText =
            TextView(this).apply {

                textSize = 15f

                setPadding(
                    0,
                    15,
                    0,
                    15
                )
            }

        val saveGameButton =
            Button(this).apply {

                text =
                    "SAVE SELECTED GAME"

                setOnClickListener {

                    val position =
                        gameSpinner
                            .selectedItemPosition

                    if (
                        position >= 0 &&
                        position < apps.size
                    ) {

                        val selected =
                            apps[position]

                        preferences.edit()
                            .putString(
                                "target_package",
                                selected.packageName
                            )
                            .putString(
                                "target_name",
                                selected.name
                            )
                            .apply()

                        clearVisionStatus()

                        updateSelectedGame()
                        updateVisionStatus()
                    }
                }
            }

        val launchGameButton =
            Button(this).apply {

                text =
                    "OPEN SELECTED GAME"

                setOnClickListener {

                    openSelectedApp()
                }
            }

        // ---------------------------------------------------------
        // SCREEN OBSERVATION
        // ---------------------------------------------------------

        val observationTitle =
            TextView(this).apply {

                text =
                    "SCREEN OBSERVATION"

                textSize = 20f

                setPadding(
                    0,
                    30,
                    0,
                    10
                )
            }

        observationStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    10,
                    0,
                    15
                )
            }

        val startObservationButton =
            Button(this).apply {

                text =
                    "START SCREEN OBSERVATION"

                setOnClickListener {

                    startScreenCapture()
                }
            }

        val stopObservationButton =
            Button(this).apply {

                text =
                    "STOP SCREEN OBSERVATION"

                setOnClickListener {

                    ScreenCaptureService
                        .stopCapture(
                            this@MainActivity
                        )

                    clearVisionStatus()

                    updateObservationStatus()
                    updateVisionStatus()
                }
            }

        // ---------------------------------------------------------
        // VISION STATUS
        // ---------------------------------------------------------

        val visionTitle =
            TextView(this).apply {

                text =
                    "VISION STATUS"

                textSize = 20f

                setPadding(
                    0,
                    30,
                    0,
                    10
                )
            }

        visionGameStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    6,
                    0,
                    6
                )
            }

        visionBoardStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    6,
                    0,
                    6
                )
            }

        visionOccupiedStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    6,
                    0,
                    6
                )
            }

        visionPiecesStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    6,
                    0,
                    6
                )
            }

        visionConfidenceStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    6,
                    0,
                    6
                )
            }

        visionActionStatus =
            TextView(this).apply {

                textSize = 16f

                setPadding(
                    0,
                    12,
                    0,
                    15
                )
            }

        // ---------------------------------------------------------
        // PLAYER ENGINE
        // ---------------------------------------------------------

        val engineTitle =
            TextView(this).apply {

                text =
                    "PLAYER ENGINE"

                textSize = 20f

                setPadding(
                    0,
                    30,
                    0,
                    10
                )
            }

        val engineStatus =
            TextView(this).apply {

                text =
                    """
                    
Current Mode:
Normal / Human-like

Extreme Automation:
Disabled

Decision Pipeline:

Screen
   ↓
Observation
   ↓
Game State
   ↓
AI Decision
   ↓
Human-like Action
   ↓
Verification
                    """.trimIndent()

                textSize = 16f

                setPadding(
                    0,
                    10,
                    0,
                    20
                )
            }

        // ---------------------------------------------------------
        // STOP
        // ---------------------------------------------------------

        val stopPlayerButton =
            Button(this).apply {

                text =
                    "STOP PLAYER"

                setOnClickListener {

                    AutoPlayerAccessibilityService
                        .stopPlayer(
                            this@MainActivity
                        )

                    ScreenCaptureService
                        .stopCapture(
                            this@MainActivity
                        )

                    clearVisionStatus()

                    updateObservationStatus()
                    updateVisionStatus()
                }
            }

        // ---------------------------------------------------------
        // ADD VIEWS
        // ---------------------------------------------------------

        content.addView(title)
        content.addView(subtitle)
        content.addView(description)

        content.addView(serviceStatus)
        content.addView(accessibilityButton)
        content.addView(refreshButton)

        content.addView(gameTitle)
        content.addView(gameSpinner)
        content.addView(selectedGameText)
        content.addView(saveGameButton)
        content.addView(launchGameButton)

        content.addView(observationTitle)
        content.addView(observationStatus)
        content.addView(startObservationButton)
        content.addView(stopObservationButton)

        content.addView(visionTitle)
        content.addView(visionGameStatus)
        content.addView(visionBoardStatus)
        content.addView(visionOccupiedStatus)
        content.addView(visionPiecesStatus)
        content.addView(visionConfidenceStatus)
        content.addView(visionActionStatus)

        content.addView(engineTitle)
        content.addView(engineStatus)
        content.addView(stopPlayerButton)

        scrollView.addView(content)

        setContentView(scrollView)
    }

    // =============================================================
    // VISION STATUS
    // =============================================================

    private fun updateVisionStatus() {

        val targetName =
            preferences.getString(
                "target_name",
                null
            )

        val available =
            visionPreferences.getBoolean(
                "available",
                false
            )

        val occupied =
            visionPreferences.getInt(
                "occupied_cells",
                -1
            )

        val pieces =
            visionPreferences.getInt(
                "pieces_detected",
                -1
            )

        val confidence =
            visionPreferences.getInt(
                "confidence",
                0
            )

        visionGameStatus.text =
            "Game: ${targetName ?: "None"}"

        visionBoardStatus.text =
            "Board: " +
                    if (available) {
                        "DETECTED"
                    } else if (
                        ScreenCaptureService
                            .isCapturing()
                    ) {
                        "ANALYZING"
                    } else {
                        "WAITING"
                    }

        visionOccupiedStatus.text =
            "Occupied Cells: " +
                    if (occupied >= 0) {
                        occupied.toString()
                    } else {
                        "--"
                    }

        visionPiecesStatus.text =
            "Pieces Detected: " +
                    if (pieces >= 0) {
                        "$pieces / 3"
                    } else {
                        "-- / 3"
                    }

        visionConfidenceStatus.text =
            "Confidence: " +
                    if (available) {
                        "$confidence%"
                    } else {
                        "--%"
                    }

        visionActionStatus.text =
            "Automatic Action: OFF\n" +
                    "Mode: Observation Only"
    }

    private fun clearVisionStatus() {

        visionPreferences.edit()
            .clear()
            .apply()
    }

    // =============================================================
    // LOAD APPS
    // =============================================================

    private fun loadLaunchableApps() {

        val launcherIntent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {

                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val activities =
            packageManager.queryIntentActivities(
                launcherIntent,
                0
            )

        apps =
            activities
                .mapNotNull { resolveInfo ->

                    val activityInfo =
                        resolveInfo.activityInfo
                            ?: return@mapNotNull null

                    val appPackage =
                        activityInfo.packageName

                    if (
                        appPackage ==
                        packageNameOfThisApp()
                    ) {
                        return@mapNotNull null
                    }

                    val applicationInfo =
                        activityInfo.applicationInfo
                            ?: return@mapNotNull null

                    val label =
                        applicationInfo
                            .loadLabel(
                                packageManager
                            )
                            ?.toString()
                            ?.trim()

                    if (
                        label.isNullOrEmpty()
                    ) {
                        return@mapNotNull null
                    }

                    AppInfo(
                        name = label,
                        packageName = appPackage
                    )
                }
                .distinctBy {
                    it.packageName
                }
                .sortedBy {
                    it.name.lowercase()
                }

        updateSpinner()
    }

    // =============================================================
    // SPINNER
    // =============================================================

    private fun updateSpinner() {

        val names =
            if (apps.isEmpty()) {

                listOf(
                    "No launchable apps found"
                )

            } else {

                apps.map {

                    "${it.name}  •  " +
                            it.packageName
                }
            }

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                names
            )

        adapter.setDropDownViewResource(
            android.R.layout
                .simple_spinner_dropdown_item
        )

        gameSpinner.adapter =
            adapter

        val savedPackage =
            preferences.getString(
                "target_package",
                null
            )

        if (
            savedPackage != null
        ) {

            val index =
                apps.indexOfFirst {

                    it.packageName ==
                            savedPackage
                }

            if (index >= 0) {

                gameSpinner.setSelection(
                    index
                )
            }
        }

        gameSpinner.onItemSelectedListener =
            object :
                AdapterView
                    .OnItemSelectedListener {

                override fun onItemSelected(
                    parent:
                        AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (
                        position >= 0 &&
                        position < apps.size
                    ) {

                        val selected =
                            apps[position]

                        selectedGameText.text =
                            "Selected Game / App:\n" +
                                    "${selected.name}\n" +
                                    selected.packageName
                    }
                }

                override fun onNothingSelected(
                    parent:
                        AdapterView<*>?
                ) {
                }
            }
    }

    // =============================================================
    // OPEN APP
    // =============================================================

    private fun openSelectedApp() {

        val targetPackage =
            preferences.getString(
                "target_package",
                null
            )

        if (
            targetPackage.isNullOrEmpty()
        ) {

            selectedGameText.text =
                "Please select and save a game first."

            return
        }

        try {

            val launchIntent =
                packageManager
                    .getLaunchIntentForPackage(
                        targetPackage
                    )

            if (
                launchIntent != null
            ) {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                startActivity(
                    launchIntent
                )

            } else {

                selectedGameText.text =
                    "Unable to launch selected app."
            }

        } catch (e: Exception) {

            selectedGameText.text =
                "Launch error:\n${e.message}"
        }
    }

    // =============================================================
    // SCREEN CAPTURE
    // =============================================================

    private fun startScreenCapture() {

        val targetPackage =
            preferences.getString(
                "target_package",
                null
            )

        if (
            targetPackage.isNullOrEmpty()
        ) {

            observationStatus.text =
                "Please select and save a target game first."

            return
        }

        clearVisionStatus()

        val projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val intent =
            projectionManager
                .createScreenCaptureIntent()

        startActivityForResult(
            intent,
            SCREEN_CAPTURE_REQUEST
        )
    }

    @Deprecated(
        "Using Activity Result API is not required at this stage."
    )
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

            observationStatus.text =
                "Screen capture permission was not granted."

            return
        }

        ScreenCaptureService.startCapture(
            this,
            resultCode,
            data
        )

        observationStatus.text =
            "Screen Observation: STARTING..."

        updateVisionStatus()
    }

    // =============================================================
    // ACCESSIBILITY
    // =============================================================

    private fun updateServiceStatus() {

        serviceStatus.text =
            if (
                isAccessibilityServiceEnabled()
            ) {

                "● Game Control: ENABLED"

            } else {

                "○ Game Control: NOT ENABLED"
            }
    }

    private fun isAccessibilityServiceEnabled():
            Boolean {

        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure
                    .ENABLED_ACCESSIBILITY_SERVICES
            )
                ?: return false

        val expected =
            ComponentName(
                this,
                AutoPlayerAccessibilityService::class.java
            )

        val expectedName =
            expected.flattenToString()

        return enabledServices
            .split(':')
            .any {

                it.equals(
                    expectedName,
                    ignoreCase = true
                )
            }
    }

    // =============================================================
    // SELECTED GAME
    // =============================================================

    private fun updateSelectedGame() {

        val name =
            preferences.getString(
                "target_name",
                null
            )

        val targetPackage =
            preferences.getString(
                "target_package",
                null
            )

        selectedGameText.text =
            if (
                name != null &&
                targetPackage != null
            ) {

                "Selected Game / App:\n" +
                        "$name\n" +
                        targetPackage

            } else {

                "Selected Game / App:\nNone"
            }
    }

    // =============================================================
    // OBSERVATION
    // =============================================================

    private fun updateObservationStatus() {

        observationStatus.text =
            if (
                ScreenCaptureService
                    .isCapturing()
            ) {

                "● Screen Observation: RUNNING"

            } else {

                "○ Screen Observation: STOPPED"
            }
    }

    private fun packageNameOfThisApp():
            String {

        return packageName
    }

    // =============================================================
    // DATA MODEL
    // =============================================================

    data class AppInfo(
        val name: String,
        val packageName: String
    )
}
