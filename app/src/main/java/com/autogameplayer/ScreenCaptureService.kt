package com.autogameplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log

import com.autogameplayer.blockblitz.BlockBlitzAi
import com.autogameplayer.blockblitz.BlockBlitzVision

import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {

        private const val TAG =
            "AutoGamePlayerCapture"

        private const val CHANNEL_ID =
            "auto_game_player_capture"

        private const val NOTIFICATION_ID =
            2001

        private var instance:
                ScreenCaptureService? = null

        private var capturing =
            false

        fun isCapturing(): Boolean {
            return capturing
        }

        fun startCapture(
            context: Context,
            resultCode: Int,
            data: Intent
        ) {

            val intent =
                Intent(
                    context,
                    ScreenCaptureService::class.java
                ).apply {

                    putExtra(
                        "result_code",
                        resultCode
                    )

                    putExtra(
                        "result_data",
                        data
                    )
                }

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                context.startForegroundService(
                    intent
                )

            } else {

                context.startService(
                    intent
                )
            }
        }

        fun stopCapture(
            context: Context
        ) {

            context.stopService(
                Intent(
                    context,
                    ScreenCaptureService::class.java
                )
            )
        }
    }

    // ============================================================
    // MEDIA PROJECTION
    // ============================================================

    private var mediaProjection:
            MediaProjection? = null

    private var virtualDisplay:
            VirtualDisplay? = null

    private var imageReader:
            ImageReader? = null

    // ============================================================
    // BACKGROUND THREAD
    // ============================================================

    private var captureThread:
            HandlerThread? = null

    private var captureHandler:
            Handler? = null

    // ============================================================
    // FRAME CONTROL
    // ============================================================

    private var lastFrameTime =
        0L

    /*
     * Vision runs approximately every 250 ms.
     * This is about 4 FPS.
     */
    private const val FRAME_INTERVAL_MS =
        250L

    // ============================================================
    // MEDIA PROJECTION CALLBACK
    // ============================================================

    private val mediaProjectionCallback =
        object :
            MediaProjection.Callback() {

            override fun onStop() {

                Log.d(
                    TAG,
                    "MediaProjection stopped"
                )

                capturing = false

                virtualDisplay?.release()

                virtualDisplay = null

                imageReader?.close()

                imageReader = null

                mediaProjection = null

                clearVisionStatus()

                stopSelf()
            }
        }

    // ============================================================
    // CREATE
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        instance = this

        createNotificationChannel()

        captureThread =
            HandlerThread(
                "AutoGamePlayerCaptureThread"
            ).also {

                it.start()

                captureHandler =
                    Handler(
                        it.looper
                    )
            }

        Log.d(
            TAG,
            "Screen capture service created"
        )
    }

    // ============================================================
    // START COMMAND
    // ============================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {

            stopSelf()

            return START_NOT_STICKY
        }

        val resultCode =
            intent.getIntExtra(
                "result_code",
                0
            )

        val resultData =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                intent.getParcelableExtra(
                    "result_data",
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")

                intent.getParcelableExtra<Intent>(
                    "result_data"
                )
            }

        if (resultData == null) {

            Log.e(
                TAG,
                "Screen capture data missing"
            )

            stopSelf()

            return START_NOT_STICKY
        }

        startCaptureForeground()

        try {

            startProjection(
                resultCode,
                resultData
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start screen projection",
                e
            )

            stopProjection()

            stopSelf()
        }

        return START_NOT_STICKY
    }

    // ============================================================
    // FOREGROUND SERVICE
    // ============================================================

    private fun startCaptureForeground() {

        val notification =
            createNotification()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    // ============================================================
    // START PROJECTION
    // ============================================================

    private fun startProjection(
        resultCode: Int,
        data: Intent
    ) {

        stopProjection()

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val projection =
            manager.getMediaProjection(
                resultCode,
                data
            )

        if (projection == null) {

            Log.e(
                TAG,
                "MediaProjection could not be created"
            )

            stopSelf()

            return
        }

        mediaProjection =
            projection

        projection.registerCallback(
            mediaProjectionCallback,
            captureHandler
        )

        // ========================================================
        // DISPLAY METRICS
        // ========================================================

        val metrics =
            DisplayMetrics()

        val displayManager =
            getSystemService(
                DISPLAY_SERVICE
            ) as DisplayManager

        displayManager
            .getDisplay(
                android.view.Display.DEFAULT_DISPLAY
            )
            ?.getRealMetrics(
                metrics
            )

        val width =
            metrics.widthPixels

        val height =
            metrics.heightPixels

        val density =
            metrics.densityDpi

        if (
            width <= 0 ||
            height <= 0
        ) {

            Log.e(
                TAG,
                "Invalid display size"
            )

            stopSelf()

            return
        }

        // ========================================================
        // OBSERVATION SIZE
        // ========================================================

        val observationWidth =
            width.coerceAtMost(
                1280
            )

        val scale =
            observationWidth.toFloat() /
                    width.toFloat()

        val observationHeight =
            (
                height *
                        scale
                )
                .toInt()
                .coerceAtLeast(1)

        // ========================================================
        // IMAGE READER
        // ========================================================

        imageReader =
            ImageReader.newInstance(
                observationWidth,
                observationHeight,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->

                processLatestFrame(
                    reader
                )

            },
            captureHandler
        )

        // ========================================================
        // VIRTUAL DISPLAY
        // ========================================================

        virtualDisplay =
            projection.createVirtualDisplay(
                "AutoGamePlayerScreen",

                observationWidth,

                observationHeight,

                density,

                DisplayManager
                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,

                imageReader?.surface,

                null,

                captureHandler
            )

        capturing = true

        Log.d(
            TAG,
            "Screen observation started: " +
                    "${observationWidth}x" +
                    "$observationHeight"
        )
    }

    // ============================================================
    // FRAME PROCESSING
    // ============================================================

    private fun processLatestFrame(
        reader: ImageReader
    ) {

        val now =
            System.currentTimeMillis()

        if (
            now - lastFrameTime <
            FRAME_INTERVAL_MS
        ) {

            return
        }

        lastFrameTime =
            now

        val image =
            try {

                reader.acquireLatestImage()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Could not acquire image",
                    e
                )

                null
            }

        if (image == null) {
            return
        }

        try {

            val bitmap =
                imageToBitmap(
                    image
                )

            if (bitmap != null) {

                analyzeFrame(
                    bitmap
                )

                bitmap.recycle()
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Frame processing failed",
                e
            )

        } finally {

            image.close()
        }
    }

    // ============================================================
    // IMAGE -> BITMAP
    // ============================================================

    private fun imageToBitmap(
        image: Image
    ): Bitmap? {

        val plane =
            image.planes
                .firstOrNull()
                ?: return null

        val buffer:
                ByteBuffer =
            plane.buffer

        val pixelStride =
            plane.pixelStride

        val rowStride =
            plane.rowStride

        val width =
            image.width

        val height =
            image.height

        if (
            pixelStride <= 0 ||
            rowStride <= 0 ||
            width <= 0 ||
            height <= 0
        ) {

            return null
        }

        val rowPadding =
            rowStride -
                    pixelStride * width

        val paddedWidth =
            width +
                    rowPadding /
                    pixelStride

        val bitmapWithPadding =
            Bitmap.createBitmap(
                paddedWidth,
                height,
                Bitmap.Config.ARGB_8888
            )

        buffer.rewind()

        bitmapWithPadding
            .copyPixelsFromBuffer(
                buffer
            )

        val resultBitmap =
            if (
                paddedWidth != width
            ) {

                Bitmap.createBitmap(
                    bitmapWithPadding,
                    0,
                    0,
                    width,
                    height
                )

            } else {

                bitmapWithPadding
            }

        if (
            resultBitmap !==
            bitmapWithPadding
        ) {

            bitmapWithPadding.recycle()
        }

        return resultBitmap
    }

    // ============================================================
    // VISION + AI
    // ============================================================

    private fun analyzeFrame(
        bitmap: Bitmap
    ) {

        try {

            // ====================================================
            // BLOCK BLITZ VISION
            // ====================================================

            val state =
                BlockBlitzVision.analyze(
                    bitmap
                )

            val board =
                state.board

            val occupied =
                board
                    ?.occupiedCount()
                    ?: -1

            val detectedPieces =
                state.pieces.count {
                    it.detected
                }

            val confidence =
                (
                    state.confidence *
                            100f
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        100
                    )

            // ====================================================
            // SAVE BASIC VISION STATUS
            // ====================================================

            val prefs =
                getSharedPreferences(
                    "vision_status",
                    MODE_PRIVATE
                )

            val editor =
                prefs.edit()

            editor.putBoolean(
                "available",
                true
            )

            editor.putInt(
                "occupied_cells",
                occupied
            )

            editor.putInt(
                "pieces_detected",
                detectedPieces
            )

            editor.putInt(
                "confidence",
                confidence
            )

            editor.putBoolean(
                "board_detected",
                board != null
            )

            // ====================================================
            // SAVE DETECTED PIECES
            // ====================================================

            for (
                piece in state.pieces
            ) {

                val index =
                    piece.index

                editor.putBoolean(
                    "piece_${index}_detected",
                    piece.detected
                )

                val shape =
                    piece.cells.joinToString(
                        separator = ";"
                    ) {

                        "${it.first},${it.second}"
                    }

                editor.putString(
                    "piece_${index}_shape",
                    shape
                )

                editor.putInt(
                    "piece_${index}_cell_count",
                    piece.cells.size
                )
            }

            // ====================================================
            // AI DECISION
            // ====================================================

            /*
             * IMPORTANT:
             *
             * AI only calculates the move.
             *
             * It does NOT touch the game.
             *
             * No tap.
             * No swipe.
             * No drag.
             */

            val bestMove =
                if (
                    board != null &&
                    detectedPieces > 0
                ) {

                    BlockBlitzAi.findBestMove(
                        state
                    )

                } else {

                    null
                }

            if (bestMove != null) {

                editor.putBoolean(
                    "best_move_available",
                    true
                )

                editor.putInt(
                    "best_move_piece",
                    bestMove.pieceIndex
                )

                editor.putInt(
                    "best_move_row",
                    bestMove.row
                )

                editor.putInt(
                    "best_move_column",
                    bestMove.column
                )

                editor.putFloat(
                    "best_move_score",
                    bestMove.score
                )

                editor.putInt(
                    "best_move_lines",
                    bestMove.linesCleared
                )

                editor.putString(
                    "best_move_reason",
                    bestMove.reason
                )

                Log.d(
                    TAG,
                    "AI BEST MOVE -> " +
                            "piece=${bestMove.pieceIndex} " +
                            "row=${bestMove.row} " +
                            "column=${bestMove.column} " +
                            "score=${bestMove.score} " +
                            "lines=${bestMove.linesCleared}"
                )

            } else {

                editor.putBoolean(
                    "best_move_available",
                    false
                )

                editor.putInt(
                    "best_move_piece",
                    -1
                )

                editor.putInt(
                    "best_move_row",
                    -1
                )

                editor.putInt(
                    "best_move_column",
                    -1
                )

                editor.putFloat(
                    "best_move_score",
                    0f
                )

                editor.putInt(
                    "best_move_lines",
                    0
                )

                editor.putString(
                    "best_move_reason",
                    "No legal placement detected"
                )

                Log.d(
                    TAG,
                    "AI -> No legal move"
                )
            }

            // ====================================================
            // AUTOMATIC ACTION
            // ====================================================

            /*
             * Deliberately OFF.
             *
             * We are testing:
             *
             * Screen
             *   ↓
             * Vision
             *   ↓
             * Pieces
             *   ↓
             * AI
             *   ↓
             * Best Move
             *
             * Only after this is stable will
             * Accessibility drag be connected.
             */

            editor.putBoolean(
                "automatic_action",
                false
            )

            editor.putString(
                "player_mode",
                "Normal / Human-like"
            )

            editor.putBoolean(
                "extreme_automation",
                false
            )

            editor.apply()

            Log.d(
                TAG,
                "VISION RESULT -> " +
                        "board=${board != null}, " +
                        "occupied=$occupied, " +
                        "pieces=$detectedPieces/3, " +
                        "confidence=$confidence%"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Vision/AI analysis failed",
                e
            )

            getSharedPreferences(
                "vision_status",
                MODE_PRIVATE
            )
                .edit()
                .putBoolean(
                    "available",
                    false
                )
                .putBoolean(
                    "best_move_available",
                    false
                )
                .putString(
                    "best_move_reason",
                    "Vision error"
                )
                .apply()
        }
    }

    // ============================================================
    // CLEAR VISION STATUS
    // ============================================================

    private fun clearVisionStatus() {

        getSharedPreferences(
            "vision_status",
            MODE_PRIVATE
        )
            .edit()
            .clear()
            .putBoolean(
                "available",
                false
            )
            .putBoolean(
                "automatic_action",
                false
            )
            .putBoolean(
                "extreme_automation",
                false
            )
            .putString(
                "player_mode",
                "Normal / Human-like"
            )
            .apply()
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createNotification():
            Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Auto Game Player"
            )
            .setContentText(
                "Screen observation is active"
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_view
            )
            .setOngoing(true)
            .build()
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {

            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,

                "Auto Game Player Screen Observation",

                NotificationManager
                    .IMPORTANCE_LOW
            ).apply {

                description =
                    "Shows when user-approved " +
                    "screen observation is active."
            }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    // ============================================================
    // STOP PROJECTION
    // ============================================================

    private fun stopProjection() {

        capturing = false

        val projection =
            mediaProjection

        if (projection != null) {

            try {

                projection.unregisterCallback(
                    mediaProjectionCallback
                )

            } catch (_: Exception) {
            }
        }

        virtualDisplay?.release()

        virtualDisplay = null

        imageReader?.close()

        imageReader = null

        if (projection != null) {

            try {

                projection.stop()

            } catch (_: Exception) {
            }
        }

        mediaProjection = null

        clearVisionStatus()
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        stopProjection()

        captureThread?.quitSafely()

        captureThread = null

        captureHandler = null

        if (instance === this) {

            instance = null
        }

        Log.d(
            TAG,
            "Screen capture service stopped"
        )

        super.onDestroy()
    }

    // ============================================================
    // BIND
    // ============================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
