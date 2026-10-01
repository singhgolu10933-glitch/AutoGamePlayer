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

        fun isCapturing(): Boolean =
            capturing

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

    private var mediaProjection:
            MediaProjection? = null

    private var virtualDisplay:
            VirtualDisplay? = null

    private var imageReader:
            ImageReader? = null

    private var captureThread:
            HandlerThread? = null

    private var captureHandler:
            Handler? = null

    private var lastFrameTime =
        0L

    // =============================================================
    // MEDIA PROJECTION CALLBACK
    // =============================================================

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

                stopSelf()
            }
        }

    // =============================================================
    // CREATE
    // =============================================================

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

    // =============================================================
    // START COMMAND
    // =============================================================

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

    // =============================================================
    // FOREGROUND SERVICE
    // =============================================================

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

    // =============================================================
    // START PROJECTION
    // =============================================================

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

        val observationWidth =
            width.coerceAtMost(1280)

        val scale =
            observationWidth.toFloat() /
                    width.toFloat()

        val observationHeight =
            (height * scale)
                .toInt()

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
                    observationHeight
        )
    }

    // =============================================================
    // FRAME PROCESSING
    // =============================================================

    private fun processLatestFrame(
        reader: ImageReader
    ) {

        val now =
            System.currentTimeMillis()

        /*
         * Approximately 4 FPS.
         */
        if (
            now - lastFrameTime <
            250L
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

    // =============================================================
    // IMAGE TO BITMAP
    // =============================================================

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

    // =============================================================
    // VISION
    // =============================================================

    private fun analyzeFrame(
        bitmap: Bitmap
    ) {

        /*
         * READ-ONLY STAGE.
         *
         * No tap.
         * No swipe.
         * No drag.
         * No automatic gameplay.
         */

        val state =
            BlockBlitzVision.analyze(
                bitmap
            )

        val occupied =
            state.board
                ?.occupiedCount()
                ?: -1

        val detectedPieces =
            state.pieces.count {
                it.detected
            }

        val confidence =
            (
                state.confidence * 100f
            ).toInt()

        getSharedPreferences(
            "vision_status",
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                "available",
                true
            )
            .putInt(
                "occupied_cells",
                occupied
            )
            .putInt(
                "pieces_detected",
                detectedPieces
            )
            .putInt(
                "confidence",
                confidence
            )
            .apply()

        Log.d(
            TAG,
            "BLOCK BLITZ VISION -> " +
                    "occupiedCells=$occupied " +
                    "pieces=$detectedPieces " +
                    "confidence=$confidence%"
        )
    }

    // =============================================================
    // NOTIFICATION
    // =============================================================

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
                android.R.drawable
                    .ic_menu_view
            )
            .setOngoing(true)
            .build()
    }

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

    // =============================================================
    // STOP PROJECTION
    // =============================================================

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

        getSharedPreferences(
            "vision_status",
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                "available",
                false
            )
            .apply()
    }

    // =============================================================
    // DESTROY
    // =============================================================

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

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
