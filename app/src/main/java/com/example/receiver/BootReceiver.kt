package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.NotificationChatService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
            val isServiceEnabled = prefs.getBoolean("service_enabled", true)
            if (isServiceEnabled) {
                NotificationChatService.startService(context)
            }
        }
    }
}
