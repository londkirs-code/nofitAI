package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.NotifAiApplication
import com.example.R
import com.example.data.repository.QueryResult
import com.example.receiver.NotificationActionReceiver
import com.example.ui.AttachContentDialogActivity
import com.example.ui.ChatDialogActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class DisplayMessage(
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isBot: Boolean
)

class NotificationChatService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                isRunning = false
                getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", false)
                    .apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PROCESS_REPLY -> {
                isRunning = true
                getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", true)
                    .apply()
                ensureForeground(isThinking = true)
                val queryText = intent.getStringExtra(EXTRA_REPLY_TEXT).orEmpty()
                val attachmentUriString = intent.getStringExtra(EXTRA_ATTACHMENT_URI)
                val attachmentUri = attachmentUriString?.let { Uri.parse(it) }
                if (queryText.isNotBlank() || attachmentUri != null) {
                    processIncomingQuery(queryText, attachmentUri)
                } else {
                    updateMainNotification(isThinking = false)
                }
            }
            ACTION_CLEAR_CHAT -> {
                synchronized(chatHistory) {
                    chatHistory.clear()
                    latestFullResponse = ""
                    latestModelUsed = ""
                    latestUserPrompt = ""
                }
                serviceScope.launch {
                    try {
                        (application as? NotifAiApplication)?.aiRepository?.createNewConversation()
                    } catch (_: Exception) {}
                }
                updateMainNotification(isThinking = false)
            }
            else -> {
                isRunning = true
                getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", true)
                    .apply()
                ensureForeground(isThinking = false)
            }
        }

        return START_STICKY
    }

    private fun ensureForeground(isThinking: Boolean) {
        try {
            val notification = buildMainNotification(isThinking = isThinking)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                notificationManager.notify(NOTIFICATION_ID, buildMainNotification(isThinking))
            } catch (_: Exception) {}
        }
    }

    private fun processIncomingQuery(query: String, attachmentUri: Uri?) {
        val userTimestamp = System.currentTimeMillis()
        val uriStr = attachmentUri?.toString()?.lowercase().orEmpty()
        val isAudio = attachmentUri != null && (
            uriStr.endsWith(".m4a") || uriStr.endsWith(".aac") ||
            uriStr.endsWith(".mp3") || uriStr.endsWith(".wav") ||
            uriStr.endsWith(".ogg") || uriStr.contains("audio")
        )
        val isImage = attachmentUri != null && (
            uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") ||
            uriStr.endsWith(".png") || uriStr.endsWith(".webp") ||
            uriStr.contains("image")
        )

        val displayUserText = when {
            isAudio && query.isBlank() -> "🎙️ [הקלטה קולית]"
            isAudio -> "🎙️ [הקלטה] $query"
            isImage && query.isBlank() -> "📸 [צילום מסך / תמונה]"
            isImage -> "📸 [תמונה] $query"
            attachmentUri != null && query.isBlank() -> "📎 [קובץ מצורף]"
            attachmentUri != null -> "📎 [קובץ] $query"
            else -> query
        }

        latestUserPrompt = displayUserText

        synchronized(chatHistory) {
            chatHistory.add(
                DisplayMessage(
                    senderName = "אתה",
                    text = displayUserText,
                    timestamp = userTimestamp,
                    isBot = false
                )
            )
        }

        // Replace the single persistent notification with thinking status
        updateMainNotification(isThinking = true)

        serviceScope.launch {
            val repository = (application as NotifAiApplication).aiRepository
            try {
                val fallbackPrompt = when {
                    isAudio -> "אנא האזן להקלטה הקולית המצורפת והשב על דברי המשתמש."
                    isImage -> "אנא נתח את צילום המסך או התמונה המצורפת."
                    else -> "אנא נתח את הקובץ המצורף"
                }
                val result: QueryResult = repository.askAi(
                    prompt = query.ifBlank { fallbackPrompt },
                    attachmentUri = attachmentUri
                )

                val responseSnippet = buildString {
                    append(result.responseText)
                    if (result.fallbackOccurred) {
                        append("\n\n(ℹ️ ${result.fallbackReason ?: "מענה ממפתח חלופי"})")
                    }
                }

                latestFullResponse = responseSnippet
                latestModelUsed = "${result.provider.displayName} • ${result.model}"

                synchronized(chatHistory) {
                    chatHistory.add(
                        DisplayMessage(
                            senderName = "NotifAI",
                            text = responseSnippet,
                            timestamp = System.currentTimeMillis(),
                            isBot = true
                        )
                    )
                    if (chatHistory.size > 30) {
                        chatHistory.removeAt(0)
                    }
                }

                // Replace the single notification with the new AI response silently
                updateMainNotification(isThinking = false)

            } catch (e: Exception) {
                val errorMsg = e.message ?: "שגיאה לא צפויה"
                latestFullResponse = "⚠️ שגיאה בקבלת תשובה: $errorMsg"
                latestModelUsed = "שגיאה"

                synchronized(chatHistory) {
                    chatHistory.add(
                        DisplayMessage(
                            senderName = "NotifAI",
                            text = latestFullResponse,
                            timestamp = System.currentTimeMillis(),
                            isBot = true
                        )
                    )
                }

                // Replace the single notification with the error message silently
                updateMainNotification(isThinking = false)
            }
        }
    }

    private fun updateMainNotification(isThinking: Boolean = false) {
        val notification = buildMainNotification(isThinking)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the SINGLE, highest-priority, silent, non-deletable notification.
     * Always replaces itself in place (never spawns extra notifications).
     */
    private fun buildMainNotification(isThinking: Boolean): Notification {
        // 1. Inline Reply Action ("שאל AI")
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("הקלד שאלה ל-NotifAI…")
            .build()

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DIRECT_REPLY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "שאל AI",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // 2. Send Content Dialog Action ("✨ שלח תוכן")
        val attachDialogIntent = Intent(this, AttachContentDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AttachContentDialogActivity.EXTRA_INITIAL_MODE, AttachContentDialogActivity.MODE_ATTACH)
        }
        val attachDialogPendingIntent = PendingIntent.getActivity(
            this,
            104,
            attachDialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sendContentAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "✨ שלח תוכן",
            attachDialogPendingIntent
        ).build()

        // 3. Actions Popup Dialog Action ("⚙️ פעולות" - Copy, Share, Select/Edit Models, Close Notification)
        val actionsDialogIntent = Intent(this, AttachContentDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AttachContentDialogActivity.EXTRA_INITIAL_MODE, AttachContentDialogActivity.MODE_ACTIONS)
        }
        val actionsDialogPendingIntent = PendingIntent.getActivity(
            this,
            107,
            actionsDialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionsButtonAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_manage,
            "⚙️ פעולות",
            actionsDialogPendingIntent
        ).build()

        // Content Click -> Full Conversation Dialog (ChatDialogActivity)
        val chatDialogIntent = Intent(this, ChatDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatDialogPendingIntent = PendingIntent.getActivity(
            this,
            103,
            chatDialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Delete Intent -> Immediately restores the notification if the user tries to swipe it away on Android 14+
        val restoreIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESTORE_NOTIFICATION
        }
        val restorePendingIntent = PendingIntent.getBroadcast(
            this,
            108,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainTitle: String
        val collapsedText: String
        val expandedBigText: String
        val summaryLine: String

        when {
            isThinking -> {
                mainTitle = "🤖 NotifAI מקליד תשובה…"
                collapsedText = "מעבד את שאלתך: \"${latestUserPrompt.take(60)}\" ⏳"
                expandedBigText = "שאלה: $latestUserPrompt\n\n⏳ NotifAI חושב ומנסח תשובה מלאה…"
                summaryLine = "מעבד תשובה…"
            }
            latestFullResponse.isNotBlank() -> {
                mainTitle = if (latestModelUsed.isNotBlank()) {
                    "🤖 NotifAI ($latestModelUsed)"
                } else {
                    "🤖 NotifAI • תשובה אחרונה"
                }
                collapsedText = latestFullResponse.replace("\n", " ").take(120)
                expandedBigText = latestFullResponse
                summaryLine = if (latestUserPrompt.isNotBlank()) {
                    "שאלה: ${latestUserPrompt.take(40)} • לחץ לשיחה המלאה"
                } else {
                    "לחץ לפתיחת הדו-שיח המלא"
                }
            }
            else -> {
                mainTitle = "NotifAI • שדה שאלה ישיר ל-AI"
                collapsedText = "הקלד שאלה ישירות בהתראה או לחץ 'שלח תוכן' ⚡"
                expandedBigText = "הקלד שאלה בשדה למטה ('שאל AI'), שלח צילום מסך/קול דרך 'שלח תוכן', או נהל מודלים ופעולות דרך 'פעולות'."
                summaryLine = "התראה קבועה • לחץ לפתיחת הדו-שיח"
            }
        }

        val largeAvatar = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_custom_asset)
        } catch (_: Exception) { null }

        return NotificationCompat.Builder(this, CHANNEL_MAIN_ID)
            .setSmallIcon(R.drawable.ic_stat_notifai_bell)
            .apply {
                if (largeAvatar != null) setLargeIcon(largeAvatar)
            }
            .setContentTitle(mainTitle)
            .setContentText(collapsedText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(mainTitle)
                    .bigText(expandedBigText)
                    .setSummaryText(summaryLine)
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setDefaults(0)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(chatDialogPendingIntent)
            .setDeleteIntent(restorePendingIntent)
            .setColor(Color.parseColor("#38BDF8"))
            .addAction(replyAction)
            .addAction(sendContentAction)
            .addAction(actionsButtonAction)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete legacy channels so old sound/vibration settings are completely purged
            try {
                notificationManager.deleteNotificationChannel("notifai_chat_channel")
                notificationManager.deleteNotificationChannel("notifai_responses_channel")
            } catch (_: Exception) {}

            val mainChannel = NotificationChannel(
                CHANNEL_MAIN_ID,
                "התראת NotifAI קבועה ושקטה",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראה יחידה וקבועה בעדיפות עליונה ללא צליל או רטט"
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(mainChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isRunning: Boolean = false
            internal set

        const val NOTIFICATION_ID = 9001
        const val CHANNEL_MAIN_ID = "notifai_single_silent_max_v3"

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_PROCESS_REPLY = "com.example.notifai.ACTION_PROCESS_REPLY"
        const val ACTION_CLEAR_CHAT = "com.example.notifai.ACTION_CLEAR_CHAT"
        const val ACTION_STOP_SERVICE = "com.example.notifai.ACTION_STOP_SERVICE"

        const val EXTRA_REPLY_TEXT = "extra_reply_text"
        const val EXTRA_ATTACHMENT_URI = "extra_attachment_uri"

        private val chatHistory = mutableListOf<DisplayMessage>()
        var latestFullResponse: String = ""
            private set
        var latestModelUsed: String = ""
            private set
        var latestUserPrompt: String = ""
            private set

        fun startService(context: Context) {
            try {
                context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", true)
                    .apply()
                val intent = Intent(context, NotificationChatService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            isRunning = false
            try {
                context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", false)
                    .apply()
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun sendQueryToService(context: Context, query: String, attachmentUri: Uri? = null) {
            try {
                context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", true)
                    .apply()
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_PROCESS_REPLY
                    putExtra(EXTRA_REPLY_TEXT, query)
                    putExtra(EXTRA_ATTACHMENT_URI, attachmentUri?.toString())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun copyText(context: Context, text: String) {
            val content = text.ifBlank { latestFullResponse }
            if (content.isBlank()) {
                Toast.makeText(context, "אין עדיין תשובה זמינה להעתקה", Toast.LENGTH_SHORT).show()
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("NotifAI", content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "התשובה הועתקה ללוח! 📋", Toast.LENGTH_SHORT).show()
        }

        fun dismissResponseNotification(context: Context, notifId: Int) {
            // Single notification architecture: no separate response notifications exist
        }

        fun clearAllResponses(context: Context) {
            try {
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_CLEAR_CHAT
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
