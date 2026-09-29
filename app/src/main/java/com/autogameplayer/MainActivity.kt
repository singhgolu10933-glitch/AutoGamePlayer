package com.autogameplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
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

    private lateinit var serviceStatus: TextView
    private lateinit var selectedGameText: TextView
    private lateinit var gameSpinner: Spinner

    private val preferences by lazy {
        getSharedPreferences("player_settings", MODE_PRIVATE)
    }

    private var games = emptyList<GameInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        loadInstalledGames()
        updateServiceStatus()
        updateSelectedGame()
    }

    override fun onResume() {
        super.onResume()

        updateServiceStatus()
        updateSelectedGame()
    }

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
            gravity = Gravity.TOP
        }

        val scrollView = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }

        val title = TextView(this).apply {
            text = "AUTO GAME PLAYER"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        val subtitle = TextView(this).apply {
            text = "Universal AI Game Player"
            textSize = 19f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 25)
        }

        val description = TextView(this).apply {
            text =
                "Select an installed Android game. " +
                "The player engine will observe the selected game " +
                "and later make normal human-like decisions."
            textSize = 16f
            setPadding(0, 0, 0, 25)
        }

        serviceStatus = TextView(this).apply {
            textSize = 17f
            setPadding(0, 15, 0, 15)
        }

        val enableButton = Button(this).apply {
            text = "ENABLE GAME CONTROL"
            setOnClickListener {
                try {
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
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
                val position = gameSpinner.selectedItemPosition

                if (position >= 0 && position < games.size) {
                    val game = games[position]

                    preferences.edit()
                        .putString("target_package", game.packageName)
                        .putString("target_name", game.name)
                        .apply()

                    updateSelectedGame()
                }
            }
        }

        val engineStatus = TextView(this).apply {
            text =
                """
                
PLAYER ENGINE

Status:
Waiting for Accessibility Service

Mode:
Normal / Human-like

Extreme automation:
Disabled

Next:
Screen Observation → AI Decision → Safe Gesture
                """.trimIndent()

            textSize = 16f
            setPadding(0, 25, 0, 20)
        }

        val stopButton = Button(this).apply {
            text = "STOP PLAYER"
            setOnClickListener {
                AutoPlayerAccessibilityService.stopPlayer(this@MainActivity)
            }
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(description)
        content.addView(serviceStatus)
        content.addView(enableButton)
        content.addView(refreshButton)
        content.addView(gameTitle)
        content.addView(gameSpinner)
        content.addView(selectedGameText)
        content.addView(saveGameButton)
        content.addView(engineStatus)
        content.addView(stopButton)

        scrollView.addView(content)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    private fun loadInstalledGames() {

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = packageManager.queryIntentActivities(
            launcherIntent,
            0
        )

        games = activities
            .mapNotNull { resolveInfo ->

                val activityInfo = resolveInfo.activityInfo
                    ?: return@mapNotNull null

                val packageName = activityInfo.packageName

                if (packageName == packageNameOfThisApp()) {
                    return@mapNotNull null
                }

                val applicationInfo = activityInfo.applicationInfo
                    ?: return@mapNotNull null

                val label = applicationInfo
                    .loadLabel(packageManager)
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
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }

        val names = mutableListOf<String>()

        if (games.isEmpty()) {
            names.add("No launchable apps found")
        } else {
            games.forEach {
                names.add("${it.name}  •  ${it.packageName}")
            }
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            names
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        gameSpinner.adapter = adapter

        val savedPackage =
            preferences.getString("target_package", null)

        if (savedPackage != null) {
            val index = games.indexOfFirst {
                it.packageName == savedPackage
            }

            if (index >= 0) {
                gameSpinner.setSelection(index)
            }
        }

        gameSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position >= 0 && position < games.size) {
                        val game = games[position]

                        selectedGameText.text =
                            "Selected:\n${game.name}\n${game.packageName}"
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }

    private fun updateServiceStatus() {

        val enabled = isAccessibilityServiceEnabled()

        if (enabled) {
            serviceStatus.text =
                "● Game Control: ENABLED"
        } else {
            serviceStatus.text =
                "○ Game Control: NOT ENABLED\n\n" +
                "Tap ENABLE GAME CONTROL and enable Auto Game Player."
        }
    }

    private fun updateSelectedGame() {

        val name =
            preferences.getString("target_name", null)

        val packageName =
            preferences.getString("target_package", null)

        if (name != null && packageName != null) {
            selectedGameText.text =
                "Selected Game:\n$name\n$packageName"
        } else {
            selectedGameText.text =
                "Selected Game:\nNone"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expected = ComponentName(
            this,
            AutoPlayerAccessibilityService::class.java
        )

        val expectedName = expected.flattenToString()

        return enabledServices
            .split(':')
            .any {
                it.equals(
                    expectedName,
                    ignoreCase = true
                )
            }
    }

    private fun packageNameOfThisApp(): String {
        return packageName
    }

    data class GameInfo(
        val name: String,
        val packageName: String
    )
}
