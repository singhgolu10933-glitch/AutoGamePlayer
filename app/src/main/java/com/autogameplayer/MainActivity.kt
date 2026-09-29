package com.autogameplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.projection.MediaProjectionManager
import android.os.Bundle
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

    private val preferences by lazy {
        getSharedPreferences(
            "player_settings",
            MODE_PRIVATE
        )
    }

    private var games = emptyList<GameInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        loadInstalledGames()
        updateServiceStatus()
        updateSelectedGame()
        updateObservationStatus()
    }

    override fun onResume() {
        super.onResume()

        updateServiceStatus()
        updateSelectedGame()
        updateObservationStatus()
    }

    private fun createInterface() {

        val scrollView = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(32, 40, 32, 40)
        }

        val title = TextView(this).apply {
            text = "AUTO GAME PLAYER"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 10)
        }

        val subtitle = TextView(this).apply {
            text = "Universal AI Game Player"
            textSize = 19f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 25)
        }

        val description = TextView(this).apply {
            text =
                "Select a target Android game. " +
                "The player first observes the game screen, " +
                "then the AI engine will make normal human-like decisions."
            textSize = 16f
            setPadding(0, 0, 0, 25)
        }

        serviceStatus = TextView(this).apply {
            textSize = 17f
            setPadding(0, 15, 0, 15)
        }

        val accessibilityButton = Button(this).apply {
            text = "ENABLE GAME CONTROL"

            setOnClickListener {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                    )
                } catch (_: Exception) {
                    startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                    )
                }
            }
        }

        val refreshButton = Button(this).apply {
            text = "REFRESH STATUS"

            setOnClickListener {
                updateServiceStatus()
                updateSelectedGame()
                updateObservationStatus()
                loadInstalledGames()
            }
        }

        val gameTitle = TextView(this).apply {
            text = "SELECT TARGET GAME"
            textSize = 20f
            setPadding(0, 30, 0, 10)
        }

        gameSpinner = Spinner(this)

        selectedGameText = TextView(this).apply {
            textSize = 15f
            setPadding(0, 15, 0, 15)
        }

        val saveGameButton = Button(this).apply {
            text = "SAVE SELECTED GAME"

            setOnClickListener {

                val position =
                    gameSpinner.selectedItemPosition

                if (position >= 0 &&
                    position < games.size
                ) {

                    val game =
                        games[position]

                    preferences.edit()
                        .putString(
                            "target_package",
                            game.packageName
                        )
                        .putString(
                            "target_name",
                            game.name
                        )
                        .apply()

                    updateSelectedGame()
                }
            }
        }

        val observationTitle = TextView(this).apply {
            text = "SCREEN OBSERVATION"
            textSize = 20f
            setPadding(0, 30, 0, 10)
        }

        observationStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, 10, 0, 15)
        }

        val startObservationButton = Button(this).apply {
            text = "START SCREEN OBSERVATION"

            setOnClickListener {
                startScreenCapture()
            }
        }

        val stopObservationButton = Button(this).apply {
            text = "STOP SCREEN OBSERVATION"

            setOnClickListener {
                ScreenCaptureService.stopCapture(this@MainActivity)
                updateObservationStatus()
            }
        }

        val engineStatus = TextView(this).apply {
            text =
                """
                
PLAYER ENGINE

Current mode:
Normal / Human-like

Extreme automation:
Disabled

Pipeline:

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
            setPadding(0, 25, 0, 20)
        }

        val stopPlayerButton = Button(this).apply {
            text = "STOP PLAYER"

            setOnClickListener {
                AutoPlayerAccessibilityService.stopPlayer(
                    this@MainActivity
                )

                ScreenCaptureService.stopCapture(
                    this@MainActivity
                )

                updateObservationStatus()
            }
        }

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

        content.addView(observationTitle)
        content.addView(observationStatus)
        content.addView(startObservationButton)
        content.addView(stopObservationButton)

        content.addView(engineStatus)
        content.addView(stopPlayerButton)

        scrollView.addView(content)

        setContentView(scrollView)
    }

    private fun startScreenCapture() {

        val targetPackage =
            preferences.getString(
                "target_package",
                null
            )

        if (targetPackage.isNullOrEmpty()) {
            observationStatus.text =
                "Please select and save a target game first."

            return
        }

        val projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val intent =
            projectionManager.createScreenCaptureIntent()

        startActivityForResult(
            intent,
            SCREEN_CAPTURE_REQUEST
        )
    }

    @Deprecated("Activity result API is not required for this project stage.")
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

        if (requestCode != SCREEN_CAPTURE_REQUEST) {
            return
        }

        if (resultCode != RESULT_OK ||
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
    }

    private fun loadInstalledGames() {

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val activities =
            packageManager.queryIntentActivities(
                launcherIntent,
                0
            )

        games = activities
            .mapNotNull { resolveInfo ->

                val activityInfo =
                    resolveInfo.activityInfo
                        ?: return@mapNotNull null

                val packageName =
                    activityInfo.packageName

                if (packageName ==
                    packageNameOfThisApp()
                ) {
                    return@mapNotNull null
                }

                val appInfo =
                    activityInfo.applicationInfo
                        ?: return@mapNotNull null

                /*
                 * Prefer user-installed applications.
                 * This removes most system utilities such as
                 * My Files from the target list.
                 */

                val isSystemApp =
                    (appInfo.flags and
                            ApplicationInfo.FLAG_SYSTEM) != 0

                if (isSystemApp) {
                    return@mapNotNull null
                }

                val label =
                    appInfo.loadLabel(
                        packageManager
                    )
                        ?.toString()
                        ?.trim()

                if (label.isNullOrEmpty()) {
                    return@mapNotNull null
                }

                GameInfo(
                    name = label,
                    packageName = packageName
                )
            }
            .distinctBy {
                it.packageName
            }
            .sortedBy {
                it.name.lowercase()
            }

        val names =
            if (games.isEmpty()) {

                listOf(
                    "No user-installed apps found"
                )

            } else {

                games.map {
                    "${it.name} • ${it.packageName}"
                }
            }

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                names
            )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        gameSpinner.adapter = adapter

        val savedPackage =
            preferences.getString(
                "target_package",
                null
            )

        if (savedPackage != null) {

            val index =
                games.indexOfFirst {
                    it.packageName ==
                            savedPackage
                }

            if (index >= 0) {
                gameSpinner.setSelection(index)
            }
        }

        gameSpinner.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (position >= 0 &&
                        position < games.size
                    ) {

                        val game =
                            games[position]

                        selectedGameText.text =
                            "Selected Game:\n" +
                                    "${game.name}\n" +
                                    game.packageName
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }

    private fun updateServiceStatus() {

        serviceStatus.text =
            if (isAccessibilityServiceEnabled()) {

                "● Game Control: ENABLED"

            } else {

                "○ Game Control: NOT ENABLED"
            }
    }

    private fun updateObservationStatus() {

        observationStatus.text =
            if (ScreenCaptureService.isCapturing()) {

                "● Screen Observation: RUNNING"

            } else {

                "○ Screen Observation: STOPPED"
            }
    }

    private fun updateSelectedGame() {

        val name =
            preferences.getString(
                "target_name",
                null
            )

        val packageName =
            preferences.getString(
                "target_package",
                null
            )

        selectedGameText.text =
            if (name != null &&
                packageName != null
            ) {

                "Selected Game:\n" +
                        "$name\n" +
                        packageName

            } else {

                "Selected Game:\nNone"
            }
    }

    private fun isAccessibilityServiceEnabled():
            Boolean {

        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
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

    private fun packageNameOfThisApp():
            String {

        return packageName
    }

    data class GameInfo(
        val name: String,
        val packageName: String
    )
}
