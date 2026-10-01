package com.autogameplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "AutoGamePlayerCapture"
        private const val CHANNEL_ID = "auto_game_player_capture"
        private const val NOTIFICATION_ID = 2001

        private var instance: ScreenCaptureService? = null
        private var capturing = false

        fun isCapturing(): Boolean = capturing

        fun startCapture(
            context: Context,
            resultCode: Int,
            data: Intent
        ) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("result_data", data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCapture(context: Context) {
            context.stopService(
                Intent(context, ScreenCaptureService::class.java)
            )
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var lastFrameTime = 0L

    /*
     * IMPORTANT:
     * Android requires this callback to be registered
     * before createVirtualDisplay() is called.
     */
    private val mediaProjectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system/user")

                capturing = false

                virtualDisplay?.release()
                virtualDisplay = null

                imageReader?.close()
                imageReader = null

                mediaProjection = null

                stopSelf()
            }
        }

    override fun onCreate() {
        super.onCreate()

        instance = this

        createNotificationChannel()

        Log.d(TAG, "Screen capture service created")
    }

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
            intent.getIntExtra("result_code", 0)

        val resultData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    "result_data",
                    Intent::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>("result_data")
            }

        if (resultData == null) {
            Log.e(TAG, "Screen capture data missing")
            stopSelf()
            return START_NOT_STICKY
        }

        startCaptureForeground()

        try {
            startProjection(resultCode, resultData)
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

    private fun startCaptureForeground() {

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

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

        mediaProjection = projection

        /*
         * IMPORTANT FIX:
         * Register callback BEFORE createVirtualDisplay().
         */
        projection.registerCallback(
            mediaProjectionCallback,
            null
        )

        val metrics = DisplayMetrics()

        val displayManager =
            getSystemService(
                DISPLAY_SERVICE
            ) as DisplayManager

        displayManager
            .getDisplay(
                android.view.Display.DEFAULT_DISPLAY
            )
            ?.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val observationWidth =
            width.coerceAtMost(1280)

        val scale =
            observationWidth.toFloat() /
                    width.toFloat()

        val observationHeight =
            (height * scale).toInt()

        imageReader =
            ImageReader.newInstance(
                observationWidth,
                observationHeight,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                processLatestFrame(reader)
            },
            null
        )

        /*
         * Callback has already been registered above.
         * It is now safe to create the VirtualDisplay.
         */
        virtualDisplay =
            projection.createVirtualDisplay(
                "AutoGamePlayerScreen",
                observationWidth,
                observationHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

        capturing = true

        Log.d(
            TAG,
            "Screen observation started: " +
                    "${observationWidth}x$observationHeight"
        )
    }

    private fun processLatestFrame(
        reader: ImageReader
    ) {

        val image =
            try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Could not acquire screen frame",
                    e
                )
                null
            }

        if (image == null) {
            return
        }

        try {

            val now =
                System.currentTimeMillis()

            /*
             * Human-like / low-load observation rate.
             */
            if (now - lastFrameTime < 250L) {
                return
            }

            lastFrameTime = now

            val width = image.width
            val height = image.height

            Log.d(
                TAG,
                "Frame observed: ${width}x${height}"
            )

        } finally {

            image.close()
        }
    }

    private fun createNotification(): Notification {

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

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Auto Game Player Screen Observation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "Shows when user-approved screen observation is active."
            }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(channel)
    }

    private fun stopProjection() {

        capturing = false

        val projection = mediaProjection

        if (projection != null) {

            try {
                projection.unregisterCallback(
                    mediaProjectionCallback
                )
            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "Callback already unregistered"
                )
            }
        }

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        if (projection != null) {

            try {
                projection.stop()
            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "MediaProjection already stopped"
                )
            }
        }

        mediaProjection = null
    }

    override fun onDestroy() {

        stopProjection()

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
