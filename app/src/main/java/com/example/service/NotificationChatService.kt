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
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.NotifAiApplication
import com.example.R
import com.example.data.repository.QueryResult
import com.example.receiver.NotificationActionReceiver
import com.example.ui.AttachContentDialogActivity
import com.example.ui.ChatDialogActivity
import com.example.ui.FullResponseActivity
import com.example.ui.ScreenCaptureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PROCESS_REPLY -> {
                val queryText = intent.getStringExtra(EXTRA_REPLY_TEXT).orEmpty()
                val attachmentUriString = intent.getStringExtra(EXTRA_ATTACHMENT_URI)
                val attachmentUri = attachmentUriString?.let { Uri.parse(it) }
                if (queryText.isNotBlank() || attachmentUri != null) {
                    processIncomingQuery(queryText, attachmentUri)
                }
            }
            ACTION_CLEAR_CHAT -> {
                synchronized(chatHistory) {
                    chatHistory.clear()
                    latestFullResponse = ""
                    latestModelUsed = ""
                }
                serviceScope.launch {
                    try {
                        (application as? NotifAiApplication)?.aiRepository?.createNewConversation()
                    } catch (_: Exception) {}
                }
                updateNotification()
            }
            else -> {
                // Initial start or refresh
                try {
                    val initialNotification = buildNotification(isThinking = false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(
                                NOTIFICATION_ID,
                                initialNotification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            )
                        } else {
                            startForeground(NOTIFICATION_ID, initialNotification)
                        }
                    } else {
                        startForeground(NOTIFICATION_ID, initialNotification)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return START_STICKY
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

        // Show thinking status immediately in the notification
        updateNotification(isThinking = true)

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

                latestFullResponse = result.responseText
                latestModelUsed = "${result.provider.displayName} (${result.model})"

                val responseSnippet = buildString {
                    append(result.responseText)
                    if (result.fallbackOccurred) {
                        append("\n\n(ℹ️ ${result.fallbackReason ?: "מענה ממפתח חלופי"})")
                    }
                }

                synchronized(chatHistory) {
                    chatHistory.add(
                        DisplayMessage(
                            senderName = "NotifAI",
                            text = responseSnippet,
                            timestamp = System.currentTimeMillis(),
                            isBot = true
                        )
                    )
                    // Keep history compact in notification memory
                    if (chatHistory.size > 12) {
                        chatHistory.removeAt(0)
                    }
                }

                updateNotification(isThinking = false)

            } catch (e: Exception) {
                val errorMsg = e.message ?: "שגיאה לא צפויה"
                latestFullResponse = errorMsg

                synchronized(chatHistory) {
                    chatHistory.add(
                        DisplayMessage(
                            senderName = "NotifAI",
                            text = "⚠️ $errorMsg",
                            timestamp = System.currentTimeMillis(),
                            isBot = true
                        )
                    )
                }
                updateNotification(isThinking = false)
            }
        }
    }

    private fun updateNotification(isThinking: Boolean = false) {
        val notification = buildNotification(isThinking)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isThinking: Boolean): Notification {
        val userPerson = Person.Builder()
            .setName("אתה")
            .setKey("user_key")
            .build()

        val botPerson = Person.Builder()
            .setName(if (latestModelUsed.isNotBlank()) "NotifAI ($latestModelUsed)" else "NotifAI 🤖")
            .setKey("bot_key")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle("NotifAI • צ'אט ישיר")
            .setGroupConversation(false)

        synchronized(chatHistory) {
            if (chatHistory.isEmpty()) {
                messagingStyle.addMessage(
                    "שלום! הקלד שאלה ישירות בהתראה ואענה מיד ⚡",
                    System.currentTimeMillis(),
                    botPerson
                )
            } else {
                for (msg in chatHistory) {
                    val person = if (msg.isBot) botPerson else userPerson
                    messagingStyle.addMessage(msg.text, msg.timestamp, person)
                }
            }
        }

        if (isThinking) {
            messagingStyle.addMessage("מקליד תשובה… ✍️", System.currentTimeMillis(), botPerson)
        }

        // Inline Reply RemoteInput
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

        // Copy Latest Response Action
        val copyPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COPY_RESPONSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val copyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "📋 העתק",
            copyPendingIntent
        ).build()

        // Chat Dialog Independent Viewer Action
        val chatDialogIntent = Intent(this, ChatDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatDialogPendingIntent = PendingIntent.getActivity(
            this,
            103,
            chatDialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullViewAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "💬 חלון שיחה",
            chatDialogPendingIntent
        ).build()

        // Attach Content Dialog Action (Screenshot, Voice Record, Hebrew STT, File Upload)
        val attachDialogIntent = Intent(this, AttachContentDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

        // New Chat Action
        val newChatPendingIntent = PendingIntent.getBroadcast(
            this,
            105,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_NEW_CHAT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val newChatAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_rotate,
            "🧹 נקה",
            newChatPendingIntent
        ).build()

        // Screenshot Action (Broadcast for instant capture via AccessibilityService or guidance)
        val screenshotBroadcastIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SCREENSHOT
        }
        val screenshotPendingIntent = PendingIntent.getBroadcast(
            this,
            106,
            screenshotBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val screenshotAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_camera,
            "📸 צילום מסך",
            screenshotPendingIntent
        ).build()

        // Content Intent (Clicking on the notification opens the independent dialog window!)
        val openDialogIntent = Intent(this, ChatDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openDialogPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openDialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NotifAI • צ'אט AI פעיל")
            .setContentText(if (isThinking) "NotifAI מקליד תשובה…" else (latestFullResponse.ifBlank { "שאל AI ישירות מהתראה" }))
            .setStyle(messagingStyle)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openDialogPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#38BDF8"))
            .addAction(replyAction)
            .addAction(sendContentAction)
            .addAction(fullViewAction)
            .addAction(screenshotAction)
            .addAction(copyAction)
            .addAction(newChatAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "שיחת NotifAI פעילה",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראה קבועה ואינטראקטיבית לשיחה ישירה עם AI"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "notifai_chat_channel"

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

        fun startService(context: Context) {
            try {
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

        fun sendQueryToService(context: Context, query: String, attachmentUri: Uri? = null) {
            try {
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

        fun copyLatestResponse(context: Context) {
            if (latestFullResponse.isBlank()) {
                Toast.makeText(context, "אין תשובה זמינה להעתקה", Toast.LENGTH_SHORT).show()
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("NotifAI Response", latestFullResponse)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "התשובה הועתקה ללוח! 📋", Toast.LENGTH_SHORT).show()
        }
    }
}
