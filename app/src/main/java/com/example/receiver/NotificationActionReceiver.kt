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
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notifId != -1) {
                    NotificationChatService.dismissResponseNotification(context, notifId)
                }
                if (!replyText.isNullOrBlank()) {
                    NotificationChatService.sendQueryToService(context, replyText)
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
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notifId != -1) {
                    NotificationChatService.dismissResponseNotification(context, notifId)
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
