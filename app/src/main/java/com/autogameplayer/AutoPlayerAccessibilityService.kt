package com.autogameplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoPlayerAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "AutoGamePlayer"

        private var instance: AutoPlayerAccessibilityService? = null

        private var playerRunning = false

        fun stopPlayer(service: MainActivity? = null) {
            playerRunning = false
            instance?.logMessage("Player stopped")
        }

        fun isRunning(): Boolean {
            return playerRunning
        }

        fun startPlayer() {
            playerRunning = true
            instance?.logMessage("Player started")
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var currentPackage: String? = null

    private var lastEventTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this

        logMessage(
            "Accessibility Service connected"
        )
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (event == null) {
            return
        }

        currentPackage =
            event.packageName?.toString()

        val targetPackage =
            getSharedPreferences(
                "player_settings",
                MODE_PRIVATE
            ).getString(
                "target_package",
                null
            )

        if (targetPackage == null) {
            return
        }

        if (currentPackage != targetPackage) {
            return
        }

        lastEventTime =
            System.currentTimeMillis()

        /*
         * IMPORTANT:
         *
         * This is the observation layer.
         *
         * The actual AI decision engine will be connected
         * after screen observation is working.
         */

        when (event.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {

                observeGame()
            }
        }
    }

    private fun observeGame() {

        val root: AccessibilityNodeInfo? =
            rootInActiveWindow

        if (root == null) {
            return
        }

        val packageName =
            root.packageName?.toString()

        if (packageName == null) {
            root.recycle()
            return
        }

        /*
         * Some games expose useful UI nodes.
         * Many games use OpenGL/Canvas and expose
         * little or no accessibility tree.
         *
         * For those games the next layer will use
         * user-approved screen capture.
         */

        val nodeCount =
            countNodes(root)

        Log.d(
            TAG,
            "Game observed: package=$packageName nodes=$nodeCount"
        )

        root.recycle()
    }

    private fun countNodes(
        node: AccessibilityNodeInfo?
    ): Int {

        if (node == null) {
            return 0
        }

        var count = 1

        for (index in 0 until node.childCount) {

            val child = node.getChild(index)

            count += countNodes(child)

            child?.recycle()
        }

        return count
    }

    /**
     * Safe gesture method.
     *
     * This method is intentionally not automatically called yet.
     * AI decision + verification will control it later.
     */
    fun performTap(
        x: Float,
        y: Float,
        durationMs: Long = 80L
    ): Boolean {

        if (!isServiceReady()) {
            return false
        }

        val path = Path()

        path.moveTo(x, y)

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        durationMs
                    )
                )
                .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {

                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    Log.d(
                        TAG,
                        "Tap completed: $x,$y"
                    )
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    Log.d(
                        TAG,
                        "Tap cancelled"
                    )
                }
            },
            handler
        )
    }

    /**
     * Swipe method.
     *
     * Used later by the AI action engine.
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L
    ): Boolean {

        if (!isServiceReady()) {
            return false
        }

        val path = Path()

        path.moveTo(
            startX,
            startY
        )

        path.lineTo(
            endX,
            endY
        )

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        durationMs
                    )
                )
                .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {

                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    Log.d(
                        TAG,
                        "Swipe completed"
                    )
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    Log.d(
                        TAG,
                        "Swipe cancelled"
                    )
                }
            },
            handler
        )
    }

    private fun isServiceReady(): Boolean {

        if (instance !== this) {
            return false
        }

        if (!playerRunning) {
            return false
        }

        val targetPackage =
            getSharedPreferences(
                "player_settings",
                MODE_PRIVATE
            ).getString(
                "target_package",
                null
            )

        return !targetPackage.isNullOrEmpty() &&
                currentPackage == targetPackage
    }

    private fun logMessage(
        message: String
    ) {

        Log.d(
            TAG,
            message
        )
    }

    override fun onInterrupt() {

        playerRunning = false

        Log.d(
            TAG,
            "Accessibility Service interrupted"
        )
    }

    override fun onDestroy() {

        playerRunning = false

        if (instance === this) {
            instance = null
        }

        Log.d(
            TAG,
            "Accessibility Service destroyed"
        )

        super.onDestroy()
    }
}
