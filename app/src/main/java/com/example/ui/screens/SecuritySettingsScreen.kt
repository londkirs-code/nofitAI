package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.FastScreenCaptureAccessibilityService
import com.example.ui.viewmodel.NotifAiViewModel
import com.example.util.AppStrings
import com.example.util.MediaStorageManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: NotifAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentLang = AppStrings.langOverrideState
    val prefs = remember { context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE) }
    var autoStartOnBoot by remember { mutableStateOf(prefs.getBoolean("auto_start_boot", true)) }

    var isMediaSavingEnabled by remember { mutableStateOf(MediaStorageManager.isMediaSavingEnabled(context)) }
    var mediaSizeBytes by remember { mutableStateOf(MediaStorageManager.getTotalSavedMediaSizeBytes(context)) }
    var showClearMediaConfirmDialog by remember { mutableStateOf(false) }

    // Live Permission States for Permissions Center (Task #24)
    val checkNotifGranted = {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val checkMicGranted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val checkFastScreenshotGranted = {
        FastScreenCaptureAccessibilityService.isAccessibilityServiceEnabled(context)
    }
    val checkBatteryUnrestricted = {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    var isNotifGranted by remember { mutableStateOf(checkNotifGranted()) }
    var isMicGranted by remember { mutableStateOf(checkMicGranted()) }
    var isFastScreenshotGranted by remember { mutableStateOf(checkFastScreenshotGranted()) }
    var isBatteryUnrestricted by remember { mutableStateOf(checkBatteryUnrestricted()) }

    // Pre-permission explanation modal state (Task #22)
    var pendingPermissionDialogType by remember { mutableStateOf<String?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotifGranted = checkNotifGranted()
        if (granted) {
            viewModel.toggleService(true)
            Toast.makeText(
                context,
                AppStrings.tr("הרשאת ההתראות אושרה בהצלחה! 🔔", "Notification permission granted! 🔔"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicGranted = granted
        if (granted) {
            Toast.makeText(
                context,
                AppStrings.tr("הרשאת המיקרופון אושרה בהצלחה! 🎙️", "Microphone permission granted! 🎙️"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Refresh permission statuses whenever returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotifGranted = checkNotifGranted()
                isMicGranted = checkMicGranted()
                isFastScreenshotGranted = checkFastScreenshotGranted()
                isBatteryUnrestricted = checkBatteryUnrestricted()
                mediaSizeBytes = MediaStorageManager.getTotalSavedMediaSizeBytes(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 0. LANGUAGE & MULTI-LANGUAGE SUPPORT CARD (Task #15)
        // ==========================================
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("language_settings_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppStrings.tr("שפת האפליקציה ומענה ה-AI", "App & AI Response Language"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.tr(
                                "מתאים אוטומטית לשפת הטלפון או לבחירה ידנית (כולל כיוון טקסט ותרגום כל הממשק)",
                                "Automatically adapts to phone language or manual choice (full UI translation & layout direction)"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppStrings.Lang.entries.forEach { langOption ->
                        val isSelected = currentLang == langOption
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                AppStrings.setLanguageOverride(context, langOption)
                            },
                            label = {
                                Text(
                                    text = if (langOption == AppStrings.Lang.AUTO) {
                                        AppStrings.tr("אוטומטי (שפת המכשיר)", "Auto (Device Language)")
                                    } else {
                                        langOption.nativeLabel
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }
        }

        // ==========================================
        // 1. PERMISSIONS CENTER CARD (Task #24 & #22)
        // ==========================================
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permissions_center_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppStrings.tr("מרכז הרשאות האפליקציה", "App Permissions Center"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.tr(
                                "סטטוס חי של כל ההרשאות, הסבר על תפקידן וקיצורי דרך להפעלה",
                                "Live status of all permissions, explanations of why they are needed, and quick activation"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Permission 1: Notifications
                PermissionStatusItem(
                    icon = Icons.Default.Notifications,
                    title = AppStrings.tr("הצגת התראות (חובה לפעולת NotifAI)", "Notifications (Required for NotifAI)"),
                    explanation = AppStrings.tr(
                        "נדרשת כדי להציג את ההתראה הקבועה בווילון ההתראות, לאפשר מענה מהיר (Inline Reply) ולהציג תשובות, תמונות ושמע מה-AI.",
                        "Required to display the persistent notification in the shade, enable Inline Reply, and show AI responses, images, and audio."
                    ),
                    isGranted = isNotifGranted,
                    actionButtonText = if (isNotifGranted) {
                        AppStrings.tr("הגדרות התראות", "Notification Settings")
                    } else {
                        AppStrings.tr("אשר התראות", "Grant Notifications")
                    },
                    testTag = "perm_center_notif_btn",
                    onActionClick = {
                        if (!isNotifGranted) {
                            pendingPermissionDialogType = "NOTIFICATIONS"
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Permission 2: Microphone (Voice Recording & Speech-to-Text)
                PermissionStatusItem(
                    icon = Icons.Default.Mic,
                    title = AppStrings.tr("מיקרופון (הקלטת קול ודיבור לטקסט)", "Microphone (Voice Recording & Speech-to-Text)"),
                    explanation = AppStrings.tr(
                        "נדרשת אך ורק בעת שימוש ב-'הקלטת קול' או 'דיבור לטקסט' מתוך ההתראה, כדי לקלוט את הדיבור שלך ולשלוח אותו ל-AI.",
                        "Required only when using 'Voice Recording' or 'Speech-to-Text' from the notification to capture your speech and send it to AI."
                    ),
                    isGranted = isMicGranted,
                    actionButtonText = if (isMicGranted) {
                        AppStrings.tr("מאושר (פתח הגדרות)", "Granted (Open Settings)")
                    } else {
                        AppStrings.tr("אשר מיקרופון", "Grant Microphone")
                    },
                    testTag = "perm_center_mic_btn",
                    onActionClick = {
                        if (!isMicGranted) {
                            pendingPermissionDialogType = "MICROPHONE"
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Permission 3: Instant Screenshot (Accessibility Service)
                PermissionStatusItem(
                    icon = Icons.Default.CameraAlt,
                    title = AppStrings.tr("צילום מסך מיידי בלחיצה אחת (נגישות)", "Instant 1-Tap Screenshot (Accessibility)"),
                    explanation = AppStrings.tr(
                        "מאפשר לצלם את המסך ישירות מתוך כפתור 'צילום מסך' בהתראה ולשלוח למודל ה-Vision בלי שמערכת אנדרואיד תקפיץ חלונית אישור בכל פעם מחדש.",
                        "Allows capturing the screen directly from the 'Screenshot' button in the notification without Android showing a confirmation prompt every time."
                    ),
                    isGranted = isFastScreenshotGranted,
                    actionButtonText = if (isFastScreenshotGranted) {
                        AppStrings.tr("פעיל (הגדרות נגישות)", "Active (Accessibility Settings)")
                    } else {
                        AppStrings.tr("הפעל צילום מיידי", "Enable Instant Screenshot")
                    },
                    testTag = "perm_center_screenshot_btn",
                    onActionClick = {
                        pendingPermissionDialogType = "SCREENSHOT"
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Permission 4: Unrestricted Background / Battery Optimization
                PermissionStatusItem(
                    icon = Icons.Default.BatteryChargingFull,
                    title = AppStrings.tr("ריצה רציפה ברקע (ללא הגבלת סוללה)", "Unrestricted Background Run (Battery)"),
                    explanation = AppStrings.tr(
                        "מונע ממערכת ההפעלה לסגור את שירות ההתראות של NotifAI בזמן המתנה לתשובות ארוכות ממודלי AI או כשהמסך כבוי.",
                        "Prevents Android from stopping NotifAI's notification service while waiting for long AI responses or when the screen is off."
                    ),
                    isGranted = isBatteryUnrestricted,
                    actionButtonText = if (isBatteryUnrestricted) {
                        AppStrings.tr("מוגדר ללא הגבלה", "Unrestricted")
                    } else {
                        AppStrings.tr("אפשר ריצה ברקע", "Allow Background Run")
                    },
                    testTag = "perm_center_battery_btn",
                    onActionClick = {
                        pendingPermissionDialogType = "BATTERY"
                    }
                )
            }
        }

        // ==========================================
        // 2. NOTIFICATION & AUTO-START CARD
        // ==========================================
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = AppStrings.tr("הגדרות התראה והפעלה אוטומטית", "Notification & Auto-Start Settings"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

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
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = AppStrings.tr("הפעלה אוטומטית בהדלקת המכשיר", "Auto-Start on Device Boot"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = AppStrings.tr(
                                    "משחזר את ההתראה הקבועה מיד לאחר הפעלת הטלפון מחדש",
                                    "Restores the persistent notification automatically after rebooting the phone"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = autoStartOnBoot,
                        onCheckedChange = {
                            autoStartOnBoot = it
                            prefs.edit().putBoolean("auto_start_boot", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.tr("פתח הגדרות התראות מערכת אנדרואיד", "Open Android System Notification Settings"))
                }
            }
        }

        // ==========================================
        // 3. MEDIA & STORAGE MANAGEMENT CARD
        // ==========================================
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isMediaSavingEnabled) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdStorage,
                                contentDescription = null,
                                tint = if (isMediaSavingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = AppStrings.tr("שמירת מדיה בצ'אט (חיסכון באחסון)", "Chat Media Saving (Storage Saver)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMediaSavingEnabled) {
                                    AppStrings.tr("צילומי מסך, קבצים והקלטות נשמרים בהיסטוריה", "Screenshots, files, and recordings are saved in history")
                                } else {
                                    AppStrings.tr("מצב חיסכון: מדיה אינה נשמרת בזיכרון", "Saver Mode: media is not stored on disk")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMediaSavingEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isMediaSavingEnabled,
                        onCheckedChange = { checked ->
                            isMediaSavingEnabled = checked
                            MediaStorageManager.setMediaSavingEnabled(context, checked)
                            val msg = if (checked) {
                                AppStrings.tr("שמירת מדיה בהיסטוריית השיחות הופעלה 📸", "Media saving in chat history enabled 📸")
                            } else {
                                AppStrings.tr("מצב חיסכון הופעל: קבצי מדיה חדשים לא יישמרו 💾", "Saver mode enabled: new media files won't be saved 💾")
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Text(
                    text = AppStrings.tr(
                        "מאפשר לשמור צילומי מסך, קבצים מצורפים והקלטות קוליות בזיכרון המכשיר לצפייה חוזרת בהיסטוריית השיחות. משתמשים המעוניינים לחסוך באחסון יכולים לכבות אפשרות זו בכל עת.",
                        "Saves screenshots, attachments, and voice recordings on device for viewing in chat history. You can turn this off anytime to save storage."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.tr("נפח מדיה שמורה נוכחי:", "Current Saved Media Size:"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = MediaStorageManager.formatMediaSize(mediaSizeBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = { showClearMediaConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = mediaSizeBytes > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (mediaSizeBytes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.tr("נקה את כל קבצי המדיה מהמכשיר", "Clear All Saved Media Files"),
                        color = if (mediaSizeBytes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // ==========================================
        // 4. SECURITY ARCHITECTURE CARD
        // ==========================================
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppStrings.tr("הצפנת מפתחות בחומרה (Hardware Keystore)", "Hardware Keystore Encryption"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.tr("הצפנה חד-כיוונית לתצוגה • AES-256 GCM", "Write-only display protection • AES-256 GCM"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = AppStrings.tr(
                        "• כל מפתח API מוצפן מיידית באמצעות מפתח מאסטר חומרתי שנוצר ב-Android KeyStore.\n" +
                            "• לא ניתן לצפות בתוכן המפתח בשום שלב — לא בעת ההקלדה ולא בעת עריכה.\n" +
                            "• המפתחות אינם נשמרים כטקסט גלוי (Plaintext) בשום שלב בדיסק או במסד הנתונים.",
                        "• Every API key is encrypted immediately using a hardware-backed master key in Android KeyStore.\n" +
                            "• Key contents can never be viewed in plain text—neither during entry nor editing.\n" +
                            "• Keys are never stored as plaintext on disk or in the database."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Pre-Permission Explanation Dialog (Task #22)
    val dialogType = pendingPermissionDialogType
    if (dialogType != null) {
        val (title, explanation, confirmLabel) = when (dialogType) {
            "NOTIFICATIONS" -> Triple(
                AppStrings.tr("🔔 למה נדרשת הרשאת התראות?", "🔔 Why is Notification Permission Needed?"),
                AppStrings.tr(
                    "NotifAI פועלת כמרכז בינה מלאכותית ישירות בתוך וילון ההתראות שלך.\n\nללא הרשאה זו, אנדרואיד לא תאפשר להציג את שורת הכתיבה בהתראה או להציג את תשובות ה-AI.",
                    "NotifAI operates as an AI hub directly inside your notification shade.\n\nWithout this permission, Android cannot show the interactive reply bar or AI responses."
                ),
                AppStrings.tr("הבנתי, המשך לאישור התראות", "Continue to Grant Notifications")
            )
            "MICROPHONE" -> Triple(
                AppStrings.tr("🎙️ למה נדרשת הרשאת מיקרופון?", "🎙️ Why is Microphone Permission Needed?"),
                AppStrings.tr(
                    "הרשאת המיקרופון משמשת אך ורק כאשר אתה בוחר להקליט הודעה קולית או להשתמש בדיבור לטקסט מתוך ההתראה.\n\nהאפליקציה לעולם אינה מקליטה ברקע ללא לחיצה יזומה שלך.",
                    "Microphone access is used exclusively when you tap Voice Recording or Speech-to-Text from the notification.\n\nThe app never records in the background without your action."
                ),
                AppStrings.tr("הבנתי, המשך לאישור מיקרופון", "Continue to Grant Microphone")
            )
            "SCREENSHOT" -> Triple(
                AppStrings.tr("📸 למה נדרשת הרשאת נגישות לצילום מסך?", "📸 Why is Accessibility Needed for Screenshots?"),
                AppStrings.tr(
                    "באנדרואיד, צילום מסך רגיל דורש חלונית אישור מעצבנת בכל צילום מחדש.\n\nהפעלת שירות הנגישות של NotifAI מאפשרת לך ללחוץ על 'צילום מסך' בהתראה וללכוד את המסך מיידית בלחיצה אחת לצורך ניתוח ב-AI. השירות אינו קורא או אוסף מידע עד שתלחץ על צילום מסך.",
                    "On Android, standard screen capture asks for confirmation every single time.\n\nEnabling NotifAI's Accessibility Service lets you tap 'Screenshot' in the notification to capture the screen in 1 tap for AI analysis."
                ),
                AppStrings.tr("פתח הגדרות נגישות", "Open Accessibility Settings")
            )
            else -> Triple(
                AppStrings.tr("🔋 למה נדרשת החרגה מחיסכון בסוללה?", "🔋 Why Allow Unrestricted Battery Usage?"),
                AppStrings.tr(
                    "כדי שהתראת NotifAI תישאר זמינה תמיד בווילון ההתראות ותוכל להשלים בקשות AI גם כשאתה עובר בין אפליקציות, מומלץ לאפשר לה לפעול ברקע ללא הגבלת סוללה.",
                    "So NotifAI stays available in your notification shade and completes long AI requests while switching apps, allow unrestricted background battery usage."
                ),
                AppStrings.tr("פתח הגדרות סוללה", "Open Battery Settings")
            )
        }

        AlertDialog(
            onDismissRequest = { pendingPermissionDialogType = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentType = dialogType
                        pendingPermissionDialogType = null
                        when (currentType) {
                            "NOTIFICATIONS" -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            "MICROPHONE" -> {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            "SCREENSHOT" -> {
                                FastScreenCaptureAccessibilityService.openAccessibilitySettings(context)
                            }
                            "BATTERY" -> {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            }
                        }
                    }
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermissionDialogType = null }) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }

    if (showClearMediaConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearMediaConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = AppStrings.tr("ניקוי כל המדיה השמורה", "Clear All Saved Media"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.tr(
                        "האם אתה בטוח שברצונך למחוק את כל קבצי התמונות, צילומי המסך וההקלטות השמורים בהיסטוריה?\n\nהטקסט של השיחות יישמר במלואו, ורק קבצי המדיה הכבדים יימחקו לצורך חיסכון בנפח האחסון.",
                        "Are you sure you want to delete all saved images, screenshots, and voice recordings?\n\nConversation text will be kept in full; only media files will be removed to free up space."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        MediaStorageManager.clearAllSavedMedia(context)
                        mediaSizeBytes = 0L
                        showClearMediaConfirmDialog = false
                        Toast.makeText(
                            context,
                            AppStrings.tr("נמחקו קבצי מדיה ושוחרר נפח אחסון! 🧹", "Media files cleared and storage freed! 🧹"),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(AppStrings.tr("נקה מדיה עכשיו", "Clear Media Now"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMediaConfirmDialog = false }) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusItem(
    icon: ImageVector,
    title: String,
    explanation: String,
    isGranted: Boolean,
    actionButtonText: String,
    testTag: String,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = if (isGranted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGranted) AppStrings.tr("מאושר ✓", "Granted ✓") else AppStrings.tr("טרם אושר", "Not Granted"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (isGranted) {
                OutlinedButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag(testTag),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(actionButtonText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                FilledTonalButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag(testTag),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(actionButtonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
