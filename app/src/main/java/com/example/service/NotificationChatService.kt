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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
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
import com.example.util.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DisplayMessage(
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isBot: Boolean
)

class NotificationChatService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        AppStrings.initFromPrefs(this)
        isRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppStrings.initFromPrefs(this)
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                isRunning = false
                stopAudioPlaybackAndNotification()
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
                val conversationIdExtra = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L).takeIf { it > 0 }
                if (queryText.isNotBlank() || attachmentUri != null) {
                    processIncomingQuery(queryText, attachmentUri, conversationIdExtra)
                } else {
                    _isProcessingFlow.value = false
                    updateMainNotification(isThinking = false)
                }
            }
            ACTION_CLEAR_CHAT -> {
                // Task #12: Start a brand new conversation without history AND reset notification display
                synchronized(chatHistory) {
                    chatHistory.clear()
                    latestFullResponse = ""
                    latestModelUsed = ""
                    latestUserPrompt = ""
                    latestAttachmentUri = null
                    latestAttachmentMimeType = null
                    latestAttachmentName = null
                }
                serviceScope.launch {
                    try {
                        (application as? NotifAiApplication)?.aiRepository?.createNewConversation()
                    } catch (_: Exception) {}
                }
                updateMainNotification(isThinking = false)
            }
            ACTION_CLEAR_DISPLAY_ONLY -> {
                // Task #21: Clear ONLY the text/media currently displayed in the notification WITHOUT resetting the conversation!
                synchronized(chatHistory) {
                    latestFullResponse = ""
                    latestModelUsed = ""
                    latestUserPrompt = ""
                    latestAttachmentUri = null
                    latestAttachmentMimeType = null
                    latestAttachmentName = null
                }
                updateMainNotification(isThinking = false)
            }
            ACTION_PLAY_AUDIO_IN_NOTIFICATION -> {
                val mediaPath = intent.getStringExtra(EXTRA_ATTACHMENT_URI) ?: latestAttachmentUri
                if (!mediaPath.isNullOrBlank()) {
                    playAudioWithDismissibleNotification(mediaPath)
                }
            }
            ACTION_STOP_AUDIO_PLAYBACK -> {
                // Task #19: Allow closing and stopping the audio playback notification
                stopAudioPlaybackAndNotification()
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

    private fun processIncomingQuery(query: String, attachmentUri: Uri?, conversationId: Long? = null) {
        _isProcessingFlow.value = true
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
            isAudio && query.isBlank() -> AppStrings.tr("🎙️ [הקלטה קולית]", "🎙️ [Voice Recording]")
            isAudio -> AppStrings.tr("🎙️ [הקלטה] $query", "🎙️ [Voice] $query")
            isImage && query.isBlank() -> AppStrings.tr("📸 [צילום מסך / תמונה]", "📸 [Screenshot / Image]")
            isImage -> AppStrings.tr("📸 [תמונה] $query", "📸 [Image] $query")
            attachmentUri != null && query.isBlank() -> AppStrings.tr("📎 [קובץ מצורף]", "📎 [Attached File]")
            attachmentUri != null -> AppStrings.tr("📎 [קובץ] $query", "📎 [File] $query")
            else -> query
        }

        latestUserPrompt = displayUserText

        synchronized(chatHistory) {
            chatHistory.add(
                DisplayMessage(
                    senderName = AppStrings.tr("אתה", "You"),
                    text = displayUserText,
                    timestamp = userTimestamp,
                    isBot = false
                )
            )
        }

        updateMainNotification(isThinking = true)

        serviceScope.launch {
            val repository = (application as NotifAiApplication).aiRepository
            try {
                val fallbackPrompt = when {
                    isAudio -> AppStrings.tr(
                        "אנא האזן להקלטה הקולית המצורפת והשב על דברי המשתמש.",
                        "Please listen to the attached voice recording and reply to the user."
                    )
                    isImage -> AppStrings.tr(
                        "אנא נתח את צילום המסך או התמונה המצורפת.",
                        "Please analyze the attached screenshot or image."
                    )
                    else -> AppStrings.tr(
                        "אנא נתח את הקובץ המצורף",
                        "Please analyze the attached file"
                    )
                }
                val result: QueryResult = repository.askAi(
                    prompt = query.ifBlank { fallbackPrompt },
                    attachmentUri = attachmentUri,
                    conversationId = conversationId
                )

                val responseSnippet = buildString {
                    append(result.responseText)
                    if (result.fallbackOccurred) {
                        append(
                            "\n\n(ℹ️ ${result.fallbackReason ?: AppStrings.tr("מענה ממודל/מפתח חלופי", "Answered via fallback model/key")})"
                        )
                    }
                }

                latestFullResponse = responseSnippet
                latestModelUsed = "${result.provider.displayName} • ${result.model}"
                latestAttachmentUri = result.generatedAttachmentUri
                latestAttachmentMimeType = result.generatedAttachmentMimeType
                latestAttachmentName = result.generatedAttachmentName

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

                _isProcessingFlow.value = false
                updateMainNotification(isThinking = false)

                // If the response generated an audio file, automatically show the dismissible audio player notification
                if (latestAttachmentUri != null && latestAttachmentMimeType?.startsWith("audio/") == true) {
                    playAudioWithDismissibleNotification(latestAttachmentUri!!)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: AppStrings.tr("שגיאה לא צפויה", "Unexpected error")
                latestFullResponse = AppStrings.tr("⚠️ שגיאה בקבלת תשובה:\n$errorMsg", "⚠️ Error receiving response:\n$errorMsg")
                latestModelUsed = AppStrings.tr("שגיאה", "Error")
                latestAttachmentUri = null
                latestAttachmentMimeType = null
                latestAttachmentName = null

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

                _isProcessingFlow.value = false
                updateMainNotification(isThinking = false)
            }
        }
    }

    /**
     * Plays audio received from the AI model and displays a dedicated audio playback notification
     * that can be closed/dismissed via a clear Stop/Close button or swipe (Tasks #7 & #19).
     */
    private fun playAudioWithDismissibleNotification(pathOrUri: String) {
        try {
            stopMediaPlayerOnly()
            val player = MediaPlayer()
            if (pathOrUri.startsWith("/")) {
                player.setDataSource(pathOrUri)
            } else {
                player.setDataSource(this, Uri.parse(pathOrUri))
            }
            player.setOnCompletionListener {
                stopAudioPlaybackAndNotification()
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            isAudioPlaying = true
            _isAudioPlayingFlow.value = true

            val stopAudioIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_STOP_AUDIO_PLAYBACK
            }
            val stopAudioPendingIntent = PendingIntent.getBroadcast(
                this,
                201,
                stopAudioIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val downloadAudioIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DOWNLOAD_MEDIA
                putExtra(EXTRA_ATTACHMENT_URI, pathOrUri)
                putExtra(EXTRA_ATTACHMENT_MIME, latestAttachmentMimeType ?: "audio/wav")
                putExtra(EXTRA_ATTACHMENT_NAME, latestAttachmentName ?: "notifai_audio.wav")
            }
            val downloadPendingIntent = PendingIntent.getBroadcast(
                this,
                202,
                downloadAudioIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val audioNotif = NotificationCompat.Builder(this, CHANNEL_AUDIO_ID)
                .setSmallIcon(R.drawable.ic_stat_notifai_bell)
                .setContentTitle(AppStrings.tr("🔊 מנגן שמע מ-NotifAI", "🔊 Playing Audio from NotifAI"))
                .setContentText(
                    latestAttachmentName ?: AppStrings.tr(
                        "לחץ 'סגור שמע' לעצירה וסגירת ההתראה",
                        "Tap 'Close Audio' to stop playback and dismiss"
                    )
                )
                .setOngoing(false) // Allows swiping away to dismiss!
                .setAutoCancel(true)
                .setSilent(true)
                .setDeleteIntent(stopAudioPendingIntent) // Swiping away also stops the audio!
                .addAction(
                    android.R.drawable.ic_media_pause,
                    AppStrings.tr("⏹️ עצור וסגור התראת שמע", "⏹️ Stop & Close Audio Notification"),
                    stopAudioPendingIntent
                )
                .addAction(
                    android.R.drawable.stat_sys_download,
                    AppStrings.tr("📥 הורד קובץ שמע", "📥 Download Audio"),
                    downloadPendingIntent
                )
                .build()

            notificationManager.notify(AUDIO_NOTIFICATION_ID, audioNotif)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopMediaPlayerOnly() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isAudioPlaying = false
        _isAudioPlayingFlow.value = false
    }

    private fun stopAudioPlaybackAndNotification() {
        stopMediaPlayerOnly()
        try {
            notificationManager.cancel(AUDIO_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun updateMainNotification(isThinking: Boolean = false) {
        val notification = buildMainNotification(isThinking)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the SINGLE, highest-priority, silent, non-deletable notification.
     * Supports displaying generated images inline (BigPictureStyle) and media playback/download actions (Tasks #7 & #8).
     */
    private fun buildMainNotification(isThinking: Boolean): Notification {
        // 1. Inline Reply Action ("שאל AI")
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(AppStrings.tr("הקלד שאלה ל-NotifAI…", "Type a question for NotifAI…"))
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
            AppStrings.tr("שאל AI", "Ask AI"),
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
            AppStrings.tr("✨ שלח תוכן", "✨ Send Content"),
            attachDialogPendingIntent
        ).build()

        // 3. Actions Popup Dialog Action ("⚙️ פעולות")
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
            AppStrings.tr("⚙️ פעולות", "⚙️ Actions"),
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

        // Delete Intent -> Immediately restores the notification if the user tries to swipe it away
        val restoreIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESTORE_NOTIFICATION
        }
        val restorePendingIntent = PendingIntent.getBroadcast(
            this,
            108,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val outputMode = com.example.data.repository.AiRepository.getPreferredOutputType(this)
        val outputBadge = if (outputMode != com.example.data.model.AiTaskType.TEXT) {
            " [${outputMode.emoji} ${AppStrings.tr(outputMode.shortBadgeHe, outputMode.name)}]"
        } else ""

        val mainTitle: String
        val collapsedText: String
        val expandedBigText: String
        val summaryLine: String

        when {
            isThinking -> {
                mainTitle = AppStrings.tr("🤖 NotifAI מקליד תשובה…$outputBadge", "🤖 NotifAI is typing…$outputBadge")
                collapsedText = AppStrings.tr(
                    "מעבד את שאלתך: \"${latestUserPrompt.take(60)}\" ⏳",
                    "Processing your prompt: \"${latestUserPrompt.take(60)}\" ⏳"
                )
                expandedBigText = AppStrings.tr(
                    "שאלה: $latestUserPrompt\n\n⏳ NotifAI חושב ומנסח תשובה מלאה…",
                    "Prompt: $latestUserPrompt\n\n⏳ NotifAI is generating a complete answer…"
                )
                summaryLine = AppStrings.tr("מעבד תשובה…", "Processing response…")
            }
            latestFullResponse.isNotBlank() -> {
                mainTitle = if (latestModelUsed.isNotBlank()) {
                    "🤖 NotifAI ($latestModelUsed)$outputBadge"
                } else {
                    AppStrings.tr("🤖 NotifAI • תשובה אחרונה$outputBadge", "🤖 NotifAI • Latest Answer$outputBadge")
                }
                collapsedText = latestFullResponse.replace("\n", " ").take(120)
                expandedBigText = latestFullResponse
                summaryLine = if (latestAttachmentUri != null) {
                    AppStrings.tr(
                        "📎 מצורף קובץ מדיה (תמונה/שמע/וידאו/קובץ) • לחץ לצפייה או הורדה ב'פעולות'",
                        "📎 Media/File attached • Tap 'Actions' to view, play, or download"
                    )
                } else if (latestUserPrompt.isNotBlank()) {
                    AppStrings.tr(
                        "שאלה: ${latestUserPrompt.take(40)} • לחץ לשיחה המלאה",
                        "Prompt: ${latestUserPrompt.take(40)} • Tap for full chat"
                    )
                } else {
                    AppStrings.tr("לחץ לפתיחת הדו-שיח המלא", "Tap to open full conversation")
                }
            }
            else -> {
                mainTitle = AppStrings.tr("NotifAI • מרכז AI בהתראות$outputBadge", "NotifAI • Notification AI Hub$outputBadge")
                collapsedText = AppStrings.tr(
                    "הקלד שאלה ישירות בהתראה או לחץ 'שלח תוכן' ⚡",
                    "Type a question directly in the notification or tap 'Send Content' ⚡"
                )
                expandedBigText = AppStrings.tr(
                    "הקלד שאלה בשדה למטה ('שאל AI'), שלח צילום מסך/קול דרך 'שלח תוכן', או נהל מפתחות, מודלים וסוג פלט דרך 'פעולות'.",
                    "Type a question below ('Ask AI'), send screenshot/voice via 'Send Content', or manage keys, models & output via 'Actions'."
                )
                summaryLine = AppStrings.tr("התראה קבועה • לחץ לפתיחת הדו-שיח", "Persistent notification • Tap to open chat")
            }
        }

        val largeAvatar = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_custom_asset)
        } catch (_: Exception) { null }

        // Check if the latest AI response includes an image to display directly inside the notification (Task #7 & #37)
        val generatedImageBitmap: Bitmap? = if (!isThinking && latestAttachmentUri != null && latestAttachmentMimeType?.startsWith("image/") == true) {
            try {
                val primaryImagePath = latestAttachmentUri!!.substringBefore("|").trim()
                val file = File(primaryImagePath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            } catch (_: Exception) { null }
        } else null

        val builder = NotificationCompat.Builder(this, CHANNEL_MAIN_ID)
            .setSmallIcon(R.drawable.ic_stat_notifai_bell)
            .apply {
                if (generatedImageBitmap != null) {
                    setLargeIcon(generatedImageBitmap)
                    setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(generatedImageBitmap)
                            .setBigContentTitle(mainTitle)
                            .setSummaryText(collapsedText)
                    )
                } else {
                    if (largeAvatar != null) setLargeIcon(largeAvatar)
                    setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(mainTitle)
                            .bigText(expandedBigText)
                            .setSummaryText(summaryLine)
                    )
                }
            }
            .setContentTitle(mainTitle)
            .setContentText(collapsedText)
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

        return builder.build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                notificationManager.deleteNotificationChannel("notifai_chat_channel")
                notificationManager.deleteNotificationChannel("notifai_responses_channel")
            } catch (_: Exception) {}

            val mainChannel = NotificationChannel(
                CHANNEL_MAIN_ID,
                AppStrings.tr("התראת NotifAI קבועה ושקטה", "NotifAI Persistent Silent Notification"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = AppStrings.tr(
                    "התראה יחידה וקבועה בעדיפות עליונה ללא צליל או רטט",
                    "Single high-priority persistent notification without sound or vibration"
                )
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val audioChannel = NotificationChannel(
                CHANNEL_AUDIO_ID,
                AppStrings.tr("נגן שמע NotifAI (ניתן לסגירה)", "NotifAI Audio Player (Dismissible)"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = AppStrings.tr(
                    "התראת ניגון שמע שניתן לעצור ולסגור בכל עת",
                    "Audio playback notification that can be stopped and dismissed anytime"
                )
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(mainChannel)
            notificationManager.createNotificationChannel(audioChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        stopAudioPlaybackAndNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isRunning: Boolean = false
            internal set

        var isAudioPlaying: Boolean = false
            internal set

        private val _isAudioPlayingFlow = MutableStateFlow(false)
        val isAudioPlayingFlow: StateFlow<Boolean> = _isAudioPlayingFlow.asStateFlow()

        private val _isProcessingFlow = MutableStateFlow(false)
        val isProcessingFlow: StateFlow<Boolean> = _isProcessingFlow.asStateFlow()

        const val NOTIFICATION_ID = 9001
        const val AUDIO_NOTIFICATION_ID = 9002
        const val CHANNEL_MAIN_ID = "notifai_single_silent_max_v3"
        const val CHANNEL_AUDIO_ID = "notifai_audio_player_channel_v1"

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_PROCESS_REPLY = "com.example.notifai.ACTION_PROCESS_REPLY"
        const val ACTION_CLEAR_CHAT = "com.example.notifai.ACTION_CLEAR_CHAT"
        const val ACTION_CLEAR_DISPLAY_ONLY = "com.example.notifai.ACTION_CLEAR_DISPLAY_ONLY"
        const val ACTION_STOP_SERVICE = "com.example.notifai.ACTION_STOP_SERVICE"
        const val ACTION_PLAY_AUDIO_IN_NOTIFICATION = "com.example.notifai.ACTION_PLAY_AUDIO_IN_NOTIFICATION"
        const val ACTION_STOP_AUDIO_PLAYBACK = "com.example.notifai.ACTION_STOP_AUDIO_PLAYBACK"

        const val EXTRA_REPLY_TEXT = "extra_reply_text"
        const val EXTRA_ATTACHMENT_URI = "extra_attachment_uri"
        const val EXTRA_ATTACHMENT_MIME = "extra_attachment_mime"
        const val EXTRA_ATTACHMENT_NAME = "extra_attachment_name"
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"

        private val chatHistory = mutableListOf<DisplayMessage>()
        var latestFullResponse: String = ""
            private set
        var latestModelUsed: String = ""
            private set
        var latestUserPrompt: String = ""
            private set
        var latestAttachmentUri: String? = null
            private set
        var latestAttachmentMimeType: String? = null
            private set
        var latestAttachmentName: String? = null
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

        fun sendQueryToService(
            context: Context,
            query: String,
            attachmentUri: Uri? = null,
            conversationId: Long? = null
        ) {
            try {
                context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_enabled", true)
                    .apply()
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_PROCESS_REPLY
                    putExtra(EXTRA_REPLY_TEXT, query)
                    putExtra(EXTRA_ATTACHMENT_URI, attachmentUri?.toString())
                    if (conversationId != null) {
                        putExtra(EXTRA_CONVERSATION_ID, conversationId)
                    }
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
            AppStrings.initFromPrefs(context)
            val content = text.ifBlank { latestFullResponse }
            if (content.isBlank()) {
                Toast.makeText(
                    context,
                    AppStrings.tr("אין עדיין תשובה זמינה להעתקה", "No response available to copy yet"),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("NotifAI", content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                context,
                AppStrings.tr("התשובה הועתקה ללוח! 📋", "Response copied to clipboard! 📋"),
                Toast.LENGTH_SHORT
            ).show()
        }

        fun dismissResponseNotification(context: Context, notifId: Int) {
            // Single notification architecture
        }

        /**
         * Task #12: Starts a new chat without history AND clears the notification.
         */
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

        /**
         * Task #21: Clears ONLY the text displayed in the notification without starting a new chat.
         */
        fun clearNotificationDisplayOnly(context: Context) {
            try {
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_CLEAR_DISPLAY_ONLY
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun playAudioFromNotification(context: Context, uriStr: String? = latestAttachmentUri) {
            if (uriStr.isNullOrBlank()) return
            try {
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_PLAY_AUDIO_IN_NOTIFICATION
                    putExtra(EXTRA_ATTACHMENT_URI, uriStr)
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopAudioPlayback(context: Context) {
            try {
                val intent = Intent(context, NotificationChatService::class.java).apply {
                    action = ACTION_STOP_AUDIO_PLAYBACK
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
