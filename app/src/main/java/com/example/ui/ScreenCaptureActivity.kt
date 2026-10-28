package com.example.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.service.FastScreenCaptureAccessibilityService
import com.example.service.MediaProjectionCaptureService
import com.example.util.AppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * On-demand Screen Capture Activity (Tasks #22 & #23).
 * Triggered when the user taps "צילום מסך" in the notification or app.
 * If the instant Accessibility screenshot service is already active, captures immediately with 0 prompts.
 * Otherwise, displays a clear explanation dialog explaining WHY screen capture permission is needed
 * before opening Accessibility settings or launching the one-time Android MediaProjection prompt.
 */
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
        AppStrings.initFromPrefs(this)
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
                    showPermissionExplanationBeforeCapture()
                }
            }
        } else {
            showPermissionExplanationBeforeCapture()
        }
    }

    private fun showPermissionExplanationBeforeCapture() {
        AlertDialog.Builder(this)
            .setTitle(AppStrings.tr("📸 למה נדרשת הרשאת צילום מסך?", "📸 Why is Screen Capture Permission Needed?"))
            .setMessage(
                AppStrings.tr(
                    "בחרת לשלוח צילום מסך ל-NotifAI.\n\n" +
                        "למה ההרשאה נדרשת?\n" +
                        "כדי ש-NotifAI תוכל ללכוד את מה שמוצג כעת על המסך שלך ולהעביר את התמונה למודל ה-AI (Vision) לצורך ניתוח, תרגום או מענה בהתראה.\n\n" +
                        "כיצד תרצה לאשר?\n" +
                        "• מומלץ: הפעל פעם אחת את 'NotifAI' בהגדרות הנגישות לצילום מיידי בלחיצה אחת ללא שאלות חוזרות.\n" +
                        "• חלופה: אשר לכידת מסך חד-פעמית כעת דרך מערכת אנדרואיד.",
                    "You chose to send a screenshot to NotifAI.\n\n" +
                        "Why is this permission needed?\n" +
                        "So NotifAI can capture what is currently on your screen and send the image to the Vision AI model for analysis, translation, or answering directly in the notification.\n\n" +
                        "How would you like to proceed?\n" +
                        "• Recommended: Enable 'NotifAI' once in Accessibility Settings for instant 1-tap screenshots without repeated prompts.\n" +
                        "• Alternative: Approve a one-time screen capture now via Android."
                )
            )
            .setPositiveButton(AppStrings.tr("⚡ הפעל צילום מיידי קבוע (נגישות)", "⚡ Enable Instant 1-Tap Screenshot")) { dialog, _ ->
                dialog.dismiss()
                FastScreenCaptureAccessibilityService.openAccessibilitySettings(this)
                finish()
            }
            .setNegativeButton(AppStrings.tr("📸 צילום חד-פעמי עכשיו", "📸 One-Time Capture Now")) { dialog, _ ->
                dialog.dismiss()
                launchMediaProjectionCapture()
            }
            .setNeutralButton(AppStrings.tr("ביטול", "Cancel")) { dialog, _ ->
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
