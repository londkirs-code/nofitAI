package com.example.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.FastScreenCaptureAccessibilityService
import com.example.service.MediaProjectionCaptureService

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenCaptureActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionCaptureService.start(
                context = applicationContext,
                resultCode = result.resultCode,
                data = result.data!!
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        // 1. If instant accessibility service is active, capture immediately with 0 prompts!
        val fastService = FastScreenCaptureAccessibilityService.instance
        if (fastService != null) {
            fastService.captureNow()
            finish()
            overridePendingTransition(0, 0)
            return
        }

        // 2. Check if accessibility service is enabled in system settings
        val isEnabledInSettings = FastScreenCaptureAccessibilityService.isAccessibilityServiceEnabled(this)
        if (isEnabledInSettings) {
            // Already enabled! Wait up to 400ms for binder to connect if process was dormant
            lifecycleScope.launch {
                var attempts = 0
                while (FastScreenCaptureAccessibilityService.instance == null && attempts < 8) {
                    delay(50)
                    attempts++
                }
                val activeInstance = FastScreenCaptureAccessibilityService.instance
                if (activeInstance != null) {
                    activeInstance.captureNow()
                    finish()
                } else {
                    launchMediaProjectionCapture()
                }
            }
        } else {
            showInstantCaptureGuideDialog()
        }
    }

    private fun showInstantCaptureGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("📸 צילום מסך מיידי ללא אישור בכל פעם")
            .setMessage(
                "כדי לצלם מסך באופן מיידי ובלחיצה אחת מתוך ההתראה (בלי שגוגל יבקש אישור בכל פעם מחדש):\n\n" +
                "הפעל פעם אחת את 'NotifAI' בהגדרות הנגישות של המכשיר."
            )
            .setPositiveButton("⚡ הפעל בהגדרות נגישות") { dialog, _ ->
                dialog.dismiss()
                FastScreenCaptureAccessibilityService.openAccessibilitySettings(this)
                finish()
            }
            .setNegativeButton("אישור חד-פעמי עכשיו") { dialog, _ ->
                dialog.dismiss()
                launchMediaProjectionCapture()
            }
            .setNeutralButton("ביטול") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun launchMediaProjectionCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
