package com.example.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.core.content.FileProvider
import com.example.data.remote.AttachmentData
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ScreenshotHelper {

    fun captureActivityScreenshot(
        activity: Activity,
        onCaptured: (AttachmentData, Uri?) -> Unit
    ) {
        val window = activity.window
        val view = window.decorView.rootView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val locationOfViewInWindow = IntArray(2)
            view.getLocationInWindow(locationOfViewInWindow)

            try {
                PixelCopy.request(
                    window,
                    Rect(
                        locationOfViewInWindow[0],
                        locationOfViewInWindow[1],
                        locationOfViewInWindow[0] + view.width,
                        locationOfViewInWindow[1] + view.height
                    ),
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            processBitmap(activity, bitmap, onCaptured)
                        } else {
                            fallbackViewCapture(activity, view, onCaptured)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                fallbackViewCapture(activity, view, onCaptured)
            }
        } else {
            fallbackViewCapture(activity, view, onCaptured)
        }
    }

    private fun fallbackViewCapture(
        activity: Activity,
        view: View,
        onCaptured: (AttachmentData, Uri?) -> Unit
    ) {
        try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            processBitmap(activity, bitmap, onCaptured)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processBitmap(
        activity: Activity,
        bitmap: Bitmap,
        onCaptured: (AttachmentData, Uri?) -> Unit
    ) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()

        val fileName = "screenshot_${System.currentTimeMillis()}.jpg"
        var uri: Uri? = null

        try {
            val cacheFile = File(activity.cacheDir, fileName)
            FileOutputStream(cacheFile).use { it.write(byteArray) }
            uri = Uri.fromFile(cacheFile)
        } catch (_: Exception) {}

        val attachment = AttachmentData(
            name = fileName,
            mimeType = "image/jpeg",
            bytes = byteArray
        )

        onCaptured(attachment, uri)
    }
}
