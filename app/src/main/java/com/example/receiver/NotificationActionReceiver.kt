package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.example.service.NotificationChatService
import com.example.util.AppStrings
import com.example.util.MediaStorageManager

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        AppStrings.initFromPrefs(context)

        when (intent.action) {
            ACTION_DIRECT_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(NotificationChatService.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank()) {
                    NotificationChatService.sendQueryToService(context, replyText)
                } else {
                    NotificationChatService.startService(context)
                }
            }
            ACTION_RESTORE_NOTIFICATION -> {
                val prefs = context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                val isEnabled = prefs.getBoolean("service_enabled", true)
                if (isEnabled) {
                    NotificationChatService.startService(context)
                }
            }
            ACTION_COPY_RESPONSE -> {
                val textToCopy = intent.getStringExtra(EXTRA_RESPONSE_TEXT) ?: NotificationChatService.latestFullResponse
                NotificationChatService.copyText(context, textToCopy)
            }
            ACTION_NEW_CHAT, ACTION_CLEAR_ALL_NOTIFICATIONS -> {
                val serviceIntent = Intent(context, NotificationChatService::class.java).apply {
                    action = NotificationChatService.ACTION_CLEAR_CHAT
                }
                context.startService(serviceIntent)
            }
            ACTION_CLEAR_DISPLAY_ONLY -> {
                NotificationChatService.clearNotificationDisplayOnly(context)
            }
            ACTION_STOP_AUDIO_PLAYBACK -> {
                NotificationChatService.stopAudioPlayback(context)
                Toast.makeText(
                    context,
                    AppStrings.tr("ניגון השמע והתראת השמע נסגרו ⏹️", "Audio playback & notification closed ⏹️"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            ACTION_DOWNLOAD_MEDIA -> {
                val uriStr = intent.getStringExtra(NotificationChatService.EXTRA_ATTACHMENT_URI)
                    ?: NotificationChatService.latestAttachmentUri
                val mime = intent.getStringExtra(NotificationChatService.EXTRA_ATTACHMENT_MIME)
                    ?: NotificationChatService.latestAttachmentMimeType
                val name = intent.getStringExtra(NotificationChatService.EXTRA_ATTACHMENT_NAME)
                    ?: NotificationChatService.latestAttachmentName
                if (!uriStr.isNullOrBlank()) {
                    val saved = MediaStorageManager.saveFileToPublicDownloads(context, uriStr, mime, name)
                    if (saved != null) {
                        Toast.makeText(
                            context,
                            AppStrings.tr(
                                "הקובץ הורד לתיקיית ההורדות (Downloads/NotifAI): $saved 📥",
                                "File downloaded to Downloads/NotifAI: $saved 📥"
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            ACTION_DISMISS_NOTIFICATION -> {
                val prefs = context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("service_enabled", true)) {
                    NotificationChatService.startService(context)
                }
            }
            ACTION_SHARE_RESPONSE -> {
                val textToShare = intent.getStringExtra(EXTRA_RESPONSE_TEXT)
                    ?.takeIf { it.isNotBlank() }
                    ?: NotificationChatService.latestFullResponse
                if (textToShare.isNotBlank()) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, textToShare)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val chooser = Intent.createChooser(
                        shareIntent,
                        AppStrings.tr("שתף תשובה מ-NotifAI", "Share response from NotifAI")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(chooser)
                } else {
                    Toast.makeText(
                        context,
                        AppStrings.tr("אין עדיין תשובה זמינה לשיתוף", "No response available to share yet"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            ACTION_SCREENSHOT -> {
                val accessibilityService = com.example.service.FastScreenCaptureAccessibilityService.instance
                if (accessibilityService != null) {
                    accessibilityService.captureNow()
                } else {
                    val captureIntent = Intent(context, com.example.ui.ScreenCaptureActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(captureIntent)
                }
            }
        }
    }

    companion object {
        const val ACTION_DIRECT_REPLY = "com.example.notifai.ACTION_DIRECT_REPLY"
        const val ACTION_RESTORE_NOTIFICATION = "com.example.notifai.ACTION_RESTORE_NOTIFICATION"
        const val ACTION_COPY_RESPONSE = "com.example.notifai.ACTION_COPY_RESPONSE"
        const val ACTION_SHARE_RESPONSE = "com.example.notifai.ACTION_SHARE_RESPONSE"
        const val ACTION_NEW_CHAT = "com.example.notifai.ACTION_NEW_CHAT"
        const val ACTION_CLEAR_ALL_NOTIFICATIONS = "com.example.notifai.ACTION_CLEAR_ALL_NOTIFICATIONS"
        const val ACTION_CLEAR_DISPLAY_ONLY = "com.example.notifai.ACTION_CLEAR_DISPLAY_ONLY"
        const val ACTION_STOP_AUDIO_PLAYBACK = "com.example.notifai.ACTION_STOP_AUDIO_PLAYBACK"
        const val ACTION_DOWNLOAD_MEDIA = "com.example.notifai.ACTION_DOWNLOAD_MEDIA"
        const val ACTION_DISMISS_NOTIFICATION = "com.example.notifai.ACTION_DISMISS_NOTIFICATION"
        const val ACTION_SCREENSHOT = "com.example.notifai.ACTION_SCREENSHOT"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_RESPONSE_TEXT = "extra_response_text"
    }
}
