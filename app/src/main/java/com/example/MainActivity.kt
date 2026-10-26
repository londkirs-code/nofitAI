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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.security.AppLockManager
import com.example.service.NotificationChatService
import com.example.ui.screens.ApiKeysScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NotificationCenterScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.screens.SetupPinDialog
import com.example.ui.theme.NotifAiTheme
import com.example.ui.viewmodel.NotifAiViewModel

enum class NavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    NOTIFICATION("התראה", Icons.Filled.NotificationsActive, Icons.Outlined.Notifications, "nav_notification"),
    KEYS("מפתחות", Icons.Filled.Key, Icons.Outlined.Key, "nav_keys"),
    HISTORY("היסטוריה", Icons.Filled.History, Icons.Outlined.History, "nav_history"),
    SECURITY("אבטחה", Icons.Filled.Security, Icons.Outlined.Security, "nav_security")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Explicitly clear FLAG_SECURE so streaming emulator and browser display render immediately
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        AppLockManager.setBlockScreenshots(this, false)

        setContent {
            NotifAiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val viewModel: NotifAiViewModel = viewModel()
                    MainAppContent(
                        viewModel = viewModel,
                        intent = intent
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
    intent: Intent?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTab by remember { mutableStateOf(NavTab.NOTIFICATION) }
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isAppLocked by remember { mutableStateOf(AppLockManager.isLockEnabled(context)) }
    var showQuickSetupPinDialog by remember { mutableStateOf(false) }

    // Auto-lock when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (AppLockManager.isLockEnabled(context) && AppLockManager.isAutoLockOnBackground(context)) {
                    isAppLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android 13+ Notification permission request
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            NotificationChatService.startService(context)
            Toast.makeText(context, "התראות NotifAI הופעלו בהצלחה! 🔔", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "שים לב: ללא הרשאת התראות, NotifAI לא יוכל לפעול בהתראה", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
                Toast.makeText(context, "הטקסט שנשלח הועבר למענה בהתראה!", Toast.LENGTH_SHORT).show()
            }
        }

        if (intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_ATTACHMENT_PICKER, false) == true) {
            currentTab = NavTab.NOTIFICATION
        }

        if (intent?.getBooleanExtra(MainActivity.EXTRA_CAPTURE_SCREENSHOT, false) == true) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                viewModel.sendScreenshot(activity)
            }
        }
    }

    if (isAppLocked) {
        AppLockScreen(
            onUnlocked = { isAppLocked = false }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (isServiceActive) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.error,
                                    CircleShape
                                )
                        )
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
                    val lockConfigured = AppLockManager.isLockEnabled(context)
                    IconButton(
                        onClick = {
                            if (AppLockManager.hasPin(context)) {
                                AppLockManager.setLockEnabled(context, true)
                                isAppLocked = true
                            } else {
                                showQuickSetupPinDialog = true
                            }
                        },
                        modifier = Modifier.testTag("top_bar_lock_btn")
                    ) {
                        Icon(
                            imageVector = if (lockConfigured) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (lockConfigured) "נעל אפליקציה" else "הגדר קוד נעילה",
                            tint = if (lockConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                NavTab.values().forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
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
                    NavTab.NOTIFICATION -> NotificationCenterScreen(viewModel = viewModel)
                    NavTab.KEYS -> ApiKeysScreen(viewModel = viewModel)
                    NavTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                    NavTab.SECURITY -> SecuritySettingsScreen(
                        viewModel = viewModel,
                        onLockAppNow = { isAppLocked = true }
                    )
                }
            }
        }
    }

    if (showQuickSetupPinDialog) {
        SetupPinDialog(
            hasExistingPin = AppLockManager.hasPin(context),
            onDismiss = { showQuickSetupPinDialog = false },
            onPinSaved = { showQuickSetupPinDialog = false }
        )
    }
}
