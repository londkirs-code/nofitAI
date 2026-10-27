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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.receiver.NotificationActionReceiver
import com.example.service.FastScreenCaptureAccessibilityService
import com.example.service.NotificationChatService
import com.example.ui.theme.NotifAiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class AttachDialogMode {
    MENU,
    VOICE_RECORD,
    SPEECH_TO_TEXT,
    FILE_SELECTED
}

class AttachContentDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotifAiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AttachContentDialogScreen(
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun AttachContentDialogScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentMode by remember { mutableStateOf(AttachDialogMode.MENU) }
    var showCloseNotificationConfirm by remember { mutableStateOf(false) }

    // Selected file state
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSizeStr by remember { mutableStateOf("") }

    // File picker launcher
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
                    text = "סגירת התראת NotifAI",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "האם אתה בטוח שברצונך לסגור ולהסיר את התראת NotifAI מווילון ההתראות?\n\nתוכל להפעיל אותה שוב בכל עת מתוך האפליקציה.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseNotificationConfirm = false
                        NotificationChatService.stopService(context)
                        Toast.makeText(context, "התראת NotifAI נסגרה", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_close_notification_btn")
                ) {
                    Text("כן, סגור התראה", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCloseNotificationConfirm = false },
                    modifier = Modifier.testTag("cancel_close_notification_btn")
                ) {
                    Text("ביטול")
                }
            }
        )
    }

    BackHandler {
        if (currentMode != AttachDialogMode.MENU) {
            currentMode = AttachDialogMode.MENU
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
                .clickable(enabled = false) {}, // prevent dismissing when tapping card
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
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentMode != AttachDialogMode.MENU) {
                            IconButton(
                                onClick = { currentMode = AttachDialogMode.MENU },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("attach_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "חזרה לתפריט"
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
                                    AttachDialogMode.MENU -> "שלח תוכן ל-NotifAI"
                                    AttachDialogMode.VOICE_RECORD -> "הקלטת קול ישירה"
                                    AttachDialogMode.SPEECH_TO_TEXT -> "דיבור לטקסט בעברית"
                                    AttachDialogMode.FILE_SELECTED -> "העלאת קובץ מצורף"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (currentMode) {
                                    AttachDialogMode.MENU -> "בחר כיצד להזין תוכן ל-AI"
                                    AttachDialogMode.VOICE_RECORD -> "הקלט שמע ושלח לפענוח"
                                    AttachDialogMode.SPEECH_TO_TEXT -> "המרת קול לטקסט בזמן אמת"
                                    AttachDialogMode.FILE_SELECTED -> "אישור ושליחה"
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
                            contentDescription = "סגור",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Animated Mode Content
                AnimatedContent(
                    targetState = currentMode,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "attach_mode_content"
                ) { mode ->
                    when (mode) {
                        AttachDialogMode.MENU -> {
                            AttachMenuOptions(
                                onScreenshotClick = {
                                    // Close dialog first so it doesn't appear in the captured image
                                    onDismiss()
                                    // Trigger instant capture
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
                                        Toast.makeText(context, "שגיאה בפתיחת בורר הקבצים", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCloseNotificationClick = {
                                    showCloseNotificationConfirm = true
                                }
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
                                    Toast.makeText(context, "ההקלטה נשלחה ל-NotifAI 🎙️", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "השאלה נשלחה ל-NotifAI! 🚀", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "הקובץ נשלח ל-NotifAI! 📎", Toast.LENGTH_SHORT).show()
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
    onFileUploadClick: () -> Unit,
    onCloseNotificationClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. צילום מסך
        AttachOptionCard(
            title = "📸 צילום מסך",
            subtitle = "צלם את מה שמוצג כעת במסך ונתח עם AI",
            badgeText = "מיידי",
            icon = Icons.Default.CameraAlt,
            accentColor = Color(0xFF0284C7),
            testTag = "attach_option_screenshot",
            onClick = onScreenshotClick
        )

        // 2. הקלטת קול
        AttachOptionCard(
            title = "🎙️ הקלטת קול",
            subtitle = "הקלט שמע ישירות ושלח כקובץ קולי למודל",
            badgeText = "שמע",
            icon = Icons.Default.Mic,
            accentColor = Color(0xFFE11D48),
            testTag = "attach_option_record",
            onClick = onVoiceRecordClick
        )

        // 3. דיבור לטקסט (עברית)
        AttachOptionCard(
            title = "🗣️ דיבור לטקסט (עברית)",
            subtitle = "דבר בעברית והפוך לקוד/שאלה טקסטואלית",
            badgeText = "he-IL",
            icon = Icons.Default.RecordVoiceOver,
            accentColor = Color(0xFF7C3AED),
            testTag = "attach_option_stt",
            onClick = onSpeechToTextClick
        )

        // 4. העלאת קובץ
        AttachOptionCard(
            title = "📁 העלאת קובץ",
            subtitle = "תמונה, מסמך, PDF, קוד או שמע מהמכשיר",
            badgeText = "מסמכים",
            icon = Icons.Default.AttachFile,
            accentColor = Color(0xFF059669),
            testTag = "attach_option_file",
            onClick = onFileUploadClick
        )

        // 5. סגור התראה
        AttachOptionCard(
            title = "🛑 סגור התראה",
            subtitle = "הסר את התראת NotifAI מווילון ההתראות",
            badgeText = "הסרה",
            icon = Icons.Default.Close,
            accentColor = Color(0xFFDC2626),
            testTag = "attach_option_close_notification",
            onClick = onCloseNotificationClick
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
// 1. INLINE VOICE RECORDER
// ==========================================
@Composable
private fun VoiceRecordSection(
    onSendAudio: (Uri, String) -> Unit,
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
            Toast.makeText(context, "נדרשת הרשאת מיקרופון להקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    var isRecording by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }
    var currentPrompt by remember { mutableStateOf("") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioLevel by remember { mutableFloatStateOf(0.1f) }

    // Pulsing transition animation for recording
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

    // Clean up recorder on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
            } catch (_: Exception) {}
            recorder?.release()
        }
    }

    // Timer & amplitude loop
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
            Text(
                text = "נדרשת הרשאת מיקרופון להקלטת הודעה קולית",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("הענק הרשאת מיקרופון")
            }
        } else {
            // Animated Visual Mic Circle
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
                                // Start recording
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
                                    Toast.makeText(context, "שגיאה בהפעלת המיקרופון: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Stop and prepare
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
                        contentDescription = if (isRecording) "עצור הקלטה" else "התחל הקלטה",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Duration & State
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = (secondsElapsed / 3) / 60
                val secs = (secondsElapsed / 3) % 60
                val timeFormatted = String.format(Locale.US, "%02d:%02d", mins, secs)

                Text(
                    text = if (isRecording) timeFormatted else if (currentOutputFile != null) "הקלטה הושלמה ($timeFormatted)" else "לחץ על המיקרופון כדי להקליט",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isRecording) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRecording) "מקליט כעת… לחץ שוב לסיום" else if (currentOutputFile != null) "ההקלטה מוכנה לשליחה" else "איכות שמע גבוהה AAC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Optional Prompt Field
            OutlinedTextField(
                value = currentPrompt,
                onValueChange = { currentPrompt = it },
                label = { Text("הנחיה או שאלה על ההקלטה (אופציונלי)") },
                placeholder = { Text("למשל: תסכם את מה שאמרתי, או ענה על השאלה") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_prompt_input"),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )

            // Buttons
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
                    Text("ביטול")
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
                            Toast.makeText(context, "טרם הוקלט קול", Toast.LENGTH_SHORT).show()
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
                    Text("שלח ל-AI")
                }
            }
        }
    }
}

// ==========================================
// 2. HEBREW SPEECH-TO-TEXT SECTION
// ==========================================
@Composable
private fun HebrewSpeechToTextSection(
    onSendText: (String) -> Unit,
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
            Toast.makeText(context, "נדרשת הרשאת מיקרופון לדיבור לטקסט", Toast.LENGTH_SHORT).show()
        }
    }

    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var speechStatusText by remember { mutableStateOf("מוכן להאזנה בעברית") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Fallback system recognizer activity launcher
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val topResult = results?.firstOrNull()
            if (!topResult.isNullOrBlank()) {
                recognizedText = if (recognizedText.isBlank()) topResult else "$recognizedText $topResult"
                speechStatusText = "הקליטה נקלטה בהצלחה!"
            }
        }
    }

    // Function to launch speech listening
    val startListening = {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        speechStatusText = "מאזין בעברית… דבר עכשיו 🎙️"
                    }

                    override fun onBeginningOfSpeech() {
                        speechStatusText = "קולט דיבור… 🗣️"
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        speechStatusText = "מעבד את הדברים שנאמרו…"
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        speechStatusText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "לא נקלטו מילים ברורות בעברית. נסה שוב."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא נשמע דיבור. לחץ להאזנה נוספת."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "שגיאת רשת בשירות הזיהוי הקולי."
                            else -> "האזנה הסתיימה. לחץ שוב לדבר."
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            recognizedText = if (recognizedText.isBlank()) text else "$recognizedText $text"
                            speechStatusText = "נקלט בהצלחה! באפשרותך לערוך או לשלוח."
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            speechStatusText = "מקשיב: \"$partial\""
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
                speechRecognizer = recognizer
            } catch (e: Exception) {
                // Fallback to system intent
                val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "דבר בעברית ל-NotifAI…")
                }
                systemSpeechLauncher.launch(fallbackIntent)
            }
        } else {
            // SpeechRecognizer not available directly; use system voice prompt activity
            val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "דבר בעברית ל-NotifAI…")
            }
            try {
                systemSpeechLauncher.launch(fallbackIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "זיהוי קולי אינו נתמך במכשיר זה", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-start listening on view load if permission granted
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

    // Animation
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
            Text(
                text = "נדרשת הרשאת מיקרופון לדיבור לטקסט",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("הענק הרשאת מיקרופון")
            }
        } else {
            // Visual Voice Recognition Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) Color(0xFF7C3AED).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
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
                        contentDescription = if (isListening) "מאזין" else "התחל לדבר",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Status chip
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isListening) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Editable Transcribed Text Field
            OutlinedTextField(
                value = recognizedText,
                onValueChange = { recognizedText = it },
                label = { Text("טקסט מתומלל בעברית (ניתן לעריכה)") },
                placeholder = { Text("הדברים שתגיד יופיעו כאן אוטומטית…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("stt_transcribed_input"),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (recognizedText.isNotBlank()) {
                        IconButton(onClick = { recognizedText = "" }) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "נקה טקסט")
                        }
                    }
                }
            )

            // Actions Row
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
                    Text("דבר שוב")
                }

                Button(
                    onClick = {
                        onSendText(recognizedText.trim())
                    },
                    enabled = recognizedText.isNotBlank(),
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("שלח שאלה")
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
    var promptText by remember { mutableStateOf("אנא נתח את הקובץ המצורף והסבר את תוכנו") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // File Card
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
                        text = fileName.ifBlank { "קובץ מצורף" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = if (fileSizeStr.isNotBlank()) "גודל: $fileSizeStr" else "מוכן לשליחה ל-AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onChangeFile,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("החלף", fontSize = 12.sp)
                }
            }
        }

        // Custom Prompt Outlined TextField
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("הנחיה או שאלה על הקובץ") },
            placeholder = { Text("למשל: תרגם לעברית, תסכם, תקן שגיאות...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("file_prompt_input"),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3
        )

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ביטול")
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
                Text("שלח ל-AI")
            }
        }
    }
}

private fun getFileMetadata(context: Context, uri: Uri): Pair<String, String> {
    var name = "attachment"
    var sizeStr = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    val bytes = cursor.getLong(sizeIndex)
                    sizeStr = when {
                        bytes < 1024 -> "$bytes B"
                        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return Pair(name, sizeStr)
}
