package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.data.model.AiModelInfo
import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.model.CustomApiProtocol
import com.example.data.model.ProviderType
import com.example.data.repository.AiRepository
import com.example.ui.components.CenteredMenuOption
import com.example.ui.components.CenteredSelectionMenuDialog
import com.example.ui.components.DragHandleBadge
import com.example.ui.components.DraggableReorderColumn
import com.example.ui.components.PollinationsConfigCard
import com.example.ui.dialogs.AddKeyDialog
import com.example.ui.viewmodel.NotifAiViewModel
import com.example.util.AppStrings
import com.example.util.ParsedImportKeyItem
import com.example.util.SmartKeyImporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiKeysScreen(
    viewModel: NotifAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKeys by viewModel.apiKeys.collectAsState()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var keyToEdit by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var dialogInitialRawKey by remember { mutableStateOf("") }
    var showTaskRoutingSection by remember { mutableStateOf(false) }
    var routingRefreshTick by remember { mutableStateOf(0) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }
    var showBulkKeysDialog by remember { mutableStateOf(false) }

    val sortedKeys = remember(apiKeys) { apiKeys.sortedBy { it.priority } }
    val nextPriority = remember(sortedKeys) { (sortedKeys.maxOfOrNull { it.priority } ?: 0) + 1 }

    // Top-of-page Instant API Key Entry State (Auto-detects provider & loads models automatically)
    var topQuickKeyText by remember { mutableStateOf("") }
    var topDetectedProvider by remember { mutableStateOf(ProviderType.GROQ) }
    var topWasProviderAutoDetected by remember { mutableStateOf(false) }
    var topProviderMenuOpen by remember { mutableStateOf(false) }
    var topSelectedModel by remember { mutableStateOf(ProviderType.GROQ.defaultModel) }
    var topFetchedModels by remember { mutableStateOf<List<AiModelInfo>>(emptyList()) }
    var topIsFetchingModels by remember { mutableStateOf(false) }
    var topFetchStatusText by remember { mutableStateOf<String?>(null) }
    var topRememberKeyOnDevice by remember { mutableStateOf(true) }

    LaunchedEffect(topQuickKeyText, topDetectedProvider) {
        val trimmed = topQuickKeyText.trim()
        if (trimmed.length >= 8) {
            delay(350)
            topIsFetchingModels = true
            topFetchStatusText = AppStrings.tr(
                "טוען אוטומטית את כל המודלים משרתי ${topDetectedProvider.displayName}...",
                "Automatically loading all models from ${topDetectedProvider.displayName}..."
            )
            val res = viewModel.fetchDetailedModels(topDetectedProvider, trimmed)
            topIsFetchingModels = false
            if (res.isSuccess && res.models.isNotEmpty()) {
                val sorted = AiModelInfo.sortBestFirst(res.models)
                topFetchedModels = sorted
                val bestModel = sorted.first().id
                topSelectedModel = bestModel
                val freeCount = sorted.count { it.effectivelyFree }
                topFetchStatusText = AppStrings.tr(
                    "נטענו אוטומטית ${sorted.size} מודלים ($freeCount חינמיים בראש הרשימה 🟢) • נבחר: $bestModel",
                    "Auto-loaded ${sorted.size} models ($freeCount free first 🟢) • Selected: $bestModel"
                )
            } else {
                topFetchStatusText = null
            }
        } else {
            topIsFetchingModels = false
            topFetchStatusText = null
            topFetchedModels = emptyList()
        }
    }

    if (topProviderMenuOpen) {
        val providerOptions = remember {
            ProviderType.entries.map { provider ->
                CenteredMenuOption(
                    value = provider,
                    title = provider.displayName,
                    subtitle = "${provider.keyHint} • ${provider.documentationUrl}",
                    badge = provider.defaultModel.substringAfterLast("/").take(16)
                )
            }
        }
        CenteredSelectionMenuDialog(
            title = AppStrings.tr("בחירת ספק AI למפתח", "Select AI Provider"),
            subtitle = AppStrings.tr(
                "המערכת מזהה אוטומטית לפי המפתח, ותמיד ניתן לשנות כאן ידנית",
                "Auto-detected from key text, and can always be changed manually here"
            ),
            options = providerOptions,
            selectedValue = topDetectedProvider,
            onSelect = { chosen ->
                topDetectedProvider = chosen
                topSelectedModel = chosen.defaultModel
                topWasProviderAutoDetected = false
            },
            onDismiss = { topProviderMenuOpen = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. VERY TOP OF KEYS PAGE: Instant API Key Input Row (Auto-detects Provider & Models Immediately)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
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
                    Text(
                        text = AppStrings.tr(
                            "🔑 הזנת מפתח API מהירה (מזהה ספק וטוען מודלים אוטומטית):",
                            "🔑 Quick API Key Entry (Auto-detects provider & models):"
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = topQuickKeyText,
                    onValueChange = { newText ->
                        val trimmed = newText.trim()
                        topQuickKeyText = trimmed
                        val detected = ProviderType.detectProviderFromKey(trimmed)
                        if (detected != null) {
                            topDetectedProvider = detected
                            topSelectedModel = detected.defaultModel
                            topWasProviderAutoDetected = true
                        }
                    },
                    label = {
                        Text(
                            AppStrings.tr(
                                "הדבק או הקלד מפתח API כאן — יתעדכן אוטומטית מיד",
                                "Paste or type API key here — updates automatically"
                            ),
                            fontSize = 12.sp
                        )
                    },
                    placeholder = {
                        Text("gsk_... / sk-or-v1-... / AIzaSy... / sk-...", fontSize = 12.sp)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (topQuickKeyText.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        topQuickKeyText = ""
                                        topWasProviderAutoDetected = false
                                        topFetchStatusText = null
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = AppStrings.tr("נקה", "Clear"),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                    if (pasted.isNotBlank()) {
                                        topQuickKeyText = pasted
                                        val detected = ProviderType.detectProviderFromKey(pasted)
                                        if (detected != null) {
                                            topDetectedProvider = detected
                                            topSelectedModel = detected.defaultModel
                                            topWasProviderAutoDetected = true
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            AppStrings.tr("הלוח ריק", "Clipboard is empty"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = AppStrings.tr("הדבק מהלוח", "Paste from clipboard"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("top_quick_api_key_input")
                )

                // Live Auto-Updated Bar as soon as key text is entered
                AnimatedVisibility(visible = topQuickKeyText.isNotBlank()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                            Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (topWasProviderAutoDetected) {
                                                AppStrings.tr(
                                                    "זוהה אוטומטית: ${topDetectedProvider.displayName}",
                                                    "Auto-detected: ${topDetectedProvider.displayName}"
                                                )
                                            } else {
                                                AppStrings.tr(
                                                    "ספק נבחר: ${topDetectedProvider.displayName}",
                                                    "Selected provider: ${topDetectedProvider.displayName}"
                                                )
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    TextButton(
                                        onClick = { topProviderMenuOpen = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text(
                                            text = AppStrings.tr("שנה ספק ▾", "Change Provider ▾"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (topIsFetchingModels || topFetchStatusText != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (topIsFetchingModels) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Text(
                                            text = topFetchStatusText.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { topRememberKeyOnDevice = !topRememberKeyOnDevice },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = topRememberKeyOnDevice,
                                onCheckedChange = { topRememberKeyOnDevice = it }
                            )
                            Text(
                                text = AppStrings.tr(
                                    "זכור מפתח במכשיר (פרטיות: נשמר במכשיר רק אם מסומן 'זכור')",
                                    "Remember key on device (Privacy: saved on device ONLY if 'Remember' is checked)"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val cleanKey = topQuickKeyText.trim()
                                    if (cleanKey.isNotBlank()) {
                                        val autoName = "${topDetectedProvider.displayName.substringBefore(" ")} #$nextPriority"
                                        viewModel.addOrUpdateApiKey(
                                            id = null,
                                            provider = topDetectedProvider,
                                            name = autoName,
                                            rawKey = cleanKey,
                                            model = topSelectedModel.ifBlank { topDetectedProvider.defaultModel },
                                            priority = nextPriority,
                                            temperature = 0.7f,
                                            maxTokens = 4096,
                                            systemPrompt = null,
                                            supportedTasks = AiTaskType.toCsv(
                                                setOf(AiTaskType.TEXT, AiTaskType.IMAGE_INPUT, AiTaskType.FILE_INPUT)
                                            ),
                                            fallbackModels = "",
                                            customBaseUrl = null,
                                            customProtocol = CustomApiProtocol.OPENAI.name,
                                            rememberOnDevice = topRememberKeyOnDevice
                                        )
                                        topQuickKeyText = ""
                                        topWasProviderAutoDetected = false
                                        topFetchStatusText = null
                                        Toast.makeText(
                                            context,
                                            AppStrings.tr(
                                                "המפתח נוסף בהצלחה (${topDetectedProvider.displayName} • $topSelectedModel)! 🔐",
                                                "Key added (${topDetectedProvider.displayName} • $topSelectedModel)! 🔐"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("top_quick_save_key_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AppStrings.tr("שמור מפתח מיד", "Save Key Now"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    keyToEdit = null
                                    dialogInitialRawKey = topQuickKeyText.trim()
                                    showAddKeyDialog = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = AppStrings.tr("בחירת מודלים והגדרות", "Models & Settings"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Built-in Default Pollinations Image Generator Card (Works out-of-the-box without API key)
        PollinationsConfigCard(
            modifier = Modifier.padding(bottom = 12.dp),
            initiallyExpanded = false,
            hasActiveGeminiKeyInRepo = apiKeys.any { it.provider == ProviderType.GEMINI && it.isEnabled },
            onModelChanged = { chosenModel ->
                val builtIn = apiKeys.find { it.id == AiRepository.BUILTIN_POLLINATIONS_KEY_ID || it.provider == ProviderType.POLLINATIONS }
                if (builtIn != null) {
                    viewModel.addOrUpdateApiKey(
                        id = builtIn.id,
                        provider = ProviderType.POLLINATIONS,
                        name = builtIn.name,
                        rawKey = "",
                        model = chosenModel,
                        priority = builtIn.priority,
                        temperature = builtIn.temperature,
                        maxTokens = builtIn.maxTokens,
                        systemPrompt = builtIn.systemPrompt,
                        supportedTasks = builtIn.supportedTasks,
                        fallbackModels = builtIn.fallbackModels
                    )
                }
            }
        )

        // Sub-header with Add / Configure button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppStrings.tr("סדרי עדיפויות, משימות ומפתחות", "Priorities, Tasks & API Keys"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tr(
                        "${apiKeys.size} מפתחות מוגדרים • גרירה לשינוי סדר, זיהוי אוטומטי וייבוא חכם",
                        "${apiKeys.size} keys configured • Drag to reorder, auto-detect & smart bulk import"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    keyToEdit = null
                    dialogInitialRawKey = topQuickKeyText.trim()
                    showAddKeyDialog = true
                },
                modifier = Modifier.testTag("add_key_fab_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("הגדרות מפתח מלאות", "Full Key Dialog"), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        // Action Bar: Smart Bulk Import/Backup + Delete All Saved Keys
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { showBulkKeysDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("bulk_import_export_keys_btn"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = AppStrings.tr("ייבוא / גיבוי רשימת מפתחות (JSON / TXT / הדבקה)", "Bulk Import / Backup (JSON / TXT / Paste)"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (sortedKeys.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showDeleteAllConfirmDialog = true },
                    modifier = Modifier.testTag("delete_all_keys_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = AppStrings.tr("מחק את כל המפתחות", "Delete all keys"),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.tr("מחק הכל", "Delete All"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (sortedKeys.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.tr("ניתוב מפתח ומודל לפי סוג בקשה", "Key & Model Routing by Request Type"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppStrings.tr("בחר מפתח ייעודי לכל סוג משימה (או אוטומטי לפי עדיפות):", "Choose a dedicated key per task type (or Auto by priority):"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { showTaskRoutingSection = !showTaskRoutingSection },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (showTaskRoutingSection) AppStrings.tr("הסתר", "Hide") else AppStrings.tr("הגדר לפי סוג", "Configure"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showTaskRoutingSection) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val enabledKeys = sortedKeys.filter { it.isEnabled }
                        AiTaskType.entries.forEach { taskType ->
                            val dedicatedId = remember(routingRefreshTick, taskType) {
                                AiRepository.getDedicatedKeyIdForTask(context, taskType)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${taskType.emoji} ${AppStrings.tr(taskType.titleHe, taskType.name)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(110.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isAuto = dedicatedId == null
                                    Surface(
                                        onClick = {
                                            AiRepository.setDedicatedKeyIdForTask(context, taskType, null)
                                            routingRefreshTick++
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = AppStrings.tr("אוטומטי (לפי עדיפות)", "Auto (By Priority)"),
                                            fontSize = 10.sp,
                                            fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isAuto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    enabledKeys.forEach { k ->
                                        val isChosen = dedicatedId == k.id
                                        Surface(
                                            onClick = {
                                                AiRepository.setDedicatedKeyIdForTask(context, taskType, k.id)
                                                routingRefreshTick++
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                        ) {
                                            Text(
                                                text = "${k.name} (${k.selectedModel.take(16)})",
                                                fontSize = 10.sp,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sortedKeys.isEmpty()) {
            EmptyKeysPlaceholder(
                onAddClick = {
                    keyToEdit = null
                    showAddKeyDialog = true
                },
                onBulkImportClick = {
                    showBulkKeysDialog = true
                }
            )
        } else {
            Text(
                text = AppStrings.tr(
                    "💡 גרור כרטיס מפתח למעלה או למטה כדי לשנות את סדר הקדימיות והגיבוי האוטומטי:",
                    "💡 Drag any key card up or down to change its priority and fallback order:"
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            DraggableReorderColumn(
                items = sortedKeys,
                keySelector = { it.id },
                onReorderFinished = { newOrder ->
                    viewModel.reorderKeys(newOrder.map { it.id })
                },
                modifier = Modifier.fillMaxWidth()
            ) { index, key, isDragging, dragHandleModifier ->
                KeyPriorityCard(
                    keyEntity = key,
                    isFirst = index == 0,
                    isDragging = isDragging,
                    dragHandleModifier = dragHandleModifier,
                    onToggle = { viewModel.toggleKeyEnabled(key) },
                    onDuplicate = { viewModel.duplicateKey(key) },
                    onClearSuspension = { viewModel.clearSuspension(key) },
                    onEdit = {
                        keyToEdit = key
                        showAddKeyDialog = true
                    },
                    onDelete = { viewModel.deleteKey(key) }
                )
            }
        }
    }

    // Delete All Keys Confirmation Dialog (Centered & Padded)
    if (showDeleteAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = AppStrings.tr("מחיקת כל המפתחות השמורים", "Delete All Saved Keys"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.tr(
                        "האם אתה בטוח שברצונך למחוק את כל ${sortedKeys.size} המפתחות השמורים? פעולה זו תנקה את כל המפתחות מהאחסון המוצפן ולא ניתן לבטלה (מומלץ לבצע גיבוי לפני כן).",
                        "Are you sure you want to delete all ${sortedKeys.size} saved keys? This will wipe all keys from encrypted storage and cannot be undone."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllKeys()
                        showDeleteAllConfirmDialog = false
                        Toast.makeText(
                            context,
                            AppStrings.tr("כל המפתחות השמורים נמחקו בהצלחה 🗑️", "All saved keys deleted 🗑️"),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_all_keys_btn")
                ) {
                    Text(AppStrings.tr("כן, מחק את כל המפתחות", "Yes, Delete All Keys"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmDialog = false }) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }

    // Smart Bulk Import / Backup Dialog (.json, .txt, and Clipboard Paste)
    if (showBulkKeysDialog) {
        SmartBulkKeysDialog(
            existingKeysCount = sortedKeys.size,
            onDismiss = { showBulkKeysDialog = false },
            onImportKeys = { items ->
                viewModel.importParsedKeys(items) { importedCount ->
                    Toast.makeText(
                        context,
                        AppStrings.tr(
                            "יובאו בהצלחה $importedCount מפתחות עם שיוך ספק ומודל מועדף! 🎉",
                            "Successfully imported $importedCount keys with provider & preferred model! 🎉"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
                showBulkKeysDialog = false
            },
            onExportJson = { onResult ->
                scope.launch {
                    onResult(viewModel.exportKeysAsJson())
                }
            },
            onExportTxt = { onResult ->
                scope.launch {
                    onResult(viewModel.exportKeysAsTxt())
                }
            }
        )
    }

    if (showAddKeyDialog) {
        AddKeyDialog(
            existingKey = keyToEdit,
            nextPriority = nextPriority,
            initialRawKey = if (keyToEdit == null) dialogInitialRawKey else "",
            onFetchDetailedModels = { provider, rawKey ->
                viewModel.fetchDetailedModels(provider, rawKey)
            },
            onFetchDetailedModelsForExistingKey = { keyId ->
                viewModel.fetchDetailedModelsForExistingKey(keyId)
            },
            onDismiss = {
                showAddKeyDialog = false
                keyToEdit = null
                dialogInitialRawKey = ""
            },
            onSave = { provider, name, rawKey, model, priority, temperature, maxTokens, systemPrompt, supportedTasks, fallbackModels, customBaseUrl, customProtocol, rememberOnDevice ->
                viewModel.addOrUpdateApiKey(
                    id = keyToEdit?.id,
                    provider = provider,
                    name = name,
                    rawKey = rawKey,
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
                if (keyToEdit == null && dialogInitialRawKey.isNotBlank()) {
                    topQuickKeyText = ""
                    topWasProviderAutoDetected = false
                    topFetchStatusText = null
                }
                showAddKeyDialog = false
                keyToEdit = null
                dialogInitialRawKey = ""
            }
        )
    }
}

@Composable
fun KeyPriorityCard(
    keyEntity: ApiKeyEntity,
    isFirst: Boolean,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onClearSuspension: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val candidateModels = remember(keyEntity.selectedModel, keyEntity.fallbackModels) {
        keyEntity.orderedCandidateModels
    }
    val tasks = remember(keyEntity.supportedTasks) {
        keyEntity.parsedSupportedTasks
    }
    val now = System.currentTimeMillis()
    val activeModelSuspensions = remember(keyEntity.modelSuspensionsCsv, now) {
        keyEntity.parsedModelSuspensions.filter { (_, untilMs) -> untilMs > now }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Drag Handle, Priority Badge, Name, Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    DragHandleBadge(
                        priorityNumber = keyEntity.priority,
                        isDragging = isDragging,
                        dragHandleModifier = dragHandleModifier
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "#${keyEntity.priority}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = keyEntity.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${keyEntity.provider.displayName} • ${keyEntity.selectedModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (candidateModels.size > 1) {
                            Text(
                                text = AppStrings.tr(
                                    "גיבוי אוטומטי במפתח: ${candidateModels.drop(1).joinToString(" ← ")}",
                                    "Fallback chain: ${candidateModels.drop(1).joinToString(" → ")}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Switch(
                    checked = keyEntity.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("toggle_key_switch_${keyEntity.id}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Assigned Task / Modality Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tasks.forEach { task ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${task.emoji} ${AppStrings.tr(task.titleHe, task.name)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Banner: Full Key Suspension OR Specific Model Suspension
            if (keyEntity.isSuspended || activeModelSuspensions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val suspensionText = if (keyEntity.isSuspended) {
                                val dur = ApiKeyEntity.formatDurationHe(keyEntity.remainingSuspensionSeconds)
                                "המפתח כולו חסום ל-$dur (${keyEntity.suspensionReason ?: "מכסת מפתח נוצלה"})"
                            } else {
                                val modelListStr = activeModelSuspensions.entries.joinToString(", ") { (m, until) ->
                                    val secs = ((until - now) / 1000L).coerceAtLeast(1L)
                                    "$m (${ApiKeyEntity.formatDurationHe(secs)})"
                                }
                                "מודל ספציפי מושהה זמנית: $modelListStr • שאר המודלים במפתח פעילים"
                            }
                            Text(
                                text = suspensionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        IconButton(
                            onClick = onClearSuspension,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = AppStrings.tr("אפס השהייה", "Clear suspension"),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Masked Key Preview, Duplicate & Edit/Delete Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = AppStrings.tr(
                            "מוצפן בחומרה • בקשות: ${keyEntity.successfulRequests}",
                            "Hardware encrypted • Requests: ${keyEntity.successfulRequests}"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OutlinedButton(
                        onClick = onDuplicate,
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("duplicate_key_btn_${keyEntity.id}"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = AppStrings.tr("שכפל מפתח", "Duplicate key"),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.tr("שכפל מפתח", "Duplicate"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = AppStrings.tr("ערוך מפתח", "Edit key"), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = AppStrings.tr("מחק מפתח", "Delete key"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFirst) {
                        AppStrings.tr("⭐ קדימות ראשית במענה (#1)", "⭐ Primary Priority (#1)")
                    } else {
                        AppStrings.tr("גיבוי בעדיפות #${keyEntity.priority}", "Fallback Priority #${keyEntity.priority}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tr("☰ החזק וגרור לשינוי מיקום", "☰ Hold & drag to reorder"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun SmartBulkKeysDialog(
    existingKeysCount: Int,
    onDismiss: () -> Unit,
    onImportKeys: (List<ParsedImportKeyItem>) -> Unit,
    onExportJson: ((String) -> Unit) -> Unit,
    onExportTxt: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var rawInputText by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<ParsedImportKeyItem>>(emptyList()) }
    var itemIndexChangingProvider by remember { mutableStateOf<Int?>(null) }
    var pendingSaveContent by remember { mutableStateOf<String?>(null) }

    fun recomputeParsed(text: String) {
        rawInputText = text
        parsedItems = SmartKeyImporter.parseKeysFromContent(text)
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    recomputeParsed(text)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppStrings.tr("שגיאה בקריאת הקובץ: ${e.localizedMessage}", "Error reading file: ${e.localizedMessage}"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingSaveContent
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                Toast.makeText(
                    context,
                    AppStrings.tr("הקובץ נשמר בהצלחה! 💾", "File saved successfully! 💾"),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    AppStrings.tr("שגיאה בשמירת הקובץ", "Error saving file"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 14.dp),
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = AppStrings.tr("ייבוא וגיבוי חכם של רשימת מפתחות", "Smart Bulk Key Import & Backup"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tr(
                        "תומך בקובצי JSON, TXT והדבקת כמות מהלוח • מזהה אוטומטית ספק ומודל מועדף",
                        "Supports JSON, TXT & bulk clipboard paste • Auto-detects provider & preferred model"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Export / Backup section if keys exist
                if (existingKeysCount > 0) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = AppStrings.tr(
                                    "💾 גיבוי / ייצוא $existingKeysCount המפתחות השמורים:",
                                    "💾 Backup / Export $existingKeysCount Saved Keys:"
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onExportJson { json ->
                                            pendingSaveContent = json
                                            createDocumentLauncher.launch("notifai_keys_backup.json")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.tr("שמור JSON", "Save JSON"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onExportTxt { txt ->
                                            pendingSaveContent = txt
                                            createDocumentLauncher.launch("notifai_keys_backup.txt")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.tr("שמור TXT", "Save TXT"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onExportTxt { txt ->
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("NotifAI Keys Backup", txt))
                                            Toast.makeText(
                                                context,
                                                AppStrings.tr("כל המפתחות הועתקו ללוח! 📋", "All keys copied to clipboard! 📋"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.tr("העתק הכל", "Copy All"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Import Source Buttons: Load File (.json / .txt) or Paste from Clipboard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { openFileLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.tr("בחר קובץ JSON / TXT", "Pick JSON / TXT File"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clipText.isNotBlank()) {
                                val combined = if (rawInputText.isBlank()) clipText else "$rawInputText\n$clipText"
                                recomputeParsed(combined)
                            } else {
                                Toast.makeText(
                                    context,
                                    AppStrings.tr("הלוח ריק", "Clipboard is empty"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.tr("הדבק מהלוח", "Paste Clipboard"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { recomputeParsed(it) },
                    label = {
                        Text(
                            AppStrings.tr(
                                "הדבק כאן רשימת מפתחות, טקסט חופשי או תוכן JSON / TXT...",
                                "Paste key list, free text, or JSON / TXT content here..."
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            "AIzaSy... gemini-2.5-flash\nsk-or-v1-... deepseek/deepseek-r1:free\ngsk_...",
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 160.dp),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 8
                )

                // Detected Keys Preview with manual Provider & Preferred Model override
                if (parsedItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = AppStrings.tr(
                                    "זוהו אוטומטית ${parsedItems.size} מפתחות (ניתן לשנות ספק ומודל ידנית):",
                                    "Auto-detected ${parsedItems.size} keys (tap to change provider or model):"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parsedItems.forEachIndexed { index, item ->
                            val labelStr = if (item.wasModelAutoDetectedFromFile) {
                                AppStrings.tr("⭐ מודל מועדף שזוהה מהקובץ/טקסט", "⭐ Preferred model detected")
                            } else {
                                AppStrings.tr("מודל ברירת מחדל (ניתן לערוך)", "Default model (editable)")
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${index + 1} • ${item.rawKey.take(10)}...${item.rawKey.takeLast(4)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedButton(
                                            onClick = { itemIndexChangingProvider = index },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${item.provider.displayName.substringBefore(" ")} ▾",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = item.preferredModel,
                                        onValueChange = { newModel ->
                                            parsedItems = parsedItems.toMutableList().also { list ->
                                                list[index] = item.copy(preferredModel = newModel.trim())
                                            }
                                        },
                                        label = {
                                            Text(labelStr, fontSize = 10.sp)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportKeys(parsedItems) },
                enabled = parsedItems.isNotEmpty(),
                modifier = Modifier.testTag("confirm_bulk_import_btn")
            ) {
                Text(
                    text = AppStrings.tr(
                        "ייבא ${parsedItems.size} מפתחות עכשיו",
                        "Import ${parsedItems.size} Keys Now"
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.tr("סגור", "Close"))
            }
        }
    )

    val changingIdx = itemIndexChangingProvider
    if (changingIdx != null && changingIdx in parsedItems.indices) {
        val currentItem = parsedItems[changingIdx]
        val providerOptions = remember {
            ProviderType.entries.map { p ->
                CenteredMenuOption(
                    value = p,
                    title = p.displayName,
                    subtitle = "${AppStrings.tr("מודל מומלץ:", "Default:")} ${p.defaultModel}"
                )
            }
        }
        CenteredSelectionMenuDialog(
            title = AppStrings.tr("שינוי ידני של ספק ה-API למפתח #${changingIdx + 1}", "Select API Provider for Key #${changingIdx + 1}"),
            subtitle = "${currentItem.rawKey.take(12)}...",
            options = providerOptions,
            selectedValue = currentItem.provider,
            onSelect = { newProvider ->
                parsedItems = parsedItems.toMutableList().also { list ->
                    list[changingIdx] = currentItem.copy(
                        provider = newProvider,
                        preferredModel = if (currentItem.wasModelAutoDetectedFromFile) currentItem.preferredModel else newProvider.defaultModel
                    )
                }
            },
            onDismiss = { itemIndexChangingProvider = null }
        )
    }
}

@Composable
fun EmptyKeysPlaceholder(
    onAddClick: () -> Unit,
    onBulkImportClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = AppStrings.tr("טרם הוגדרו מפתחות API", "No API Keys Configured Yet"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = AppStrings.tr(
                    he = "הוסף מפתח בודד או ייבא רשימת מפתחות מקובץ JSON / TXT או בהדבקה מהלוח. המערכת תזהה אוטומטית לאיזה ספק שייך כל מפתח ותסמן את המודל המועדף שלו.",
                    en = "Add a single key or bulk import a list of keys from JSON / TXT or clipboard paste. The system automatically detects each key's provider and preferred model."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.tr("הוסף מפתח", "Add Key"), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onBulkImportClick) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.tr("ייבוא רשימה", "Bulk Import"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
