package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.NotifAiApplication
import com.example.data.model.AiModelInfo
import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.repository.AiRepository
import com.example.service.FastScreenCaptureAccessibilityService
import com.example.service.NotificationChatService
import com.example.ui.components.MessageAttachmentView
import com.example.ui.dialogs.AddKeyDialog
import com.example.ui.theme.NotifAiTheme
import com.example.util.AppStrings
import com.example.util.MediaStorageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class AttachDialogMode {
    MENU,
    ACTIONS,
    VOICE_RECORD,
    SPEECH_TO_TEXT,
    FILE_SELECTED
}

class AttachContentDialogActivity : ComponentActivity() {

    private var initialModeState by mutableStateOf(AttachDialogMode.MENU)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.initFromPrefs(this)
        initialModeState = resolveModeFromIntent(intent)

        setContent {
            val currentLang = AppStrings.langOverrideState
            NotifAiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides AppStrings.layoutDirection()) {
                    AttachContentDialogScreen(
                        initialMode = initialModeState,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialModeState = resolveModeFromIntent(intent)
    }

    private fun resolveModeFromIntent(intent: Intent?): AttachDialogMode {
        return when (intent?.getStringExtra(EXTRA_INITIAL_MODE)) {
            MODE_ACTIONS -> AttachDialogMode.ACTIONS
            else -> AttachDialogMode.MENU
        }
    }

    companion object {
        const val EXTRA_INITIAL_MODE = "extra_initial_mode"
        const val MODE_ATTACH = "mode_attach"
        const val MODE_ACTIONS = "mode_actions"
    }
}

@Composable
fun AttachContentDialogScreen(
    initialMode: AttachDialogMode = AttachDialogMode.MENU,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }
    var showCloseNotificationConfirm by remember { mutableStateOf(false) }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSizeStr by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val meta = getFileMetadata(context, uri)
            selectedFileName = meta.first
            selectedFileSizeStr = meta.second
            currentMode = AttachDialogMode.FILE_SELECTED
        }
    }

    if (showCloseNotificationConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseNotificationConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = AppStrings.tr("סגירת התראת NotifAI", "Close NotifAI Notification"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = AppStrings.tr(
                        "האם אתה בטוח שברצונך לסגור ולהסיר את התראת NotifAI מווילון ההתראות?\n\nהתראה זו תופעל שוב אוטומטית בכניסה הבאה לאפליקציה.",
                        "Are you sure you want to close and remove the NotifAI notification from the notification shade?\n\nIt will automatically activate again next time you open the app."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseNotificationConfirm = false
                        NotificationChatService.stopService(context)
                        Toast.makeText(
                            context,
                            AppStrings.tr("התראת NotifAI נסגרה", "NotifAI notification closed"),
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_close_notification_btn")
                ) {
                    Text(AppStrings.tr("כן, סגור התראה", "Yes, Close Notification"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCloseNotificationConfirm = false },
                    modifier = Modifier.testTag("cancel_close_notification_btn")
                ) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }

    BackHandler {
        if (currentMode != initialMode && currentMode != AttachDialogMode.MENU && currentMode != AttachDialogMode.ACTIONS) {
            currentMode = initialMode
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 660.dp)
                .fillMaxWidth(0.96f)
                .padding(horizontal = 6.dp, vertical = 12.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentMode != AttachDialogMode.MENU && currentMode != AttachDialogMode.ACTIONS) {
                            IconButton(
                                onClick = { currentMode = AttachDialogMode.MENU },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("attach_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = AppStrings.tr("חזרה לתפריט", "Back to menu")
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (currentMode) {
                                    AttachDialogMode.MENU -> Icons.Default.AttachFile
                                    AttachDialogMode.ACTIONS -> Icons.Default.Settings
                                    AttachDialogMode.VOICE_RECORD -> Icons.Default.Mic
                                    AttachDialogMode.SPEECH_TO_TEXT -> Icons.Default.RecordVoiceOver
                                    AttachDialogMode.FILE_SELECTED -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = when (currentMode) {
                                    AttachDialogMode.MENU -> AppStrings.tr("שלח תוכן ל-NotifAI", "Send Content to NotifAI")
                                    AttachDialogMode.ACTIONS -> AppStrings.tr("פעולות, פלט וניהול מפתחות", "Actions, Output & Key Management")
                                    AttachDialogMode.VOICE_RECORD -> AppStrings.tr("הקלטת קול מיידית", "Instant Voice Recording")
                                    AttachDialogMode.SPEECH_TO_TEXT -> AppStrings.tr("דיבור לטקסט (שליחה מיידית)", "Speech-to-Text (Instant Send)")
                                    AttachDialogMode.FILE_SELECTED -> AppStrings.tr("העלאת קובץ מצורף", "Upload Attachment")
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (currentMode) {
                                    AttachDialogMode.MENU -> AppStrings.tr("בחר כיצד להזין תוכן ל-AI", "Choose how to send content to AI")
                                    AttachDialogMode.ACTIONS -> AppStrings.tr("העתקה, ניקוי, שיחה חדשה, סוג פלט ומפתחות", "Copy, clear, new chat, output type & keys")
                                    AttachDialogMode.VOICE_RECORD -> AppStrings.tr("ההקלטה מתחילה מיד עם הפתיחה", "Recording starts immediately on open")
                                    AttachDialogMode.SPEECH_TO_TEXT -> AppStrings.tr("מקשיב ושולח מיד (לחץ לעריכה לביטול שליחה אוטומטית)", "Listens & sends immediately (tap to edit)")
                                    AttachDialogMode.FILE_SELECTED -> AppStrings.tr("אישור ושליחה", "Confirm & send")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("attach_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = AppStrings.tr("סגור", "Close"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                AnimatedContent(
                    targetState = currentMode,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "attach_mode_content"
                ) { mode ->
                    when (mode) {
                        AttachDialogMode.MENU -> {
                            AttachMenuOptions(
                                onScreenshotClick = {
                                    onDismiss()
                                    val fastService = FastScreenCaptureAccessibilityService.instance
                                    if (fastService != null) {
                                        fastService.captureNow()
                                    } else {
                                        val captureIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        }
                                        context.startActivity(captureIntent)
                                    }
                                },
                                onVoiceRecordClick = {
                                    currentMode = AttachDialogMode.VOICE_RECORD
                                },
                                onSpeechToTextClick = {
                                    currentMode = AttachDialogMode.SPEECH_TO_TEXT
                                },
                                onFileUploadClick = {
                                    try {
                                        filePickerLauncher.launch("*/*")
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            AppStrings.tr("שגיאה בפתיחת בורר הקבצים", "Error opening file picker"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }

                        AttachDialogMode.ACTIONS -> {
                            ActionsPopupSection(
                                onCloseNotificationClick = {
                                    showCloseNotificationConfirm = true
                                },
                                onDismissPopup = onDismiss
                            )
                        }

                        AttachDialogMode.VOICE_RECORD -> {
                            VoiceRecordSection(
                                onSendAudio = { audioUri, prompt ->
                                    NotificationChatService.sendQueryToService(
                                        context = context,
                                        query = prompt,
                                        attachmentUri = audioUri
                                    )
                                    Toast.makeText(
                                        context,
                                        AppStrings.tr("ההקלטה נשלחה ל-NotifAI 🎙️", "Recording sent to NotifAI 🎙️"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                },
                                onCancel = {
                                    currentMode = AttachDialogMode.MENU
                                }
                            )
                        }

                        AttachDialogMode.SPEECH_TO_TEXT -> {
                            HebrewSpeechToTextSection(
                                onSendText = { text ->
                                    if (text.isNotBlank()) {
                                        NotificationChatService.sendQueryToService(
                                            context = context,
                                            query = text
                                        )
                                        Toast.makeText(
                                            context,
                                            AppStrings.tr("השאלה נשלחה מיד ל-NotifAI! 🚀", "Question sent immediately to NotifAI! 🚀"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onDismiss()
                                    }
                                },
                                onCancel = {
                                    currentMode = AttachDialogMode.MENU
                                }
                            )
                        }

                        AttachDialogMode.FILE_SELECTED -> {
                            FileSelectedSection(
                                fileName = selectedFileName,
                                fileSizeStr = selectedFileSizeStr,
                                fileUri = selectedFileUri,
                                onSendFile = { uri, prompt ->
                                    NotificationChatService.sendQueryToService(
                                        context = context,
                                        query = prompt,
                                        attachmentUri = uri
                                    )
                                    Toast.makeText(
                                        context,
                                        AppStrings.tr("הקובץ נשלח ל-NotifAI! 📎", "File sent to NotifAI! 📎"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                },
                                onChangeFile = {
                                    try {
                                        filePickerLauncher.launch("*/*")
                                    } catch (_: Exception) {}
                                },
                                onCancel = {
                                    currentMode = AttachDialogMode.MENU
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachMenuOptions(
    onScreenshotClick: () -> Unit,
    onVoiceRecordClick: () -> Unit,
    onSpeechToTextClick: () -> Unit,
    onFileUploadClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AttachOptionCard(
            title = AppStrings.tr("📸 צילום מסך", "📸 Screenshot"),
            subtitle = AppStrings.tr("צלם את מה שמוצג כעת במסך ונתח עם AI", "Capture what's on screen now and analyze with AI"),
            badgeText = AppStrings.tr("מיידי", "Instant"),
            icon = Icons.Default.CameraAlt,
            accentColor = Color(0xFF0284C7),
            testTag = "attach_option_screenshot",
            onClick = onScreenshotClick
        )

        AttachOptionCard(
            title = AppStrings.tr("🎙️ הקלטת קול (מתחיל מיד)", "🎙️ Voice Recording (Starts Immediately)"),
            subtitle = AppStrings.tr("מתחיל להקליט מיד עם הכניסה ושולח כקובץ קולי למודל", "Starts recording immediately and sends audio to AI"),
            badgeText = AppStrings.tr("שמע", "Audio"),
            icon = Icons.Default.Mic,
            accentColor = Color(0xFFE11D48),
            testTag = "attach_option_record",
            onClick = onVoiceRecordClick
        )

        AttachOptionCard(
            title = AppStrings.tr("🗣️ דיבור לטקסט (שליחה מיידית)", "🗣️ Speech-to-Text (Instant Send)"),
            subtitle = AppStrings.tr("מאזין מיד ושולח מיד בסיום הדיבור (אלא אם תלחץ לערוך)", "Listens immediately and auto-sends unless you tap to edit"),
            badgeText = AppStrings.tr("אוטומטי", "Auto"),
            icon = Icons.Default.RecordVoiceOver,
            accentColor = Color(0xFF7C3AED),
            testTag = "attach_option_stt",
            onClick = onSpeechToTextClick
        )

        AttachOptionCard(
            title = AppStrings.tr("📁 העלאת קובץ", "📁 Upload File"),
            subtitle = AppStrings.tr("תמונה, מסמך, PDF, קוד או שמע מהמכשיר", "Image, document, PDF, code, or audio from device"),
            badgeText = AppStrings.tr("מסמכים", "Files"),
            icon = Icons.Default.AttachFile,
            accentColor = Color(0xFF059669),
            testTag = "attach_option_file",
            onClick = onFileUploadClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActionsPopupSection(
    onCloseNotificationClick: () -> Unit,
    onDismissPopup: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { (context.applicationContext as NotifAiApplication).aiRepository }
    val allKeys by repository.allKeysFlow.collectAsState(initial = emptyList())
    val sortedKeys = remember(allKeys) { allKeys.sortedBy { it.priority } }
    val isAudioPlayingInNotif by NotificationChatService.isAudioPlayingFlow.collectAsState()

    var preferredOutputType by remember {
        mutableStateOf(AiRepository.getPreferredOutputType(context))
    }

    var showPerTaskRoutingSection by remember { mutableStateOf(false) }
    var taskRoutingRefreshTick by remember { mutableIntStateOf(0) }

    var showAddOrEditKeyDialog by remember { mutableStateOf(false) }
    var keyBeingEdited by remember { mutableStateOf<ApiKeyEntity?>(null) }

    // State for quick model selector dialog for a specific key
    var keyForQuickModelSelect by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var quickModelsList by remember { mutableStateOf<List<AiModelInfo>>(emptyList()) }
    var isFetchingQuickModels by remember { mutableStateOf(false) }
    var quickModelSearchQuery by remember { mutableStateOf("") }

    val latestMediaUri = NotificationChatService.latestAttachmentUri
    val latestMediaMime = NotificationChatService.latestAttachmentMimeType
    val latestMediaName = NotificationChatService.latestAttachmentName

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Copy & Share Row
        Text(
            text = AppStrings.tr("פעולות על התשובה והשיחה", "Response & Conversation Actions"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val text = NotificationChatService.latestFullResponse.ifBlank {
                            repository.getLatestAssistantResponse()
                        }
                        if (text.isBlank()) {
                            Toast.makeText(
                                context,
                                AppStrings.tr("אין עדיין תשובה זמינה להעתקה", "No response available to copy yet"),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            NotificationChatService.copyText(context, text)
                            onDismissPopup()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("actions_copy_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("העתק", "Copy"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val text = NotificationChatService.latestFullResponse.ifBlank {
                            repository.getLatestAssistantResponse()
                        }
                        if (text.isBlank()) {
                            Toast.makeText(
                                context,
                                AppStrings.tr("אין עדיין תשובה זמינה לשיתוף", "No response available to share yet"),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            val chooser = Intent.createChooser(
                                shareIntent,
                                AppStrings.tr("שתף תשובה מ-NotifAI", "Share response from NotifAI")
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(chooser)
                            onDismissPopup()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("actions_share_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("שתף", "Share"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Row 2: Clear Display Only ('נקה') vs Start New Chat ('התחל שיחה חדשה') (Tasks #12 & #21)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    NotificationChatService.clearNotificationDisplayOnly(context)
                    Toast.makeText(
                        context,
                        AppStrings.tr(
                            "תצוגת ההתראה נוקתה (השיחה וההיסטוריה נשמרו) 🧹",
                            "Notification display cleared (chat history preserved) 🧹"
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismissPopup()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("actions_clear_display_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("נקה", "Clear"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            FilledTonalButton(
                onClick = {
                    scope.launch {
                        repository.createNewConversation()
                        NotificationChatService.clearAllResponses(context)
                        Toast.makeText(
                            context,
                            AppStrings.tr(
                                "התחלת שיחה חדשה ללא היסטוריה קודמת! 🆕",
                                "Started a new conversation without history! 🆕"
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismissPopup()
                    }
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .testTag("actions_start_new_chat_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("התחל שיחה חדשה", "Start New Chat"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Row 2B: Direct Download of Latest Response as a File + Stop Audio Notification if active (Tasks #8 & #19)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (!latestMediaUri.isNullOrBlank()) {
                            val saved = MediaStorageManager.saveFileToPublicDownloads(
                                context = context,
                                pathOrUriStr = latestMediaUri,
                                mimeType = latestMediaMime,
                                suggestedFileName = latestMediaName
                            )
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
                        } else {
                            val text = NotificationChatService.latestFullResponse.ifBlank {
                                repository.getLatestAssistantResponse()
                            }
                            if (text.isBlank()) {
                                Toast.makeText(
                                    context,
                                    AppStrings.tr("אין עדיין תוכן להורדה", "No content available to download yet"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val textFile = MediaStorageManager.createDownloadableTextFile(
                                    context = context,
                                    content = text,
                                    prefix = "notifai_response"
                                )
                                val saved = if (textFile != null) {
                                    MediaStorageManager.saveFileToPublicDownloads(
                                        context = context,
                                        pathOrUriStr = textFile.first,
                                        mimeType = textFile.second,
                                        suggestedFileName = textFile.third
                                    )
                                } else null
                                if (saved != null) {
                                    Toast.makeText(
                                        context,
                                        AppStrings.tr(
                                            "התשובה נשמרה כקובץ בתיקיית ההורדות: $saved 📥",
                                            "Response saved to Downloads/NotifAI: $saved 📥"
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("actions_download_file_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = AppStrings.tr("הורד תשובה / קובץ למכשיר", "Download Response / File"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isAudioPlayingInNotif || latestMediaMime?.startsWith("audio/") == true) {
                Button(
                    onClick = {
                        NotificationChatService.stopAudioPlayback(context)
                        Toast.makeText(
                            context,
                            AppStrings.tr("ניגון השמע והתראת השמע נסגרו ⏹️", "Audio playback & notification closed ⏹️"),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("actions_stop_audio_notif_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.tr("סגור התראת שמע", "Close Audio Notif"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Latest Media Preview / Play / Download / Stop Audio Notification (Tasks #7, #8, #19)
        if (!latestMediaUri.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.tr(
                            "מדיה / קובץ מהתשובה האחרונה בהתראה (תמונה / וידאו / שמע / קובץ)",
                            "Media / File from Latest Notification Response"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    MessageAttachmentView(
                        attachmentUri = latestMediaUri,
                        attachmentMimeType = latestMediaMime,
                        attachmentName = latestMediaName,
                        isUserBubble = false
                    )

                    if (latestMediaMime?.startsWith("audio/") == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    NotificationChatService.playAudioFromNotification(context, latestMediaUri)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(AppStrings.tr("נגן שמע בהתראה", "Play in Notification"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    NotificationChatService.stopAudioPlayback(context)
                                    Toast.makeText(
                                        context,
                                        AppStrings.tr("ניגון השמע והתראת השמע נסגרו ⏹️", "Audio playback & notification closed ⏹️"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(AppStrings.tr("סגור התראת שמע", "Close Audio Notif"), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 2. Output Type Selector (Task #16: ברירת מחדל = טקסט, או תמונה, סאונד, וידאו, קובץ)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = AppStrings.tr(
                    "סוג פלט מבוקש למענה הבא (ברירת מחדל: טקסט)",
                    "Requested Output Type for Next Reply (Default: Text)"
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = AppStrings.tr(
                    "בחר איזה סוג תוצר תרצה לקבל מה-AI בשאלה הבאה מתוך ההתראה:",
                    "Select what type of output you want from AI on your next prompt:"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val outputOptions = listOf(
                AiTaskType.TEXT to AppStrings.tr("📝 טקסט (ברירת מחדל)", "📝 Text (Default)"),
                AiTaskType.IMAGE_GEN to AppStrings.tr("🎨 תמונה", "🎨 Image"),
                AiTaskType.AUDIO_GEN to AppStrings.tr("🔊 סאונד / שמע", "🔊 Sound / Audio"),
                AiTaskType.VIDEO_GEN to AppStrings.tr("🎬 וידאו", "🎬 Video"),
                AiTaskType.FILE_INPUT to AppStrings.tr("📎 קובץ", "📎 File")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                outputOptions.forEach { (taskType, label) ->
                    val selected = preferredOutputType == taskType
                    FilterChip(
                        selected = selected,
                        onClick = {
                            preferredOutputType = taskType
                            AiRepository.setPreferredOutputType(context, taskType)
                            NotificationChatService.startService(context)
                            Toast.makeText(
                                context,
                                AppStrings.tr("סוג הפלט המבוקש הוגדר ל: $label", "Output type set to: $label"),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("output_type_chip_${taskType.name}")
                    )
                }
            }

            // Built-in Pollinations Image Generator Controls (Ratio 1:1/3:4/4:3/9:16/16:9, Count 1-4, Auto-Translate, Modesty & Filtering)
            com.example.ui.components.PollinationsConfigCard(
                modifier = Modifier.padding(top = 6.dp),
                initiallyExpanded = preferredOutputType == AiTaskType.IMAGE_GEN,
                hasActiveGeminiKeyInRepo = allKeys.any { it.provider == com.example.data.model.ProviderType.GEMINI && it.isEnabled },
                onModelChanged = { chosenModel ->
                    val builtIn = allKeys.find { it.id == AiRepository.BUILTIN_POLLINATIONS_KEY_ID || it.provider == com.example.data.model.ProviderType.POLLINATIONS }
                    if (builtIn != null && repository != null) {
                        scope.launch {
                            repository.updateKeyEntity(builtIn.copy(selectedModel = chosenModel))
                        }
                    }
                }
            )
        }

        // Per-Task Dedicated Key & Model Routing directly inside 'פעולות' (Task #2)
        if (sortedKeys.isNotEmpty()) {
            OutlinedButton(
                onClick = { showPerTaskRoutingSection = !showPerTaskRoutingSection },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showPerTaskRoutingSection) {
                        AppStrings.tr("הסתר ניתוב מפתח ומודל לפי סוג בקשה", "Hide Per-Task Key & Model Routing")
                    } else {
                        AppStrings.tr("הגדר מפתח ומודל שונה לכל סוג בקשה (טקסט, תמונה, סאונד, וידאו, קובץ)", "Set Separate Key & Model per Request Type")
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = showPerTaskRoutingSection) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentTick = taskRoutingRefreshTick
                        var taskTypeForCenteredMenu by remember { mutableStateOf<AiTaskType?>(null) }

                        AiTaskType.entries.forEach { taskType ->
                            val assignedKeyId = remember(currentTick, allKeys) {
                                AiRepository.getDedicatedKeyIdForTask(context, taskType)
                            }
                            val assignedKey = sortedKeys.find { it.id == assignedKeyId }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${taskType.emoji} ${AppStrings.tr(taskType.titleHe, taskType.name)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.45f)
                                )
                                Box(modifier = Modifier.weight(0.55f)) {
                                    OutlinedButton(
                                        onClick = { taskTypeForCenteredMenu = taskType },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (assignedKey != null) {
                                                "${assignedKey.name} (${assignedKey.selectedModel.substringAfterLast("/")})"
                                            } else {
                                                AppStrings.tr("אוטומטי (לפי עדיפות)", "Auto (By Priority)")
                                            },
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        val activeTaskMenu = taskTypeForCenteredMenu
                        if (activeTaskMenu != null) {
                            val assignedKeyId = AiRepository.getDedicatedKeyIdForTask(context, activeTaskMenu)
                            val currentSelection = sortedKeys.find { it.id == assignedKeyId }
                            val menuOptions = listOf(
                                com.example.ui.components.CenteredMenuOption<ApiKeyEntity?>(
                                    value = null,
                                    title = AppStrings.tr("⚡ אוטומטי (לפי סדר עדיפויות)", "⚡ Auto (By Priority Order)"),
                                    subtitle = AppStrings.tr("ישתמש במפתח הפעיל הראשון שתומך במשימה", "Uses the first enabled key supporting this task")
                                )
                            ) + sortedKeys.map { k ->
                                com.example.ui.components.CenteredMenuOption<ApiKeyEntity?>(
                                    value = k,
                                    title = "${k.name} • ${k.provider.displayName.substringBefore(" ")}",
                                    subtitle = "${AppStrings.tr("מודל:", "Model:")} ${k.selectedModel}"
                                )
                            }
                            com.example.ui.components.CenteredSelectionMenuDialog(
                                title = "${activeTaskMenu.emoji} ${AppStrings.tr(activeTaskMenu.titleHe, activeTaskMenu.name)}",
                                subtitle = AppStrings.tr("בחר מפתח ומודל ייעודיים עבור סוג בקשה זה", "Select dedicated key & model for this request type"),
                                options = menuOptions,
                                selectedValue = currentSelection,
                                onSelect = { chosenKey ->
                                    if (chosenKey == null) {
                                        AiRepository.setDedicatedKeyIdForTask(context, activeTaskMenu, null)
                                    } else {
                                        AiRepository.setDedicatedKeyIdForTask(context, activeTaskMenu, chosenKey.id)
                                        scope.launch {
                                            val updatedTasks = chosenKey.parsedSupportedTasks + activeTaskMenu
                                            repository.updateKeyEntity(
                                                chosenKey.copy(supportedTasks = AiTaskType.toCsv(updatedTasks), isEnabled = true)
                                            )
                                        }
                                    }
                                    taskRoutingRefreshTick++
                                },
                                onDismiss = { taskTypeForCenteredMenu = null }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 3. Direct Model Selection, Key Toggle ON/OFF, Duplicate, Drag Reorder & Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppStrings.tr("כיבוי/הדלקה, גרירת סדר עדיפויות ובחירת מודל", "Toggle Keys, Drag Priority & Select Model"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tr(
                        "גרור מפתחות לשינוי עדיפות, הדלק/כבה, שכפל או החלף מודל",
                        "Drag keys to reorder priority, toggle ON/OFF, duplicate or switch model"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = {
                    keyBeingEdited = null
                    showAddOrEditKeyDialog = true
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("actions_add_key_btn"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(AppStrings.tr("הוסף מפתח", "Add Key"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (sortedKeys.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.tr("לא הוגדרו מפתחות API עדיין", "No API keys configured yet"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            keyBeingEdited = null
                            showAddOrEditKeyDialog = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.tr("הוסף מפתח ובחר מודל", "Add Key & Select Model"))
                    }
                }
            }
        } else {
            val firstEnabledId = sortedKeys.firstOrNull { it.isEnabled }?.id
            com.example.ui.components.DraggableReorderColumn(
                items = sortedKeys,
                keySelector = { it.id },
                onReorderFinished = { newOrder ->
                    scope.launch {
                        repository.reorderKeys(newOrder.map { it.id })
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { _, keyEntity, isDragging, dragHandleModifier ->
                val isPrimary = keyEntity.id == firstEnabledId && keyEntity.isEnabled
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragging) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        } else if (isPrimary) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isPrimary) 1.5.dp else 0.5.dp,
                            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                com.example.ui.components.DragHandleBadge(
                                    priorityNumber = keyEntity.priority,
                                    isDragging = isDragging,
                                    dragHandleModifier = dragHandleModifier
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isPrimary) AppStrings.tr("פעיל #1", "Active #1") else "#${keyEntity.priority}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = keyEntity.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${keyEntity.provider.displayName} • ${keyEntity.parsedSupportedTasks.joinToString(", ") { it.emoji }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Switch to turn keys ON / OFF directly from 'פעולות'
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Switch(
                                    checked = keyEntity.isEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            repository.updateKeyEntity(keyEntity.copy(isEnabled = checked))
                                            val msg = if (checked) {
                                                AppStrings.tr("המפתח '${keyEntity.name}' הופעל ✅", "Key '${keyEntity.name}' enabled ✅")
                                            } else {
                                                AppStrings.tr("המפתח '${keyEntity.name}' כובה ⏸️", "Key '${keyEntity.name}' disabled ⏸️")
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("actions_key_switch_${keyEntity.id}"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val copy = repository.duplicateKey(keyEntity.id)
                                            if (copy != null) {
                                                Toast.makeText(
                                                    context,
                                                    AppStrings.tr(
                                                        "שוכפל כ-'${copy.name}' עם מודל ${copy.selectedModel}",
                                                        "Duplicated as '${copy.name}' with model ${copy.selectedModel}"
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("actions_duplicate_key_btn_${keyEntity.id}")
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = AppStrings.tr("שכפל מפתח", "Duplicate key"),
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        keyBeingEdited = keyEntity
                                        showAddOrEditKeyDialog = true
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("edit_key_btn_${keyEntity.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = AppStrings.tr("ערוך מפתח ומודל", "Edit key & model"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Clickable Model Selector + Set as Primary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    keyForQuickModelSelect = keyEntity
                                    quickModelSearchQuery = ""
                                    quickModelsList = AiModelInfo.sortBestFirst(
                                        keyEntity.provider.suggestedModels.map {
                                            AiModelInfo(it, it, isRecommended = true)
                                        }
                                    )
                                    isFetchingQuickModels = true
                                    scope.launch {
                                        val res = repository.fetchModelsForKeyId(keyEntity.id)
                                        if (res.models.isNotEmpty()) {
                                            quickModelsList = AiModelInfo.sortBestFirst(res.models)
                                        }
                                        isFetchingQuickModels = false
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_model_picker_btn_${keyEntity.id}"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Psychology,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = keyEntity.selectedModel,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (!isPrimary) {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            repository.setKeyAsPrimary(keyEntity.id)
                                            Toast.makeText(
                                                context,
                                                AppStrings.tr(
                                                    "הוגדר כראשי (#1): ${keyEntity.selectedModel} ⚡",
                                                    "Set as Primary (#1): ${keyEntity.selectedModel} ⚡"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("set_primary_key_btn_${keyEntity.id}"),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(AppStrings.tr("קבע כראשי", "Set Primary"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 4. Close Notification Button
        AttachOptionCard(
            title = AppStrings.tr("🛑 סגור התראה", "🛑 Close Notification"),
            subtitle = AppStrings.tr("הסר את התראת NotifAI מווילון ההתראות", "Remove NotifAI notification from the notification shade"),
            badgeText = AppStrings.tr("הסרה", "Remove"),
            icon = Icons.Default.Close,
            accentColor = Color(0xFFDC2626),
            testTag = "actions_close_notification_btn",
            onClick = onCloseNotificationClick
        )
    }

    // Quick Model Picker + Fallback Toggle + Model Type Filter + Drag Reorder Modal
    val targetKey = keyForQuickModelSelect
    if (targetKey != null) {
        var selectedPrimaryModel by remember(targetKey.id) { mutableStateOf(targetKey.selectedModel) }
        var enabledFallbacks by remember(targetKey.id) {
            val initList = targetKey.fallbackModels
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != targetKey.selectedModel }
            mutableStateOf(initList)
        }
        var selectedKeyTasks by remember(targetKey.id) {
            mutableStateOf(targetKey.parsedSupportedTasks)
        }
        var selectedModelFilter by remember(targetKey.id) {
            mutableStateOf(com.example.data.model.ModelFilterType.ALL)
        }

        val freeModelsTotalCount = remember(quickModelsList) {
            quickModelsList.count { it.effectivelyFree }
        }

        val filteredModels = remember(quickModelsList, quickModelSearchQuery, selectedModelFilter) {
            val byFilter = quickModelsList.filter { it.matchesFilter(selectedModelFilter) }
            AiModelInfo.sortBestFirst(
                if (quickModelSearchQuery.isBlank()) {
                    byFilter
                } else {
                    byFilter.filter {
                        it.id.contains(quickModelSearchQuery, ignoreCase = true) ||
                            it.displayName.contains(quickModelSearchQuery, ignoreCase = true) ||
                            it.roleBadge.contains(quickModelSearchQuery, ignoreCase = true)
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { keyForQuickModelSelect = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.tr("בחירת מודלים וסוגי משימות • ${targetKey.name}", "Select Models & Tasks • ${targetKey.name}"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.tr(
                                "${quickModelsList.size} מודלים ($freeModelsTotalCount חינמיים 🆓 בראש הרשימה)",
                                "${quickModelsList.size} models ($freeModelsTotalCount FREE 🆓 sorted first)"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isFetchingQuickModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Direct API Key Webpage Link (Task #4)
                    OutlinedButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetKey.provider.apiKeysWebUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.tr(
                                "🔗 עמוד מפתחות ה-API של ${targetKey.provider.displayName.substringBefore(" ")}",
                                "🔗 ${targetKey.provider.displayName.substringBefore(" ")} API Keys Page"
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Task Types for this Model/Key (Task #6)
                    Text(
                        text = AppStrings.tr("סוגי בקשות שמודל זה מטפל בהם:", "Task types handled by this model:"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiTaskType.entries.forEach { task ->
                            val isSel = task in selectedKeyTasks
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedKeyTasks = if (isSel && selectedKeyTasks.size > 1) {
                                        selectedKeyTasks - task
                                    } else {
                                        selectedKeyTasks + task
                                    }
                                },
                                label = {
                                    Text("${task.emoji} ${AppStrings.tr(task.shortBadgeHe, task.name)}", fontSize = 10.sp)
                                },
                                leadingIcon = if (isSel) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null
                            )
                        }
                    }

                    // Filter Models by Type (Free, Text, Vision, Image Gen, Audio, Video, Files)
                    Text(
                        text = AppStrings.tr("סינון מודלים לפי סוג (חינמיים, תמונה, שמע, וידאו...):", "Filter models by type (Free, Vision, Audio, Video...):"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.data.model.ModelFilterType.entries.forEach { filterType ->
                            val count = remember(quickModelsList, filterType) {
                                quickModelsList.count { it.matchesFilter(filterType) }
                            }
                            if (filterType == com.example.data.model.ModelFilterType.ALL || count > 0) {
                                val isSel = selectedModelFilter == filterType
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedModelFilter = filterType },
                                    label = {
                                        Text(
                                            "${filterType.emoji} ${AppStrings.tr(filterType.labelHe, filterType.labelEn)} ($count)",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = quickModelSearchQuery,
                        onValueChange = { quickModelSearchQuery = it },
                        label = { Text(AppStrings.tr("חפש מתוך ${filteredModels.size} מודלים או הקלד מזהה...", "Search ${filteredModels.size} models or type ID...")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Text(
                        text = AppStrings.tr(
                            "לחץ על מודל לקביעתו כראשי, או הדלק מתג כדי להפעילו כמודל גיבוי (ברירת מחדל: כבוי):",
                            "Tap a model to set as Primary, or toggle switch ON to enable as Fallback (default: OFF):"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            count = filteredModels.size,
                            key = { idx -> filteredModels[idx].id }
                        ) { idx ->
                            val modelInfo = filteredModels[idx]
                            val isPrimarySelected = modelInfo.id == selectedPrimaryModel
                            val isFallbackEnabled = modelInfo.id in enabledFallbacks
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPrimarySelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPrimaryModel = modelInfo.id
                                        enabledFallbacks = enabledFallbacks.filter { it != modelInfo.id }
                                        selectedKeyTasks = selectedKeyTasks + modelInfo.capabilities
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = modelInfo.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                color = if (modelInfo.effectivelyFree) {
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = modelInfo.roleBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 9.sp,
                                                    fontWeight = if (modelInfo.effectivelyFree) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                        if (isPrimarySelected) {
                                            Text(
                                                text = AppStrings.tr("⭐ מודל ראשי במפתח זה", "⭐ Primary model for this key"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (isFallbackEnabled) {
                                            val orderIdx = enabledFallbacks.indexOf(modelInfo.id) + 2
                                            Text(
                                                text = AppStrings.tr("גיבוי פעיל #$orderIdx", "Active Fallback #$orderIdx"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (isPrimarySelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = AppStrings.tr("ראשי", "Primary"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Switch(
                                            checked = isFallbackEnabled,
                                            onCheckedChange = { checked ->
                                                enabledFallbacks = if (checked) {
                                                    enabledFallbacks + modelInfo.id
                                                } else {
                                                    enabledFallbacks.filter { it != modelInfo.id }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (quickModelSearchQuery.isNotBlank() && filteredModels.none { it.id.equals(quickModelSearchQuery.trim(), ignoreCase = true) }) {
                            item {
                                OutlinedButton(
                                    onClick = {
                                        selectedPrimaryModel = quickModelSearchQuery.trim()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        AppStrings.tr(
                                            "השתמש במודל מותאם אישית: \"${quickModelSearchQuery.trim()}\"",
                                            "Use custom model: \"${quickModelSearchQuery.trim()}\""
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Drag-and-drop fallback model order if fallbacks are enabled
                    if (enabledFallbacks.size > 1) {
                        Text(
                            text = AppStrings.tr(
                                "גרור כדי לשנות את סדר הקדימיות של מודלי הגיבוי במפתח:",
                                "Drag to reorder fallback models priority:"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        com.example.ui.components.DraggableReorderColumn(
                            items = enabledFallbacks,
                            keySelector = { it },
                            onReorderFinished = { enabledFallbacks = it }
                        ) { idx, fbModelId, isDragging, dragHandleModifier ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        com.example.ui.components.DragHandleBadge(
                                            priorityNumber = idx + 2,
                                            isDragging = isDragging,
                                            dragHandleModifier = dragHandleModifier
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "#${idx + 2} • $fbModelId",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    IconButton(
                                        onClick = { enabledFallbacks = enabledFallbacks.filter { it != fbModelId } },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.updateKeyEntity(
                                targetKey.copy(
                                    selectedModel = selectedPrimaryModel,
                                    fallbackModels = enabledFallbacks.filter { it != selectedPrimaryModel }.joinToString(","),
                                    supportedTasks = AiTaskType.toCsv(selectedKeyTasks),
                                    isEnabled = true
                                )
                            )
                            Toast.makeText(
                                context,
                                AppStrings.tr(
                                    "המודל $selectedPrimaryModel והגדרות הגיבוי נשמרו! 🤖",
                                    "Model $selectedPrimaryModel & fallback settings saved! 🤖"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            keyForQuickModelSelect = null
                        }
                    }
                ) {
                    Text(AppStrings.tr("שמור הגדרות מודל", "Save Model Settings"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { keyForQuickModelSelect = null }) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }

    if (showAddOrEditKeyDialog) {
        AddKeyDialog(
            existingKey = keyBeingEdited,
            nextPriority = (sortedKeys.maxOfOrNull { it.priority } ?: 0) + 1,
            onFetchDetailedModels = { provider, rawKey ->
                repository.fetchAvailableModelsDetailed(provider, rawKey)
            },
            onFetchDetailedModelsForExistingKey = { keyId ->
                repository.fetchModelsForKeyId(keyId)
            },
            onDismiss = {
                showAddOrEditKeyDialog = false
                keyBeingEdited = null
            },
            onSave = { provider, name, rawKey, model, priority, temperature, maxTokens, systemPrompt, supportedTasks, fallbackModels, customBaseUrl, customProtocol, rememberOnDevice ->
                scope.launch {
                    repository.saveApiKey(
                        id = keyBeingEdited?.id,
                        provider = provider,
                        name = name,
                        plainKey = rawKey,
                        model = model,
                        priority = priority,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        systemPrompt = systemPrompt,
                        supportedTasks = supportedTasks,
                        fallbackModels = fallbackModels,
                        customBaseUrl = customBaseUrl,
                        customProtocol = customProtocol,
                        rememberOnDevice = rememberOnDevice
                    )
                    showAddOrEditKeyDialog = false
                    keyBeingEdited = null
                    Toast.makeText(
                        context,
                        AppStrings.tr("המפתח והמודל נשמרו בהצלחה! 🔐", "Key & model saved successfully! 🔐"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun AttachOptionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = accentColor.copy(alpha = 0.05f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(accentColor.copy(alpha = 0.4f), accentColor.copy(alpha = 0.15f))
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = accentColor,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// 1. INLINE VOICE RECORDER (Task #11 & #22: Starts Immediately + Permission Explanation)
// ==========================================
@Composable
private fun VoiceRecordSection(
    onSendAudio: (Uri, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                context,
                AppStrings.tr("נדרשת הרשאת מיקרופון להקלטת קול", "Microphone permission is required for voice recording"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var isRecording by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }
    var currentPrompt by remember { mutableStateOf("") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioLevel by remember { mutableFloatStateOf(0.1f) }

    val startRecordingNow = {
        if (!isRecording) {
            try {
                val outFile = File(
                    context.cacheDir,
                    "notifai_rec_${System.currentTimeMillis()}.m4a"
                )
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setOutputFile(outFile.absolutePath)
                rec.prepare()
                rec.start()
                recorder = rec
                currentOutputFile = outFile
                isRecording = true
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppStrings.tr("שגיאה בהפעלת המיקרופון: ${e.message}", "Error starting microphone: ${e.message}"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Task #11: Start recording IMMEDIATELY as soon as permission is granted!
    LaunchedEffect(hasPermission) {
        if (hasPermission && !isRecording && currentOutputFile == null) {
            startRecordingNow()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
            } catch (_: Exception) {}
            recorder?.release()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            secondsElapsed = 0
            while (isRecording) {
                delay(300)
                secondsElapsed++
                val maxAmp = try {
                    recorder?.maxAmplitude ?: 0
                } catch (_: Exception) { 0 }
                audioLevel = (maxAmp / 25000f).coerceIn(0.15f, 1f)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasPermission) {
            // Task #22: Clear explanation before requesting permission
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = AppStrings.tr("למה נדרשת הרשאת מיקרופון?", "Why is Microphone Permission Needed?"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.tr(
                            "NotifAI זקוקה לגישה למיקרופון אך ורק כשאתה בוחר להקליט הודעה קולית או להשתמש בדיבור לטקסט, כדי להעביר את דבריך למודל ה-AI. ההקלטה תתחיל מיד לאחר האישור.",
                            "NotifAI needs microphone access only when you record a voice message or use Speech-to-Text. Recording will start immediately after approval."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            AppStrings.tr("הבנתי, אשר מיקרופון והתחל להקליט מיד", "Approve Microphone & Record Immediately"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) Color(0xFFE11D48).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color(0xFFE11D48)
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickable {
                            if (!isRecording) {
                                startRecordingNow()
                            } else {
                                try {
                                    recorder?.stop()
                                } catch (_: Exception) {}
                                recorder?.release()
                                recorder = null
                                isRecording = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) AppStrings.tr("עצור הקלטה", "Stop recording") else AppStrings.tr("התחל הקלטה", "Start recording"),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = (secondsElapsed / 3) / 60
                val secs = (secondsElapsed / 3) % 60
                val timeFormatted = String.format(Locale.US, "%02d:%02d", mins, secs)

                Text(
                    text = if (isRecording) {
                        AppStrings.tr("🔴 מקליט כעת ($timeFormatted)", "🔴 Recording now ($timeFormatted)")
                    } else if (currentOutputFile != null) {
                        AppStrings.tr("הקלטה הושלמה ($timeFormatted)", "Recording complete ($timeFormatted)")
                    } else {
                        AppStrings.tr("מתחיל הקלטה...", "Starting recording...")
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isRecording) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRecording) {
                        AppStrings.tr("ההקלטה החלה מיד! לחץ על 'שלח ל-AI' לסיום ושליחה מיידית", "Recording started immediately! Tap 'Send to AI' to finish & send")
                    } else {
                        AppStrings.tr("ההקלטה מוכנה לשליחה", "Recording ready to send")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = currentPrompt,
                onValueChange = { currentPrompt = it },
                label = { Text(AppStrings.tr("הנחיה או שאלה על ההקלטה (אופציונלי)", "Prompt or question about recording (optional)")) },
                placeholder = { Text(AppStrings.tr("למשל: תסכם את מה שאמרתי, או ענה על השאלה", "e.g., Summarize what I said, or answer my question")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_prompt_input"),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            recorder?.stop()
                        } catch (_: Exception) {}
                        recorder?.release()
                        recorder = null
                        currentOutputFile?.delete()
                        onCancel()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }

                Button(
                    onClick = {
                        if (isRecording) {
                            try {
                                recorder?.stop()
                            } catch (_: Exception) {}
                            recorder?.release()
                            recorder = null
                            isRecording = false
                        }
                        val file = currentOutputFile
                        if (file != null && file.exists()) {
                            onSendAudio(Uri.fromFile(file), currentPrompt)
                        } else {
                            Toast.makeText(
                                context,
                                AppStrings.tr("טרם הוקלט קול", "No voice recorded yet"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = isRecording || currentOutputFile != null,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.tr("עצור ושלח מיד ל-AI", "Stop & Send to AI"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 2. SPEECH-TO-TEXT SECTION (Task #10 & #22: Immediate Listen & Immediate Auto-Send unless user clicks to edit)
// ==========================================
@Composable
private fun HebrewSpeechToTextSection(
    onSendText: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                context,
                AppStrings.tr("נדרשת הרשאת מיקרופון לדיבור לטקסט", "Microphone permission is required for Speech-to-Text"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isUserEditing by remember { mutableStateOf(false) }
    var autoSendCountdownActive by remember { mutableStateOf(false) }
    var speechStatusText by remember {
        mutableStateOf(
            AppStrings.tr(
                "מאזין מיד… דבר עכשיו (יישלח אוטומטית בסיום)",
                "Listening immediately… Speak now (auto-sends when done)"
            )
        )
    }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val textFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(textFieldInteractionSource) {
        textFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                isUserEditing = true
                autoSendCountdownActive = false
                speechStatusText = AppStrings.tr(
                    "מצב עריכה פעיל ✏️ (השליחה האוטומטית בוטלה — לחץ 'שלח' כשתסיים)",
                    "Edit mode active ✏️ (Auto-send cancelled — tap 'Send' when ready)"
                )
            }
        }
    }

    val triggerResultAndAutoSend = { newText: String ->
        val combined = if (recognizedText.isBlank()) newText else "$recognizedText $newText"
        recognizedText = combined
        if (!isUserEditing && combined.isNotBlank()) {
            autoSendCountdownActive = true
            speechStatusText = AppStrings.tr(
                "נקלט! שולח אוטומטית כעת… (לחץ על הטקסט לעריכה)",
                "Captured! Sending automatically now… (tap text to edit)"
            )
            scope.launch {
                delay(550)
                if (!isUserEditing && autoSendCountdownActive && recognizedText.isNotBlank()) {
                    onSendText(recognizedText.trim())
                }
            }
        } else {
            speechStatusText = AppStrings.tr(
                "נקלט! ערוך את הטקסט ולחץ 'שלח שאלה'.",
                "Captured! Edit the text and tap 'Send Question'."
            )
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val topResult = results?.firstOrNull()
            if (!topResult.isNullOrBlank()) {
                triggerResultAndAutoSend(topResult)
            }
        }
    }

    val deviceLangTag = remember {
        Locale.getDefault().toLanguageTag().ifBlank { "he-IL" }
    }

    val startListening = {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLangTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deviceLangTag)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        speechStatusText = if (isUserEditing) {
                            AppStrings.tr("מאזין במצב עריכה… 🎙️", "Listening in edit mode… 🎙️")
                        } else {
                            AppStrings.tr(
                                "מאזין… דבר עכשיו (יישלח מיד בסיום הדיבור!) 🚀",
                                "Listening… Speak now (sends immediately when you finish!) 🚀"
                            )
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        speechStatusText = AppStrings.tr("קולט דיבור… 🗣️", "Capturing speech… 🗣️")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        speechStatusText = AppStrings.tr("מעבד ושולח…", "Processing & sending…")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        speechStatusText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> AppStrings.tr("לא נקלטו מילים ברורות. לחץ על המיקרופון לנסות שוב.", "No clear words detected. Tap mic to try again.")
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> AppStrings.tr("לא נשמע דיבור. לחץ להאזנה נוספת.", "No speech heard. Tap to listen again.")
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> AppStrings.tr("שגיאת רשת בשירות הזיהוי הקולי.", "Network error in speech recognition.")
                            else -> AppStrings.tr("האזנה הסתיימה. לחץ שוב לדבר.", "Listening ended. Tap again to speak.")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            triggerResultAndAutoSend(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            recognizedText = partial
                            speechStatusText = AppStrings.tr("קולט: \"$partial\"", "Hearing: \"$partial\"")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
                speechRecognizer = recognizer
            } catch (e: Exception) {
                val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLangTag)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, AppStrings.tr("דבר עכשיו ל-NotifAI…", "Speak now to NotifAI…"))
                }
                systemSpeechLauncher.launch(fallbackIntent)
            }
        } else {
            val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLangTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, AppStrings.tr("דבר עכשיו ל-NotifAI…", "Speak now to NotifAI…"))
            }
            try {
                systemSpeechLauncher.launch(fallbackIntent)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppStrings.tr("זיהוי קולי אינו נתמך במכשיר זה", "Speech recognition is not supported on this device"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Task #10: Auto-start listening immediately when opened!
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stt_scale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasPermission) {
            // Task #22: Clear explanation before requesting permission
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = AppStrings.tr("למה נדרשת הרשאת מיקרופון?", "Why is Microphone Permission Needed?"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.tr(
                            "NotifAI זקוקה לגישה למיקרופון כדי להמיר את הדיבור שלך לטקסט בזמן אמת ולשלוח אותו מיד למענה בהתראה.",
                            "NotifAI needs microphone access to transcribe your speech in real time and send it immediately for an answer in the notification."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            AppStrings.tr("הבנתי, אשר מיקרופון והתחל לדבר מיד", "Approve Microphone & Speak Immediately"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) Color(0xFF7C3AED).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isListening) Color(0xFF7C3AED)
                            else MaterialTheme.colorScheme.secondary
                        )
                        .clickable {
                            if (!isListening) {
                                startListening()
                            } else {
                                speechRecognizer?.stopListening()
                                isListening = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = if (isListening) AppStrings.tr("מאזין", "Listening") else AppStrings.tr("התחל לדבר", "Start speaking"),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isListening) Color(0xFF7C3AED).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = speechStatusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isListening) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Button to switch to manual edit mode (cancels auto-send)
            if (!isUserEditing) {
                FilledTonalButton(
                    onClick = {
                        isUserEditing = true
                        autoSendCountdownActive = false
                        speechStatusText = AppStrings.tr(
                            "מצב עריכה פעיל ✏️ — ערוך את הטקסט ולחץ 'שלח שאלה'",
                            "Edit mode active ✏️ — edit text and tap 'Send Question'"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stt_enable_edit_mode_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        AppStrings.tr("לחץ כאן לעריכת הטקסט (בטל שליחה אוטומטית)", "Tap here to edit text (cancel auto-send)"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Editable Transcribed Text Field (tapping or editing sets isUserEditing = true)
            OutlinedTextField(
                value = recognizedText,
                onValueChange = {
                    isUserEditing = true
                    autoSendCountdownActive = false
                    recognizedText = it
                },
                interactionSource = textFieldInteractionSource,
                label = {
                    Text(
                        AppStrings.tr(
                            "טקסט מתומלל (נגיעה בתיבה עוצרת שליחה אוטומטית לעריכה)",
                            "Transcribed text (touching box pauses auto-send for editing)"
                        )
                    )
                },
                placeholder = {
                    Text(
                        AppStrings.tr(
                            "הדברים שתגיד יופיעו כאן ויישלחו מיד…",
                            "Your spoken words will appear here and send immediately…"
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("stt_transcribed_input"),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (recognizedText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                isUserEditing = true
                                autoSendCountdownActive = false
                                recognizedText = ""
                            }
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = AppStrings.tr("נקה טקסט", "Clear text"))
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        startListening()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppStrings.tr("דבר שוב", "Speak Again"))
                }

                Button(
                    onClick = {
                        autoSendCountdownActive = false
                        onSendText(recognizedText.trim())
                    },
                    enabled = recognizedText.isNotBlank(),
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.tr("שלח שאלה עכשיו", "Send Question Now"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 3. FILE SELECTED SECTION
// ==========================================
@Composable
private fun FileSelectedSection(
    fileName: String,
    fileSizeStr: String,
    fileUri: Uri?,
    onSendFile: (Uri, String) -> Unit,
    onChangeFile: () -> Unit,
    onCancel: () -> Unit
) {
    var promptText by remember {
        mutableStateOf(
            AppStrings.tr(
                "אנא נתח את הקובץ המצורף והסבר את תוכנו",
                "Please analyze the attached file and explain its contents"
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName.ifBlank { AppStrings.tr("קובץ מצורף", "Attached File") },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = if (fileSizeStr.isNotBlank()) {
                            AppStrings.tr("גודל: $fileSizeStr", "Size: $fileSizeStr")
                        } else {
                            AppStrings.tr("מוכן לשליחה ל-AI", "Ready to send to AI")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onChangeFile,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(AppStrings.tr("החלף", "Change"), fontSize = 12.sp)
                }
            }
        }

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text(AppStrings.tr("הנחיה או שאלה על הקובץ", "Prompt or question about the file")) },
            placeholder = { Text(AppStrings.tr("למשל: תרגם, תסכם, תקן שגיאות...", "e.g., Translate, summarize, fix errors...")) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("file_prompt_input"),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(AppStrings.tr("ביטול", "Cancel"))
            }

            Button(
                onClick = {
                    if (fileUri != null) {
                        onSendFile(fileUri, promptText.trim())
                    }
                },
                enabled = fileUri != null,
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("שלח קובץ ל-AI", "Send File to AI"))
            }
        }
    }
}

private fun getFileMetadata(context: Context, uri: Uri): Pair<String, String> {
    var name = "attached_file"
    var sizeBytes = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
        }
    } catch (_: Exception) {}

    val formattedSize = when {
        sizeBytes <= 0L -> ""
        sizeBytes < 1024L -> "$sizeBytes B"
        sizeBytes < 1024L * 1024L -> "${sizeBytes / 1024L} KB"
        else -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
    }
    return name to formattedSize
}
