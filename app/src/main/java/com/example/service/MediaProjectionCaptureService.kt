package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class MediaProjectionCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val isCaptured = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start immediate foreground notification for MediaProjection
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    CAPTURE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CAPTURE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(CAPTURE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // Safety timeout of 5 seconds to guarantee service cleanup
        handler.postDelayed({
            if (!isCaptured.get()) {
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, 5000)

        // Capture screen
        handler.postDelayed({
            captureScreen(resultCode, resultData)
        }, 250) // Small delay to let system notification shade collapse cleanly

        return START_NOT_STICKY
    }

    private fun captureScreen(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            // Android 14+ (API 34+) strictly requires registering a MediaProjection.Callback
            // prior to calling createVirtualDisplay.
            mediaProjection?.registerCallback(projectionCallback, handler)

            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)

            val width = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
            val height = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 1920
            val density = if (displayMetrics.densityDpi > 0) displayMetrics.densityDpi else DisplayMetrics.DENSITY_DEFAULT

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "NotifAiScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                if (isCaptured.compareAndSet(false, true)) {
                    processImage(reader, width, height)
                }
            }, handler)

        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun processImage(reader: ImageReader, width: Int, height: Int) {
        var bitmap: Bitmap? = null
        try {
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val tempBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                tempBitmap.copyPixelsFromBuffer(buffer)
                image.close()

                bitmap = if (rowPadding != 0) {
                    Bitmap.createBitmap(tempBitmap, 0, 0, width, height).also {
                        if (it != tempBitmap) tempBitmap.recycle()
                    }
                } else {
                    tempBitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cleanup()
        }

        if (bitmap != null) {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val byteArray = stream.toByteArray()
                bitmap.recycle()

                val file = File(cacheDir, "screen_capture_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { it.write(byteArray) }

                // Send directly to the AI service without opening the app!
                NotificationChatService.sendQueryToService(
                    context = applicationContext,
                    query = "מה מופיע בצילום המסך? נתח, תאר והסבר בפירוט",
                    attachmentUri = Uri.fromFile(file)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            try {
                mediaProjection?.unregisterCallback(projectionCallback)
            } catch (e: Exception) {
                // Ignore if already unregistered or stopped
            }
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildForegroundNotification(): Notification {
        val largeAvatar = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_custom_asset)
        } catch (_: Exception) { null }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notifai_bell)
            .apply {
                if (largeAvatar != null) setLargeIcon(largeAvatar)
            }
            .setContentTitle("NotifAI • לוכד צילום מסך")
            .setContentText("שולח את צילום המסך ישירות למודל ה-AI…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "לכידת מסך NotifAI",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "notifai_capture_channel"
        private const val CAPTURE_NOTIFICATION_ID = 9002

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
