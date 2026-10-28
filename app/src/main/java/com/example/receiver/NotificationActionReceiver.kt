package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.service.NotificationChatService

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            ACTION_DIRECT_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(NotificationChatService.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank()) {
                    NotificationChatService.sendQueryToService(context, replyText)
                } else {
                    // Refresh notification so RemoteInput spinner finishes cleanly
                    NotificationChatService.startService(context)
                }
            }
            ACTION_RESTORE_NOTIFICATION -> {
                // Prevent deletion if user swipes the ongoing notification on Android 14+
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
            ACTION_DISMISS_NOTIFICATION -> {
                // Single notification mode: keep main notification alive
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
                    val chooser = Intent.createChooser(shareIntent, "שתף תשובה מ-NotifAI").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(chooser)
                } else {
                    android.widget.Toast.makeText(context, "אין עדיין תשובה זמינה לשיתוף", android.widget.Toast.LENGTH_SHORT).show()
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
        const val ACTION_DISMISS_NOTIFICATION = "com.example.notifai.ACTION_DISMISS_NOTIFICATION"
        const val ACTION_SCREENSHOT = "com.example.notifai.ACTION_SCREENSHOT"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_RESPONSE_TEXT = "extra_response_text"
    }
}
