package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class FastScreenCaptureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events not used; service is used exclusively for instant screenshots
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    /**
     * Executes immediate screenshot capture without any system prompt dialogs.
     */
    fun captureNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Dismiss notification shade so screen underneath is cleanly visible
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Small delay to allow the notification drawer collapse animation
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshotResult: ScreenshotResult) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val hardwareBuffer = screenshotResult.hardwareBuffer
                                        val colorSpace = screenshotResult.colorSpace
                                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                        hardwareBuffer.close()

                                        if (bitmap != null) {
                                            // Convert hardware bitmap to software bitmap for JPEG compression
                                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                            bitmap.recycle()

                                            val stream = ByteArrayOutputStream()
                                            softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                                            softwareBitmap.recycle()
                                            val byteArray = stream.toByteArray()

                                            val file = File(cacheDir, "screen_capture_${System.currentTimeMillis()}.jpg")
                                            FileOutputStream(file).use { it.write(byteArray) }

                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    applicationContext,
                                                    "📸 צילום מסך נלכד ונשלח ישירות ל-AI!",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                NotificationChatService.sendQueryToService(
                                                    context = applicationContext,
                                                    query = "מה מופיע בצילום המסך? נתח, תאר והסבר בפירוט",
                                                    attachmentUri = Uri.fromFile(file)
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        applicationContext,
                                        "שגיאה בלכידת מסך מהירה (קוד: $errorCode)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 250)
        }
    }

    companion object {
        var instance: FastScreenCaptureAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${FastScreenCaptureAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(":").any { it.equals(expectedServiceName, ignoreCase = true) }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
