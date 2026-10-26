package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AppLockManager
import com.example.ui.viewmodel.NotifAiViewModel

@Composable
fun SecuritySettingsScreen(
    viewModel: NotifAiViewModel,
    onLockAppNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE) }
    var autoStartOnBoot by remember { mutableStateOf(prefs.getBoolean("auto_start_boot", true)) }

    var isLockEnabled by remember { mutableStateOf(AppLockManager.isLockEnabled(context)) }
    var hasPin by remember { mutableStateOf(AppLockManager.hasPin(context)) }
    var autoLockBg by remember { mutableStateOf(AppLockManager.isAutoLockOnBackground(context)) }
    var showSetupPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Lock Management Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isLockEnabled) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "נעילת אפליקציה (App Lock)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isLockEnabled) "מוגנת בקוד PIN מוצפן" else "לא פעילה כעת — הגדר קוד PIN להגנה",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLockEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !hasPin) {
                                showSetupPinDialog = true
                            } else {
                                AppLockManager.setLockEnabled(context, checked)
                                isLockEnabled = checked
                            }
                        },
                        modifier = Modifier.testTag("app_lock_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                // Action buttons: Set/Change PIN & Lock Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showSetupPinDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("setup_pin_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasPin) "שנה קוד PIN" else "הגדר קוד PIN",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isLockEnabled && hasPin) {
                        FilledTonalButton(
                            onClick = onLockAppNow,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("lock_now_btn")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("נעל כעת", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Auto-lock on background switch
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
                            Icons.Default.PhonelinkLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "נעילה אוטומטית ביציאה מהאפליקציה",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "נועל את המסך מיד במעבר לאפליקציה אחרת או כיבוי מסך",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = autoLockBg,
                        onCheckedChange = {
                            autoLockBg = it
                            AppLockManager.setAutoLockOnBackground(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }


            }
        }

        // Security Architecture Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                            text = "רמת אבטחה מקסימלית (Hardware Keystore)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "הצפנת מפתחות מבוססת חומרה AES-256 GCM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "• כל מפתח API וקוד ה-PIN מוצפנים מיידית באמצעות מפתח מאסטר חומרתי שנוצר ב-Android KeyStore.\n" +
                            "• המפתחות אינם נשמרים כטקסט פשוט (Plaintext) בשום שלב בדיסק או במסד הנתונים.\n" +
                            "• גם כשנעילת האפליקציה מופעלת, תוכל להמשיך לשאול את ה-AI דרך וילון ההתראות מבלי לחשוף את מסך המפתחות!\n" +
                            "• אפס שיתוף או העברה של מפתחות לעננים חיצוניים או שרתי צד שלישי.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Notification & Background Preferences Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "הגדרות התראה וביצועים",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Boot startup switch
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "הפעלה אוטומטית בהדלקת המכשיר",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "משחזר את ההתראה הקבועה מיד לאחר הפעלת הטלפון מחדש",
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

                Spacer(modifier = Modifier.height(2.dp))

                // System App Notification settings
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("פתח הגדרות התראות מערכת אנדרואיד")
                }
            }
        }
    }

    if (showSetupPinDialog) {
        SetupPinDialog(
            hasExistingPin = hasPin,
            onDismiss = { showSetupPinDialog = false },
            onPinSaved = {
                hasPin = AppLockManager.hasPin(context)
                isLockEnabled = AppLockManager.isLockEnabled(context)
                showSetupPinDialog = false
            }
        )
    }
}
