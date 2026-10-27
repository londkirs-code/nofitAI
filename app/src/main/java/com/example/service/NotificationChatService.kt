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
import java.util.concurrent.atomic.AtomicInteger

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
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                clearAllResponseNotificationsInternal()
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
                clearAllResponseNotificationsInternal()
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
                // Initial start or refresh
                try {
                    val initialNotification = buildMainNotification(isThinking = false)
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

        // Show thinking status immediately in the main persistent notification
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
                    if (chatHistory.size > 20) {
                        chatHistory.removeAt(0)
                    }
                }

                // Update the main persistent notification back to idle
                updateMainNotification(isThinking = false)

                // Post a separate DISMISSIBLE notification for this dialogue/response
                postResponseNotification(
                    userQuery = displayUserText,
                    responseText = responseSnippet,
                    modelName = latestModelUsed
                )

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

                updateMainNotification(isThinking = false)

                postResponseNotification(
                    userQuery = displayUserText,
                    responseText = "⚠️ שגיאה בקבלת תשובה: $errorMsg",
                    modelName = "שגיאה"
                )
            }
        }
    }

    private fun updateMainNotification(isThinking: Boolean = false) {
        val notification = buildMainNotification(isThinking)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the SINGLE persistent main notification.
     * Contains the RemoteInput field, fast actions, and cannot be swiped away (ongoing = true).
     */
    private fun buildMainNotification(isThinking: Boolean): Notification {
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

        // Clear All Responses Action
        val clearAllPendingIntent = PendingIntent.getBroadcast(
            this,
            105,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_CLEAR_ALL_NOTIFICATIONS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val clearAllAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_rotate,
            "🧹 נקה הכל",
            clearAllPendingIntent
        ).build()

        // Screenshot Action
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

        val mainTitle = "NotifAI • שדה שאלה ישיר ל-AI"
        val mainText = when {
            isThinking -> "NotifAI מקליד תשובה לשאלתך… ✍️"
            latestUserPrompt.isNotBlank() -> "שאלה אחרונה: \"${latestUserPrompt.take(45)}\" • הקלד שאלה נוספת"
            else -> "הקלד שאלה ישירות בהתראה או לחץ 'שלח תוכן' ⚡"
        }

        return NotificationCompat.Builder(this, CHANNEL_MAIN_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(mainTitle)
            .setContentText(mainText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(mainTitle)
                    .bigText(mainText)
                    .setSummaryText("התראה קבועה עם שדה הזנה")
            )
            .setOngoing(true) // Crucial: ONLY the main input notification is ongoing!
            .setAutoCancel(false)
            .setContentIntent(chatDialogPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#38BDF8"))
            .addAction(replyAction)
            .addAction(sendContentAction)
            .addAction(screenshotAction)
            .addAction(fullViewAction)
            .addAction(clearAllAction)
            .build()
    }

    /**
     * Posts a separate DISMISSIBLE notification for a dialogue response.
     * The user CAN swipe it away, tap to dismiss, or click "מחק".
     */
    private fun postResponseNotification(userQuery: String, responseText: String, modelName: String) {
        val notifId = responseIdGenerator.incrementAndGet()
        synchronized(activeResponseIds) {
            activeResponseIds.add(notifId)
        }

        // Open Dialog PendingIntent
        val chatDialogIntent = Intent(this, ChatDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatDialogPendingIntent = PendingIntent.getActivity(
            this,
            notifId * 10 + 1,
            chatDialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Copy PendingIntent
        val copyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COPY_RESPONSE
            putExtra(NotificationActionReceiver.EXTRA_RESPONSE_TEXT, responseText)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            this,
            notifId * 10 + 2,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val copyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "📋 העתק",
            copyPendingIntent
        ).build()

        // Dismiss this specific notification PendingIntent
        val dismissIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            notifId * 10 + 3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_delete,
            "🗑️ מחק",
            dismissPendingIntent
        ).build()

        val fullViewAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "💬 פתח שיחה",
            chatDialogPendingIntent
        ).build()

        val responseTitle = "🤖 NotifAI ($modelName)"
        val summaryPrompt = "שאלה: ${userQuery.take(30)}"

        val responseNotification = NotificationCompat.Builder(this, CHANNEL_RESPONSES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(responseTitle)
            .setContentText(responseText.take(80))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(responseTitle)
                    .setSummaryText(summaryPrompt)
                    .bigText(responseText)
            )
            .setOngoing(false) // Crucial: DISMISSIBLE, user can swipe it away!
            .setAutoCancel(true) // Crucial: disappears when tapped
            .setDeleteIntent(dismissPendingIntent) // Cleans tracking set when swiped away
            .setContentIntent(chatDialogPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#38BDF8"))
            .addAction(copyAction)
            .addAction(fullViewAction)
            .addAction(dismissAction)
            .build()

        notificationManager.notify(notifId, responseNotification)
    }

    private fun clearAllResponseNotificationsInternal() {
        synchronized(activeResponseIds) {
            for (id in activeResponseIds) {
                try {
                    notificationManager.cancel(id)
                } catch (_: Exception) {}
            }
            activeResponseIds.clear()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mainChannel = NotificationChannel(
                CHANNEL_MAIN_ID,
                "התראת NotifAI קבועה (שדה הזנה)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראה קבועה עם שדה הזנה וכפתורי פעולה לשאילת AI"
                enableLights(true)
                lightColor = Color.CYAN
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val responsesChannel = NotificationChannel(
                CHANNEL_RESPONSES_ID,
                "תשובות ודו-שיח NotifAI (ניתנות למחיקה)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראות המענה של ה-AI שניתן להחליק, למחוק או להעתיק"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(mainChannel)
            notificationManager.createNotificationChannel(responsesChannel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_MAIN_ID = "notifai_chat_channel"
        const val CHANNEL_RESPONSES_ID = "notifai_responses_channel"

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_PROCESS_REPLY = "com.example.notifai.ACTION_PROCESS_REPLY"
        const val ACTION_CLEAR_CHAT = "com.example.notifai.ACTION_CLEAR_CHAT"
        const val ACTION_STOP_SERVICE = "com.example.notifai.ACTION_STOP_SERVICE"

        const val EXTRA_REPLY_TEXT = "extra_reply_text"
        const val EXTRA_ATTACHMENT_URI = "extra_attachment_uri"

        private val responseIdGenerator = AtomicInteger(10000)
        private val activeResponseIds = mutableListOf<Int>()

        private val chatHistory = mutableListOf<DisplayMessage>()
        var latestFullResponse: String = ""
            private set
        var latestModelUsed: String = ""
            private set
        var latestUserPrompt: String = ""
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

        fun copyText(context: Context, text: String) {
            val content = text.ifBlank { latestFullResponse }
            if (content.isBlank()) {
                Toast.makeText(context, "אין טקסט זמין להעתקה", Toast.LENGTH_SHORT).show()
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("NotifAI", content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "הטקסט הועתק ללוח! 📋", Toast.LENGTH_SHORT).show()
        }

        fun dismissResponseNotification(context: Context, notifId: Int) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
                synchronized(activeResponseIds) {
                    activeResponseIds.remove(notifId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
