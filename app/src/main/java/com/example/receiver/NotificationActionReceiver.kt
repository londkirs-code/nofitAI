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
                }
            }
            ACTION_COPY_RESPONSE -> {
                NotificationChatService.copyLatestResponse(context)
            }
            ACTION_NEW_CHAT -> {
                val serviceIntent = Intent(context, NotificationChatService::class.java).apply {
                    action = NotificationChatService.ACTION_CLEAR_CHAT
                }
                context.startService(serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_DIRECT_REPLY = "com.example.notifai.ACTION_DIRECT_REPLY"
        const val ACTION_COPY_RESPONSE = "com.example.notifai.ACTION_COPY_RESPONSE"
        const val ACTION_NEW_CHAT = "com.example.notifai.ACTION_NEW_CHAT"
    }
}
