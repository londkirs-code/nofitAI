package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ui.AttachContentDialogActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import coil.compose.AsyncImage
import com.example.ui.viewmodel.NotifAiViewModel

@Composable
fun NotificationCenterScreen(
    viewModel: NotifAiViewModel,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isServiceActive) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isServiceActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (isServiceActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isServiceActive) "התראת NotifAI פעילה" else "התראה מושהית",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceActive) "מוכן לקבלת שאלות ישירות ממרכז ההתראות" else "הפעל כדי להציג את שורת הצ'אט בהתראות",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isServiceActive,
                        onCheckedChange = { viewModel.toggleService(it) },
                        modifier = Modifier.testTag("service_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusChip(
                        title = "מפתחות מוכנים",
                        value = "$readyKeysCount / $enabledKeysCount",
                        isSuccess = readyKeysCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        title = "מצב התראה",
                        value = if (isServiceActive) "קבועה (דביקה)" else "כבויה",
                        isSuccess = isServiceActive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Instant Screenshot 1-Click Status Card
        val isFastCaptureActive = com.example.service.FastScreenCaptureAccessibilityService.instance != null
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFastCaptureActive) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = if (isFastCaptureActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "צילום מסך מיידי (ללא אישור בכל פעם)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFastCaptureActive) "מופעל ⚡ לחיצה על צילום מסך מצלמת מיד ללא שאלות" else "מומלץ להפעיל כדי לא לקבל שאלת אישור בכל צילום",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isFastCaptureActive) {
                    Text(
                        text = "כדי לצלם מסך ישירות מתוך ההתראה בלחיצה אחת בלי שגוגל ישאל בכל פעם: הפעל פעם אחת את 'NotifAI' בהגדרות הנגישות.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = {
                            com.example.service.FastScreenCaptureAccessibilityService.openAccessibilitySettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("enable_fast_capture_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚡ הפעל צילום מיידי (בהגדרות נגישות)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "השירות מחובר! כל לחיצה על 'צילום מסך' בהתראה תצלם ותנתח מיידית.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                        text = "איך זה עובד?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. משוך את וילון ההתראות של אנדרואיד בכל רגע נתון.\n" +
                            "2. הקש על 'שאל AI' או כתוב ישירות בשורת ההזנה בהתראה של NotifAI.\n" +
                            "3. לחץ על 'שלח' — ה-AI ישיב ישירות בהתראה ללא צורך בפתיחת האפליקציה!\n" +
                            "4. לחץ על 'העתק' או 'תשובה מלאה' לקבלת התשובה בשלמותה.",
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
                    text = "בדיקת שאילתה וצירוף קובץ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "באפשרותך לצרף תמונה או מסמך ולשלוח שאלה. התשובה תופיע מיידית בהתראה במכשירך:",
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
                                    contentDescription = "קובץ מצורף",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "קובץ מצורף מוכן לשליחה",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "יועבר למודל ה-AI יחד עם שאלתך",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.setAttachmentUri(null) },
                            modifier = Modifier.testTag("remove_attachment_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "הסר קובץ")
                        }
                    }
                }

                OutlinedTextField(
                    value = testQueryInput,
                    onValueChange = { testQueryInput = it },
                    label = { Text("שאל משהו את NotifAI...") },
                    placeholder = { Text("למשל: תסכם לי בקצרה מה זה מחשוב קוונטי") },
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
                                contentDescription = "צרף תמונה",
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
                        Text("שלח להתראה", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val fastService = com.example.service.FastScreenCaptureAccessibilityService.instance
                            if (fastService != null) {
                                fastService.captureNow()
                            } else {
                                val captureIntent = android.content.Intent(context, com.example.ui.ScreenCaptureActivity::class.java).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
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
                        Text("📸 צילום מסך", fontWeight = FontWeight.Bold)
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
                        Text("צרף מדיה")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val attachIntent = Intent(context, AttachContentDialogActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(attachIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("open_attach_content_dialog_btn")
                ) {
                    Text("✨ חלונית 'שלח תוכן' (הקלטה • דיבור בעברית • קבצים)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                        Text("🧠 מה דיברנו בשיחות הקודמות?", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.startNewConversation()
                            android.widget.Toast.makeText(context, "החלה שיחה חדשה! 🧹", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("🧹 שיחה חדשה", fontSize = 11.sp)
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
                        text = "זיכרון שיחה והמשכיות חכמה",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "• NotifAI זוכרת את הקשר השיחה הנוכחית וממשיכה אותה בכל שאלה שתקליד בהתראה.\n" +
                            "• אם עוברות 3 שעות ללא אינטראקציה, המערכת מזהה זאת אוטומטית כנושא חדש.\n" +
                            "• תוכל בכל שלב לשאול בהתראה: \"זוכר מה דיברנו בשיחות הקודמות?\" והיא תעביר ל-AI את כל ההיסטוריה השמורה!\n" +
                            "• בלחיצה על כפתור \"📸 צילום מסך\" בהתראה או באפליקציה, נשלח צילום מסך מידי למודלי הראייה (Vision).",
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
