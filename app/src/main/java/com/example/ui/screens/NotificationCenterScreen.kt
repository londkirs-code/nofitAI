package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.service.NotificationChatService
import com.example.ui.AttachContentDialogActivity
import com.example.ui.viewmodel.NotifAiViewModel
import com.example.util.AppStrings

@Composable
fun NotificationCenterScreen(
    viewModel: NotifAiViewModel,
    onRequestActivateNotification: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val attachmentUri by viewModel.selectedAttachmentUri.collectAsState()

    var testQueryInput by remember { mutableStateOf("") }

    // Media picker for attachments
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setAttachmentUri(uri)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncServiceActiveState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val enabledKeysCount = apiKeys.count { it.isEnabled }
    val readyKeysCount = apiKeys.count { it.isEnabled && !it.isSuspended }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero / Live Status Banner
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceActive) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = { onRequestActivateNotification() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isServiceActive) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isServiceActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = AppStrings.tr("הפעל התראה"),
                                tint = if (isServiceActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isServiceActive) AppStrings.tr("התראת NotifAI פעילה") else AppStrings.tr("התראה מושהית"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceActive) {
                                    AppStrings.tr("מוכן לקבלת שאלות ישירות ממרכז ההתראות")
                                } else {
                                    AppStrings.tr("הפעל כדי להציג את שורת הצ'אט בהתראות")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isServiceActive,
                        onCheckedChange = { wantActive ->
                            if (wantActive) {
                                onRequestActivateNotification()
                            } else {
                                viewModel.toggleService(false)
                            }
                        },
                        modifier = Modifier.testTag("service_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Direct Notification Activation / Management Button
                if (!isServiceActive) {
                    Button(
                        onClick = {
                            onRequestActivateNotification()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("activate_notification_hero_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.tr("🚀 הפעל התראה עכשיו"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onRequestActivateNotification()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("refresh_notification_hero_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.tr("רענן התראה"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.toggleService(false)
                                Toast.makeText(context, AppStrings.tr("ההתראה נסגרה", "Notification closed"), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("close_notification_hero_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.tr("סגור התראה"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusChip(
                        title = AppStrings.tr("מפתחות מוכנים"),
                        value = "$readyKeysCount / $enabledKeysCount",
                        isSuccess = readyKeysCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        title = AppStrings.tr("מצב התראה"),
                        value = if (isServiceActive) AppStrings.tr("קבועה (פעילה)") else AppStrings.tr("כבויה / נסגרה"),
                        isSuccess = isServiceActive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // How to reply guide card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.tr("איך זה עובד?"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AppStrings.tr(
                        he = "1. בכל כניסה לאפליקציה, התראת NotifAI מופעלת אוטומטית בעדיפות עליונה וללא צליל/רטט.\n" +
                            "2. הקש על 'שאל AI' או כתוב ישירות בשורת ההזנה בהתראה.\n" +
                            "3. התשובה (טקסט, תמונה, שמע, וידאו או קובץ להורדה) תוצג ישירות באותה התראה!\n" +
                            "4. לחץ על '⚙️ פעולות' בהתראה לבחירת סוג פלט, הדלקה/כיבוי מפתחות, ניקוי תצוגה, שיחה חדשה או הורדת קבצים.",
                        en = "1. Every time you open the app, the NotifAI notification activates automatically at max priority without sound/vibration.\n" +
                            "2. Tap 'Ask AI' or type directly in the notification's inline reply bar.\n" +
                            "3. The response (text, image, audio, video, or downloadable file) appears right inside the same notification!\n" +
                            "4. Tap '⚙️ Actions' in the notification to pick output type, toggle API keys, clear display, start a new chat, or download files."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Test Query & File Attachment Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = AppStrings.tr("בדיקת שאילתה וצירוף קובץ"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tr("באפשרותך לצרף תמונה או מסמך ולשלוח שאלה. התשובה תופיע מיידית בהתראה במכשירך:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Attachment preview if selected
                AnimatedVisibility(visible = attachmentUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (attachmentUri != null) {
                                AsyncImage(
                                    model = attachmentUri,
                                    contentDescription = AppStrings.tr("קובץ מצורף", "Attachment"),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = AppStrings.tr("קובץ מצורף מוכן לשליחה"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = AppStrings.tr("יועבר למודל ה-AI יחד עם שאלתך"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.setAttachmentUri(null) },
                            modifier = Modifier.testTag("remove_attachment_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = AppStrings.tr("הסר קובץ", "Remove file"))
                        }
                    }
                }

                OutlinedTextField(
                    value = testQueryInput,
                    onValueChange = { testQueryInput = it },
                    label = { Text(AppStrings.tr("שאל משהו את NotifAI...")) },
                    placeholder = { Text(AppStrings.tr("למשל: תסכם לי בקצרה מה זה מחשוב קוונטי")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_query_input"),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("attach_file_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = AppStrings.tr("צרף תמונה", "Attach image"),
                                tint = if (attachmentUri != null) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (testQueryInput.isNotBlank() || attachmentUri != null) {
                                viewModel.sendTestQuery(testQueryInput)
                                testQueryInput = ""
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("send_to_notification_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.tr("שלח להתראה"), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val fastService = com.example.service.FastScreenCaptureAccessibilityService.instance
                            if (fastService != null) {
                                fastService.captureNow()
                            } else {
                                val captureIntent = Intent(context, com.example.ui.ScreenCaptureActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(captureIntent)
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("send_screenshot_btn")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.tr("📸 צילום מסך"), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.tr("צרף מדיה"))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val attachIntent = Intent(context, AttachContentDialogActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra(AttachContentDialogActivity.EXTRA_INITIAL_MODE, AttachContentDialogActivity.MODE_ATTACH)
                            }
                            context.startActivity(attachIntent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("open_attach_content_dialog_btn")
                    ) {
                        Text(AppStrings.tr("✨ שלח תוכן"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val actionsIntent = Intent(context, AttachContentDialogActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra(AttachContentDialogActivity.EXTRA_INITIAL_MODE, AttachContentDialogActivity.MODE_ACTIONS)
                            }
                            context.startActivity(actionsIntent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("open_actions_dialog_btn")
                    ) {
                        Text(AppStrings.tr("⚙️ פעולות ומודלים"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Conversational Memory & Recall Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.sendRecallPastChatsQuery() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("recall_past_chats_btn")
                    ) {
                        Text(AppStrings.tr("🧠 מה דיברנו בשיחות הקודמות?"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.startNewConversation()
                            Toast.makeText(context, AppStrings.tr("החלה שיחה חדשה! 🧹", "Started a new chat! 🧹"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(AppStrings.tr("🧹 שיחה חדשה"), fontSize = 11.sp)
                    }
                }
            }
        }

        // Conversational Memory Explanation Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.tr("זיכרון שיחה והמשכיות חכמה"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = AppStrings.tr(
                        he = "• NotifAI זוכרת את הקשר השיחה הנוכחית וממשיכה אותה בכל שאלה שתקליד בהתראה.\n" +
                            "• אם עוברות 3 שעות ללא אינטראקציה, המערכת מזהה זאת אוטומטית כנושא חדש.\n" +
                            "• בלחיצה על כפתור \"📸 צילום מסך\" מתוך ההתראה, יוצג הסבר ותתבקש הרשאת צילום מסך לפי הצורך (ובמסך 'הגדרות' מופיע מרכז הרשאות מלא).",
                        en = "• NotifAI remembers your active conversation context across inline replies.\n" +
                            "• After 3 hours of inactivity, a new conversation topic starts automatically.\n" +
                            "• Tapping \"📸 Screenshot\" explains and requests screenshot permission on demand (and a full Permissions Center is available in Settings)."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusChip(
    title: String,
    value: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isSuccess) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
