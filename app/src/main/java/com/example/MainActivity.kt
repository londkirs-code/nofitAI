package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ApiKeysScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NotificationCenterScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.theme.NotifAiTheme
import com.example.ui.viewmodel.NotifAiViewModel
import com.example.util.AppStrings

enum class NavTab(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    NOTIFICATION(Icons.Filled.NotificationsActive, Icons.Outlined.Notifications, "nav_notification"),
    KEYS(Icons.Filled.Key, Icons.Outlined.Key, "nav_keys"),
    HISTORY(Icons.Filled.History, Icons.Outlined.History, "nav_history"),
    SECURITY(Icons.Filled.Settings, Icons.Outlined.Settings, "nav_security");

    fun localizedTitle(): String = when (this) {
        NOTIFICATION -> AppStrings.tabNotification
        KEYS -> AppStrings.tabKeys
        HISTORY -> AppStrings.tabHistory
        SECURITY -> AppStrings.tabSettings
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.initFromPrefs(this)
        enableEdgeToEdge()

        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            NotifAiTheme {
                val activeLang = AppStrings.langOverrideState
                CompositionLocalProvider(LocalLayoutDirection provides AppStrings.layoutDirection()) {
                    val viewModel: NotifAiViewModel = viewModel()
                    MainAppContent(
                        viewModel = viewModel,
                        intent = intent,
                        langKey = activeLang.code
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_ATTACHMENT_PICKER = "extra_open_attachment_picker"
        const val EXTRA_CAPTURE_SCREENSHOT = "extra_capture_screenshot"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: NotifAiViewModel,
    intent: Intent?,
    langKey: String = "auto"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTab by remember { mutableStateOf(NavTab.NOTIFICATION) }
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-permission explanation dialog state for Notification permission (Task #22)
    var showNotifPermissionRationaleDialog by remember { mutableStateOf(false) }

    val hasNotificationPermission = {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val managerEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        runtimeGranted && managerEnabled
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val enabledNow = isGranted && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (enabledNow) {
            viewModel.toggleService(true)
            Toast.makeText(
                context,
                AppStrings.tr("התראת NotifAI הופעלה בהצלחה! 🔔", "NotifAI notification activated! 🔔"),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // If Android blocked the dialog (Don't ask again) or notifications are disabled in system settings, open App Notification Settings
            try {
                val settingsIntent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } catch (_: Exception) {}
            Toast.makeText(
                context,
                AppStrings.tr(
                    "נא לאפשר הרשאת התראות כדי להציג את שורת הצ'אט של NotifAI",
                    "Please enable Notification permission to show the NotifAI chat bar"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestOrActivateNotification = {
        if (hasNotificationPermission()) {
            viewModel.toggleService(true)
            Toast.makeText(
                context,
                AppStrings.tr("התראת NotifAI פעילה ומרועננת! 🔔", "NotifAI notification is active & refreshed! 🔔"),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            showNotifPermissionRationaleDialog = true
        }
    }

    // Automatically activate the notification every time the user enters/opens the app if permission is granted
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasNotificationPermission()) {
                    viewModel.toggleService(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasNotificationPermission()) {
            // Task #22: Show clear explanation dialog before requesting permission
            showNotifPermissionRationaleDialog = true
        } else {
            viewModel.toggleService(true)
        }

        // Handle shared text/media from Intent.ACTION_SEND
        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedStream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (sharedStream != null) {
                viewModel.setAttachmentUri(sharedStream)
            }
            if (!sharedText.isNullOrBlank()) {
                viewModel.sendTestQuery(sharedText)
                Toast.makeText(
                    context,
                    AppStrings.tr("הטקסט שנשלח הועבר למענה בהתראה!", "Shared text sent to notification!"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_ATTACHMENT_PICKER, false) == true) {
            currentTab = NavTab.NOTIFICATION
        }

        if (intent?.getBooleanExtra(MainActivity.EXTRA_CAPTURE_SCREENSHOT, false) == true) {
            val fastService = com.example.service.FastScreenCaptureAccessibilityService.instance
            if (fastService != null) {
                fastService.captureNow()
            } else {
                val captureIntent = Intent(context, com.example.ui.ScreenCaptureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(captureIntent)
            }
        }
    }

    if (showNotifPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showNotifPermissionRationaleDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_custom_asset),
                    contentDescription = "NotifAI",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            },
            title = {
                Text(
                    text = AppStrings.notifPermissionTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.notifPermissionExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifPermissionRationaleDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            try {
                                val settingsIntent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(settingsIntent)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.testTag("confirm_notif_permission_rationale_btn")
                ) {
                    Text(AppStrings.notifPermissionApproveBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotifPermissionRationaleDialog = false }
                ) {
                    Text(AppStrings.cancelBtn)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_custom_asset),
                                contentDescription = "NotifAI Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(
                                        if (isServiceActive && hasNotificationPermission()) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.error,
                                        CircleShape
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NotifAI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            requestOrActivateNotification()
                        },
                        modifier = Modifier.testTag("top_bar_notification_action_btn")
                    ) {
                        val activeAndPermitted = isServiceActive && hasNotificationPermission()
                        Icon(
                            imageVector = if (activeAndPermitted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = if (activeAndPermitted) {
                                AppStrings.tr("רענן התראה", "Refresh Notification")
                            } else {
                                AppStrings.tr("הפעל התראה ובקש הרשאה", "Enable Notification & Request Permission")
                            },
                            tint = if (activeAndPermitted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val title = tab.localizedTitle()
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = title
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    NavTab.NOTIFICATION -> NotificationCenterScreen(
                        viewModel = viewModel,
                        onRequestActivateNotification = requestOrActivateNotification
                    )
                    NavTab.KEYS -> ApiKeysScreen(viewModel = viewModel)
                    NavTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                    NavTab.SECURITY -> SecuritySettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
