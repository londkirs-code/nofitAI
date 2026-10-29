package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AiModelInfo
import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.model.CustomApiProtocol
import com.example.data.model.FetchModelsResponse
import com.example.data.model.ModelFilterType
import com.example.data.model.ProviderType
import com.example.ui.components.CenteredMenuOption
import com.example.ui.components.CenteredSelectionMenuDialog
import com.example.ui.components.DragHandleBadge
import com.example.ui.components.DraggableReorderColumn
import com.example.ui.components.PollinationsConfigCard
import com.example.util.AppStrings
import com.example.util.PollinationsImageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddKeyDialog(
    existingKey: ApiKeyEntity? = null,
    nextPriority: Int = 1,
    initialRawKey: String = "",
    onFetchDetailedModels: suspend (ProviderType, String) -> FetchModelsResponse,
    onFetchDetailedModelsForExistingKey: (suspend (String) -> FetchModelsResponse)? = null,
    onClearSuspension: (() -> Unit)? = null,
    onDuplicateKey: (() -> Unit)? = null,
    onDeleteKey: (() -> Unit)? = null,
    onToggleKeyEnabled: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        provider: ProviderType,
        name: String,
        rawKey: String,
        model: String,
        priority: Int,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String?,
        supportedTasks: String,
        fallbackModels: String,
        customBaseUrl: String?,
        customProtocol: String,
        rememberOnDevice: Boolean,
        isEnabled: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialogScrollState = rememberScrollState()

    val initialDetectedProvider = remember(initialRawKey) {
        if (initialRawKey.isNotBlank()) ProviderType.detectProviderFromKey(initialRawKey.trim()) else null
    }
    var selectedProvider by remember {
        mutableStateOf(existingKey?.provider ?: initialDetectedProvider ?: ProviderType.GROQ)
    }
    var providerMenuDialogOpen by remember { mutableStateOf(false) }
    var autoDetectedProviderBadge by remember { mutableStateOf<ProviderType?>(initialDetectedProvider) }

    var keyIsEnabled by remember(existingKey?.id) {
        mutableStateOf(existingKey?.isEnabled ?: true)
    }
    var suspensionClearedInDialog by remember(existingKey?.id) {
        mutableStateOf(false)
    }

    var priority by remember { mutableIntStateOf(existingKey?.priority ?: nextPriority) }
    var name by remember {
        mutableStateOf(
            existingKey?.name ?: "${selectedProvider.displayName.substringBefore(" ")} #${nextPriority}"
        )
    }
    var userEditedNameManually by remember { mutableStateOf(existingKey != null) }
    var apiKeyText by remember { mutableStateOf(initialRawKey.trim()) }
    var rememberKeyOnDevice by remember {
        mutableStateOf(
            existingKey?.let { it.encryptedKey.isNotEmpty() || PollinationsImageManager.getSessionOnlyKey(it.id) == null } ?: true
        )
    }

    // Custom API Provider fields
    var customBaseUrl by remember { mutableStateOf(existingKey?.customBaseUrl.orEmpty()) }
    var customProtocol by remember { mutableStateOf(existingKey?.customProtocol ?: CustomApiProtocol.OPENAI.name) }

    // Supported Task/Modality Types for this key/model
    var selectedTasks by remember {
        mutableStateOf(
            existingKey?.parsedSupportedTasks ?: setOf(AiTaskType.TEXT, AiTaskType.IMAGE_INPUT, AiTaskType.FILE_INPUT)
        )
    }

    // Enabled Fallback Models within this key in priority order
    var enabledFallbackModels by remember {
        val initialExtras = existingKey?.fallbackModels
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        mutableStateOf(initialExtras)
    }
    var showFallbackModelsSection by remember { mutableStateOf(true) }

    // Models state sorted with Free models first, then best paid models
    var modelsList by remember(selectedProvider) {
        val initial = AiModelInfo.sortBestFirst(
            selectedProvider.suggestedModels.mapIndexed { idx, modelId ->
                AiModelInfo(
                    id = modelId,
                    displayName = modelId,
                    isFree = selectedProvider == ProviderType.GROQ || selectedProvider == ProviderType.GEMINI || modelId.contains("free", ignoreCase = true),
                    isRecommended = idx < 3
                )
            }
        ).toMutableList()

        if (existingKey != null && existingKey.provider == selectedProvider && initial.none { it.id == existingKey.selectedModel }) {
            initial.add(0, AiModelInfo(id = existingKey.selectedModel, displayName = existingKey.selectedModel, isRecommended = true))
        }
        mutableStateOf(initial.toList())
    }

    var selectedModel by remember {
        mutableStateOf(existingKey?.selectedModel ?: selectedProvider.defaultModel)
    }
    var customModelInput by remember { mutableStateOf(existingKey?.selectedModel ?: selectedProvider.defaultModel) }

    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchStatusMessage by remember { mutableStateOf<String?>(null) }
    var fetchStatusIsError by remember { mutableStateOf(false) }
    var fetchSuccessCount by remember { mutableStateOf<Int?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var activeModelFilter by remember { mutableStateOf(ModelFilterType.ALL) }
    var fallbackSearchQuery by remember { mutableStateOf("") }
    var fallbackModelFilter by remember { mutableStateOf(ModelFilterType.ALL) }
    var showModelSelectionModal by remember { mutableStateOf(false) }

    var showPlaygroundAdvanced by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(existingKey?.temperature ?: 0.7f) }
    var maxTokensText by remember { mutableStateOf((existingKey?.maxTokens ?: 4096).toString()) }
    var systemPrompt by remember { mutableStateOf(existingKey?.systemPrompt.orEmpty()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun applyProviderChange(newProvider: ProviderType, isAutoDetected: Boolean) {
        val changed = selectedProvider != newProvider
        selectedProvider = newProvider
        if (isAutoDetected) {
            autoDetectedProviderBadge = newProvider
        } else {
            autoDetectedProviderBadge = null
        }
        if (changed) {
            selectedModel = newProvider.defaultModel
            customModelInput = newProvider.defaultModel
            modelsList = AiModelInfo.sortBestFirst(
                newProvider.suggestedModels.mapIndexed { idx, it ->
                    AiModelInfo(
                        id = it,
                        displayName = it,
                        isFree = newProvider == ProviderType.GROQ || newProvider == ProviderType.GEMINI || it.contains("free", ignoreCase = true),
                        isRecommended = idx < 3
                    )
                }
            )
            if (!userEditedNameManually && existingKey == null) {
                name = "${newProvider.displayName.substringBefore(" ")} #${priority}"
            }
        }
    }

    fun triggerLiveFetch(provider: ProviderType, key: String) {
        if (key.isBlank()) return
        isFetchingModels = true
        fetchStatusMessage = AppStrings.tr(
            "טוען את כל המודלים משרתי ${provider.displayName} (חינמיים תחילה)...",
            "Loading all models from ${provider.displayName} (free models first)..."
        )
        fetchStatusIsError = false
        fetchSuccessCount = null

        scope.launch {
            val result = onFetchDetailedModels(provider, key)
            isFetchingModels = false
            if (result.isSuccess && result.models.isNotEmpty()) {
                val sorted = AiModelInfo.sortBestFirst(result.models)
                modelsList = sorted
                fetchSuccessCount = sorted.size
                val freeCount = sorted.count { it.effectivelyFree }
                fetchStatusMessage = AppStrings.tr(
                    "נטענו כל ${sorted.size} המודלים ($freeCount חינמיים בראש הרשימה 🟢)",
                    "Loaded all ${sorted.size} models ($freeCount free models sorted first 🟢)"
                )
                fetchStatusIsError = false

                if (sorted.none { it.id == selectedModel }) {
                    val best = sorted.first()
                    selectedModel = best.id
                    customModelInput = best.id
                }
            } else {
                fetchStatusIsError = true
                fetchStatusMessage = result.errorMessage ?: AppStrings.tr(
                    "שגיאה בשליפת המודלים. מוצגים מודלים מומלצים.",
                    "Error fetching models. Showing recommended models."
                )
                if (result.models.isNotEmpty()) {
                    modelsList = AiModelInfo.sortBestFirst(result.models)
                }
            }
        }
    }

    fun refreshExistingKeyModels() {
        if (apiKeyText.isNotBlank()) {
            triggerLiveFetch(selectedProvider, apiKeyText)
            return
        }
        if (existingKey != null && onFetchDetailedModelsForExistingKey != null) {
            isFetchingModels = true
            fetchStatusMessage = AppStrings.tr(
                "מרענן וסורק מודלים משרתי ${selectedProvider.displayName}...",
                "Refreshing models from ${selectedProvider.displayName}..."
            )
            fetchStatusIsError = false
            fetchSuccessCount = null
            scope.launch {
                val result = onFetchDetailedModelsForExistingKey(existingKey.id)
                isFetchingModels = false
                if (result.isSuccess && result.models.isNotEmpty()) {
                    val sorted = AiModelInfo.sortBestFirst(result.models)
                    modelsList = sorted
                    fetchSuccessCount = sorted.size
                    val freeCount = sorted.count { it.effectivelyFree }
                    fetchStatusMessage = AppStrings.tr(
                        "נטענו כל ${sorted.size} המודלים הזמינים ($freeCount חינמיים בראש הרשימה)!",
                        "Loaded all ${sorted.size} available models ($freeCount free models first)!"
                    )
                    fetchStatusIsError = false
                    if (sorted.none { it.id == selectedModel }) {
                        val best = sorted.first()
                        selectedModel = best.id
                        customModelInput = best.id
                    }
                } else {
                    fetchStatusIsError = true
                    fetchStatusMessage = result.errorMessage ?: AppStrings.tr(
                        "לא ניתן היה לרענן מודלים מהשרת כעת.",
                        "Could not refresh models from server right now."
                    )
                }
            }
        }
    }

    LaunchedEffect(existingKey?.id) {
        if (existingKey != null && onFetchDetailedModelsForExistingKey != null) {
            refreshExistingKeyModels()
        }
    }

    LaunchedEffect(apiKeyText, selectedProvider) {
        if (apiKeyText.isNotBlank() && apiKeyText.length >= 8) {
            delay(450)
            triggerLiveFetch(selectedProvider, apiKeyText)
        }
    }

    val filteredModels = remember(modelsList, searchQuery, activeModelFilter, selectedProvider) {
        AiModelInfo.sortBestFirst(
            modelsList.filter { model ->
                val matchesSearch = searchQuery.isBlank() ||
                    model.id.contains(searchQuery, ignoreCase = true) ||
                    model.displayName.contains(searchQuery, ignoreCase = true) ||
                    model.roleBadge.contains(searchQuery, ignoreCase = true)
                val matchesType = model.matchesFilter(activeModelFilter)
                matchesSearch && matchesType
            }
        )
    }

    // Centered Provider Selection Menu Dialog (replaces cramped DropdownMenu)
    if (providerMenuDialogOpen) {
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
            title = AppStrings.tr("בחירת ספק AI / סוג מפתח", "Select AI Provider / Key Type"),
            subtitle = AppStrings.tr(
                "המערכת מזהה אוטומטית לפי טקסט המפתח, ותמיד ניתן לשנות כאן ידנית",
                "Auto-detected from key text, and can always be changed manually here"
            ),
            options = providerOptions,
            selectedValue = selectedProvider,
            onSelect = { chosen ->
                applyProviderChange(chosen, isAutoDetected = false)
                if (apiKeyText.isNotBlank()) {
                    triggerLiveFetch(chosen, apiKeyText)
                }
            },
            onDismiss = { providerMenuDialogOpen = false }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides AppStrings.layoutDirection()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(horizontal = 10.dp, vertical = 14.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (existingKey == null) {
                                AppStrings.tr("הוספת מפתח API והגדרת מודלים", "Add API Key & Configure Models")
                            } else {
                                AppStrings.tr("עריכת מפתח, מודלים וסוגי משימות", "Edit Key, Models & Task Types")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.tr(
                                "זיהוי ספק אוטומטי • חינמיים בראש הרשימה • גרירת עדיפויות",
                                "Auto provider detection • Free models first • Drag priorities"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick Controls inside Edit Key Dialog (Enable/Disable, Timeout/Suspension Reset, Refresh, Duplicate, Delete)
                    if (existingKey != null) {
                        val now = System.currentTimeMillis()
                        val isSuspendedNow = !suspensionClearedInDialog && (
                            existingKey.isSuspended ||
                                existingKey.parsedModelSuspensions.any { it.value > now } ||
                                !existingKey.suspensionReason.isNullOrBlank()
                            )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSuspendedNow) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AppStrings.tr("סטטוס מפתח ופעולות מהירות", "Key Status & Quick Actions"),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (keyIsEnabled) {
                                                AppStrings.tr("🟢 המפתח פעיל ומוכן לשימוש", "🟢 Key is Enabled & Active")
                                            } else {
                                                AppStrings.tr("⏸️ המפתח כבוי כרגע", "⏸️ Key is Currently Disabled")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (keyIsEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = keyIsEnabled,
                                        onCheckedChange = { newEnabled ->
                                            keyIsEnabled = newEnabled
                                            onToggleKeyEnabled?.invoke(newEnabled)
                                            if (selectedProvider == ProviderType.POLLINATIONS) {
                                                PollinationsImageManager.setEngineEnabled(context, newEnabled)
                                            }
                                        },
                                        modifier = Modifier.testTag("dialog_key_enable_switch")
                                    )
                                }

                                if (isSuspendedNow && !existingKey.suspensionReason.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "⚠️ ${existingKey.suspensionReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                // Timeout / Suspension Reset + Refresh Models Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            suspensionClearedInDialog = true
                                            onClearSuspension?.invoke()
                                            Toast.makeText(
                                                context,
                                                AppStrings.tr("השהיית המפתח וחסימות Timeout אופסו בהצלחה! 🔄", "Key suspension & timeout blocks reset! 🔄"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("dialog_reset_timeout_btn"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = AppStrings.tr("אפס השהיה / Timeout", "Reset Timeout / Cooldown"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (selectedProvider != ProviderType.POLLINATIONS) {
                                        OutlinedButton(
                                            onClick = { refreshExistingKeyModels() },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("dialog_refresh_models_btn"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = AppStrings.tr("רענן רשימת מודלים", "Refresh Models"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Duplicate & Delete buttons inside Edit dialog
                                if (onDuplicateKey != null || onDeleteKey != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (onDuplicateKey != null && selectedProvider != ProviderType.POLLINATIONS) {
                                            OutlinedButton(
                                                onClick = { onDuplicateKey.invoke() },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = AppStrings.tr("שכפל מפתח", "Duplicate Key"),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (onDeleteKey != null && selectedProvider != ProviderType.POLLINATIONS) {
                                            OutlinedButton(
                                                onClick = {
                                                    onDeleteKey.invoke()
                                                    onDismiss()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = AppStrings.tr("מחק מפתח", "Delete Key"),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedProvider == ProviderType.POLLINATIONS) {
                        // Embedded Pollinations settings card directly inside Edit Key dialog
                        PollinationsConfigCard(
                            modifier = Modifier.fillMaxWidth(),
                            initiallyExpanded = true,
                            hasActiveGeminiKeyInRepo = false,
                            onModelChanged = { chosenModel ->
                                selectedModel = chosenModel
                                customModelInput = chosenModel
                            },
                            onEngineEnabledChanged = { isChecked ->
                                keyIsEnabled = isChecked
                                onToggleKeyEnabled?.invoke(isChecked)
                            }
                        )
                    } else {
                        // 1. TOP ELEMENT: API Key input - AUTO-DETECTS PROVIDER & FETCHES MODELS AUTOMATICALLY
                        Text(
                            text = AppStrings.tr(
                                "1. הזנת מפתח API (מזהה ספק ומודלים אוטומטית מיד עם ההזנה):",
                                "1. Enter API Key (Auto-detects provider & models immediately):"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { newText ->
                                val trimmed = newText.trim()
                                apiKeyText = trimmed
                                errorMessage = null

                                val detected = ProviderType.detectProviderFromKey(trimmed, name)
                                if (detected != null) {
                                    applyProviderChange(detected, isAutoDetected = true)
                                }
                            },
                            label = {
                                Text(
                                    if (existingKey == null) {
                                        AppStrings.tr("הדבק או הקלד מפתח API כאן", "Paste or type API Key here")
                                    } else {
                                        AppStrings.tr("מפתח API (שמור ומוצפן בחומרה 🔒)", "API Key (Hardware Encrypted 🔒)")
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    if (existingKey == null) selectedProvider.keyHint
                                    else AppStrings.tr(
                                        "•••••••••••••••••••• (שמור - הזן חדש לדריסה בלבד)",
                                        "•••••••••••••••••••• (Saved - enter new key to overwrite)"
                                    )
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                            if (pasted.isNotBlank()) {
                                                apiKeyText = pasted
                                                errorMessage = null
                                                val detected = ProviderType.detectProviderFromKey(pasted, name)
                                                if (detected != null) {
                                                    applyProviderChange(detected, isAutoDetected = true)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = AppStrings.tr("הדבק מפתח מהלוח", "Paste key from clipboard"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = AppStrings.tr("המפתח חסוי ומוצפן תמיד", "Key is always masked and encrypted"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            isError = errorMessage != null,
                            supportingText = {
                                if (errorMessage != null) {
                                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                } else if (existingKey != null) {
                                    Text(
                                        AppStrings.tr(
                                            "המפתח שמור ומוצפן (AES-256 GCM). השאר ריק לשמירתו, או הקלד מפתח חדש לדריסה.",
                                            "Key is saved & encrypted (AES-256 GCM). Leave blank to keep it, or type a new key to overwrite."
                                        ),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        AppStrings.tr(
                                            "ברגע שתדביק מפתח, הספק, הכינוי ורשימת המודלים יתעדכנו אוטומטית (מוצפן AES-256 GCM)",
                                            "As soon as you paste a key, the provider, nickname & model list update automatically"
                                        ),
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rememberKeyOnDevice = !rememberKeyOnDevice },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberKeyOnDevice,
                                onCheckedChange = { rememberKeyOnDevice = it }
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = AppStrings.tr(
                                    "זכור מפתח במכשיר (פרטיות: נשמר במכשיר רק אם מסומן 'זכור', וב-Gemini נשלח ישירות לגוגל)",
                                    "Remember key on device (Privacy: saved on device ONLY if 'Remember' is checked, sent directly to Google)"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Automatic Live Model Loading Status Banner (No manual scan button needed)
                    AnimatedVisibility(visible = isFetchingModels || fetchStatusMessage != null) {
                        Surface(
                            color = when {
                                isFetchingModels -> MaterialTheme.colorScheme.surfaceVariant
                                fetchStatusIsError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                fetchSuccessCount != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isFetchingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = when {
                                            fetchStatusIsError -> Icons.Default.Warning
                                            fetchSuccessCount != null -> Icons.Default.CheckCircle
                                            else -> Icons.Default.CloudSync
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            fetchStatusIsError -> MaterialTheme.colorScheme.error
                                            fetchSuccessCount != null -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fetchStatusMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (fetchSuccessCount != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (fetchStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = autoDetectedProviderBadge != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = AppStrings.tr(
                                            "המערכת זיהתה ושייכה אוטומטית לפי טקסט המפתח: ${autoDetectedProviderBadge?.displayName.orEmpty()} (ניתן לשנות ידנית למטה)",
                                            "Auto-detected from key text: ${autoDetectedProviderBadge?.displayName.orEmpty()} (can be changed manually below)"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { providerMenuDialogOpen = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(AppStrings.tr("שנה ידנית", "Change"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 2. Provider Selection (Auto-detected + Manual Override)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.tr(
                                "2. ספק AI / שירות (מזוהה אוטומטית או בחירה ידנית):",
                                "2. AI Provider / Service (Auto-detected or manual choice):"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        onClick = { providerMenuDialogOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_dropdown_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = selectedProvider.displayName.take(1),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = selectedProvider.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = when (selectedProvider) {
                                            ProviderType.GROQ -> AppStrings.tr("מודלי קוד פתוח במהירות קיצונית (Llama, DeepSeek)", "Ultra-fast open models (Llama, DeepSeek)")
                                            ProviderType.OPENROUTER -> AppStrings.tr("גישה מאוחדת למאות מודלים חינמיים ומתקדמים", "Unified access to hundreds of free & top models")
                                            ProviderType.GEMINI -> AppStrings.tr("דגמי Gemini 2.5 / 2.0 של Google (טקסט, ראייה ותמונה)", "Google Gemini 2.5 / 2.0 (text, vision & image)")
                                            ProviderType.OPENAI -> AppStrings.tr("GPT-4o, o3-mini, DALL-E 3 ו-TTS", "GPT-4o, o3-mini, DALL-E 3 & TTS")
                                            ProviderType.CLAUDE -> AppStrings.tr("Claude 3.7 Sonnet, Claude 3.5 Haiku", "Claude 3.7 Sonnet, Claude 3.5 Haiku")
                                            ProviderType.DEEPSEEK -> AppStrings.tr("DeepSeek-V3 ו-DeepSeek-R1", "DeepSeek-V3 & DeepSeek-R1")
                                            ProviderType.MISTRAL -> AppStrings.tr("Mistral Large, Codestral ו-Pixtral", "Mistral Large, Codestral & Pixtral")
                                            ProviderType.GROK -> AppStrings.tr("xAI Grok 3 ו-Grok 2 Vision", "xAI Grok 3 & Grok 2 Vision")
                                            ProviderType.HUGGINGFACE -> AppStrings.tr("HuggingFace Serverless Inference", "HuggingFace Serverless Inference")
                                            ProviderType.POLLINATIONS -> AppStrings.tr("יצירת תמונות מובנית (Flux) ללא צורך במפתח API", "Built-in Image Generation (Flux) without API key")
                                            ProviderType.CUSTOM -> AppStrings.tr("שרת API מותאם אישית (OpenAI / Gemini / Claude)", "Custom API Server (OpenAI / Gemini / Claude)")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = AppStrings.tr("שנה ספק", "Change"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (providerMenuDialogOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Direct Link Button to Provider's API Key Page
                    OutlinedButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.apiKeysWebUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, selectedProvider.apiKeysWebUrl, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_provider_api_url_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.tr(
                                "🔗 קישור ישיר לעמוד מפתחות ה-API של ${selectedProvider.displayName.substringBefore(" ")} (${selectedProvider.documentationUrl})",
                                "🔗 Direct link to ${selectedProvider.displayName.substringBefore(" ")} API Keys page (${selectedProvider.documentationUrl})"
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Custom API Provider Configuration
                    AnimatedVisibility(visible = selectedProvider == ProviderType.CUSTOM) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = AppStrings.tr(
                                        "🛠️ הגדרות מפתח ושרת API מותאם אישית",
                                        "🛠️ Custom API Server & Protocol Configuration"
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = AppStrings.tr(
                                        "בחר את פרוטוקול ה-API של השרת שלך:",
                                        "Select your server's API protocol:"
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CustomApiProtocol.entries.forEach { proto ->
                                        val isProtoSelected = customProtocol == proto.name
                                        FilterChip(
                                            selected = isProtoSelected,
                                            onClick = { customProtocol = proto.name },
                                            label = { Text(proto.displayName, fontSize = 11.sp) },
                                            leadingIcon = if (isProtoSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null
                                        )
                                    }
                                }

                                val currentProtoObj = CustomApiProtocol.entries.find { it.name == customProtocol } ?: CustomApiProtocol.OPENAI
                                OutlinedTextField(
                                    value = customBaseUrl,
                                    onValueChange = { customBaseUrl = it.trim() },
                                    label = { Text(AppStrings.tr("כתובת שרת (Base URL / Endpoint)", "Server Base URL / Endpoint")) },
                                    placeholder = { Text(currentProtoObj.defaultEndpointHint, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_base_url_input")
                                )
                            }
                        }
                    }

                    // Key Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            userEditedNameManually = true
                        },
                        label = { Text(AppStrings.tr("כינוי למפתח (מתעדכן אוטומטית לפי הספק)", "Key Nickname (Auto-updates by provider)")) },
                        placeholder = { Text(AppStrings.tr("למשל: Groq ראשי או Gemini לתמונות", "e.g., Main Groq or Gemini Vision")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_name_input")
                    )

                    if (selectedProvider != ProviderType.POLLINATIONS) {
                        // 3. Model Selection Section (Free First + Best Quality)
                        val currentModelId = customModelInput.ifBlank { selectedModel.ifBlank { selectedProvider.defaultModel } }
                    val currentModelInfo = modelsList.find { it.id == currentModelId } ?: AiModelInfo(id = currentModelId)

                    Text(
                        text = AppStrings.tr(
                            "3. מודל ראשי במפתח זה (נטען אוטומטית • החינמיים והטובים בראש הרשימה):",
                            "3. Primary Model for this Key (Auto-loaded • Free & Best Models First):"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelSelectionModal = true }
                            .testTag("model_picker_trigger")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentModelId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 3.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = currentModelInfo.roleBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }

                                        val isModelFree = currentModelInfo.effectivelyFree
                                        Surface(
                                            color = if (isModelFree) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (isModelFree) AppStrings.tr("🟢 חינמי", "🟢 Free") else AppStrings.tr("💳 בתשלום", "💳 Paid"),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isModelFree) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showModelSelectionModal = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(AppStrings.tr("החלף מודל (${modelsList.size})", "Change (${modelsList.size})"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // 3. Task / Modality Type Assignment
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = AppStrings.tr(
                                    "3. סוגי המודל והבקשות שבהם יטפל מפתח זה:",
                                    "3. Model & Request Types Handled by this Key:"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppStrings.tr(
                                    "הגדר לאילו סוגי משימות ישמש המודל (טקסט רגיל, תמונה, ייצור תמונה, ייצור סאונד, ייצור וידאו, קובץ):",
                                    "Define which task types this model handles (text, image input, image generation, sound generation, video generation, file):"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AiTaskType.entries.forEach { task ->
                                    val isSelected = task in selectedTasks
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTasks = if (isSelected && selectedTasks.size > 1) {
                                                selectedTasks - task
                                            } else {
                                                selectedTasks + task
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "${task.emoji} ${AppStrings.tr(task.titleHe, task.name)}",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    // 4. Drag-and-Drop Priority Order of Fallback Models within this Key
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppStrings.tr(
                                            "4. מודלי גיבוי וסידור עדיפויות בגרירה בתוך המפתח",
                                            "4. Fallback Models & Drag-and-Drop Priority within Key"
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (enabledFallbackModels.isEmpty()) {
                                            AppStrings.tr(
                                                "ברירת מחדל: שאר המודלים מכובים. הדלק מודלים נוספים וגרור אותם לקביעת סדר הגיבוי.",
                                                "Default: other models are OFF. Enable extra models and drag them to set fallback order."
                                            )
                                        } else {
                                            AppStrings.tr(
                                                "מופעלים ${enabledFallbackModels.size} מודלי גיבוי (גרור כדי לשנות סדר עדיפות):",
                                                "${enabledFallbackModels.size} fallback models enabled (drag to reorder priority):"
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { showFallbackModelsSection = !showFallbackModelsSection }) {
                                    Text(
                                        text = if (showFallbackModelsSection) {
                                            AppStrings.tr("הסתר", "Hide")
                                        } else {
                                            AppStrings.tr("נהל מודלים (${enabledFallbackModels.size})", "Manage Models (${enabledFallbackModels.size})")
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showFallbackModelsSection) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Quick bulk controls for fallback models
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val topFree = modelsList
                                                    .filter { it.id != currentModelId && it.effectivelyFree }
                                                    .take(4)
                                                    .map { it.id }
                                                val additions = if (topFree.isNotEmpty()) topFree else modelsList.filter { it.id != currentModelId }.take(3).map { it.id }
                                                enabledFallbackModels = (enabledFallbackModels + additions).distinct()
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = AppStrings.tr("✨ הפעל מודלי גיבוי מומלצים", "✨ Enable Recommended Fallbacks"),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (enabledFallbackModels.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { enabledFallbackModels = emptyList() },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = AppStrings.tr("כבה הכל", "Disable All"),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (enabledFallbackModels.isNotEmpty()) {
                                        Text(
                                            text = AppStrings.tr(
                                                "✋ גרור את סמל הגרירה לשינוי סדר המעבר האוטומטי (אחרי $currentModelId):",
                                                "✋ Drag the handle to reorder automatic fallback (after $currentModelId):"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        DraggableReorderColumn(
                                            items = enabledFallbackModels,
                                            keySelector = { it },
                                            onReorderFinished = { newOrder ->
                                                enabledFallbackModels = newOrder
                                            },
                                            verticalSpacing = 6.dp,
                                            scrollState = dialogScrollState
                                        ) { idx, fallbackId, isDragging, dragModifier ->
                                            Surface(
                                                color = if (isDragging) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    width = if (isDragging) 1.5.dp else 0.5.dp,
                                                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        DragHandleBadge(
                                                            priorityNumber = idx + 2,
                                                            isDragging = isDragging,
                                                            dragHandleModifier = dragModifier,
                                                            labelText = "#${idx + 2}"
                                                        )
                                                        Text(
                                                            text = fallbackId,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            val oldPrimary = currentModelId
                                                            selectedModel = fallbackId
                                                            customModelInput = fallbackId
                                                            enabledFallbackModels = listOf(oldPrimary) + enabledFallbackModels.filter { it != fallbackId && it != oldPrimary }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text(
                                                            text = AppStrings.tr("⭐ ראשי", "⭐ Primary"),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Switch(
                                                        checked = true,
                                                        onCheckedChange = {
                                                            enabledFallbackModels = enabledFallbackModels.filter { it != fallbackId }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = AppStrings.tr(
                                            "הדלק או כבה מודלים נוספים במפתח זה (ללא הגבלת כמות • חינמיים ראשונים):",
                                            "Toggle additional models in this key ON/OFF (unlimited • free first):"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = fallbackSearchQuery,
                                        onValueChange = { fallbackSearchQuery = it },
                                        placeholder = { Text(AppStrings.tr("חפש מודל גיבוי להוספה...", "Search fallback model to add..."), fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        trailingIcon = {
                                            if (fallbackSearchQuery.isNotBlank()) {
                                                IconButton(onClick = { fallbackSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Filter chips by model type in fallback section too
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ModelFilterType.entries.forEach { filterType ->
                                            val isSel = fallbackModelFilter == filterType
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { fallbackModelFilter = filterType },
                                                label = {
                                                    Text(
                                                        "${filterType.emoji} ${AppStrings.tr(filterType.labelHe, filterType.labelEn)}",
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    val availableCandidates = remember(modelsList, currentModelId, enabledFallbackModels, fallbackSearchQuery, fallbackModelFilter) {
                                        modelsList.filter { candidate ->
                                            candidate.id != currentModelId &&
                                                candidate.id !in enabledFallbackModels &&
                                                candidate.matchesFilter(fallbackModelFilter) &&
                                                (fallbackSearchQuery.isBlank() ||
                                                    candidate.id.contains(fallbackSearchQuery, ignoreCase = true) ||
                                                    candidate.displayName.contains(fallbackSearchQuery, ignoreCase = true) ||
                                                    candidate.roleBadge.contains(fallbackSearchQuery, ignoreCase = true))
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(availableCandidates, key = { it.id }) { candidate ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = candidate.id,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            if (candidate.effectivelyFree) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = AppStrings.tr("🟢 חינמי", "🟢 Free"),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF16A34A)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = candidate.roleBadge,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Switch(
                                                        checked = false,
                                                        onCheckedChange = { checked ->
                                                            if (checked) {
                                                                enabledFallbackModels = enabledFallbackModels + candidate.id
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Modal Dialog for Centered, Unlimited, Free-First Model Selection + Capability Filters
                    if (showModelSelectionModal) {
                        val totalFreeCount = remember(modelsList) { modelsList.count { it.effectivelyFree } }
                        AlertDialog(
                            onDismissRequest = { showModelSelectionModal = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false),
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Column {
                                    Text(
                                        text = AppStrings.tr(
                                            "בחירת מודל (${selectedProvider.displayName})",
                                            "Select Model (${selectedProvider.displayName})"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = AppStrings.tr(
                                            "כל המודלים החינמיים ($totalFreeCount) נטענים ראשונים בראש הרשימה, ואחריהם כל השאר (סה\"כ ${modelsList.size}) 🟢⭐",
                                            "All free models ($totalFreeCount) load first at the top, followed by the rest (total ${modelsList.size}) 🟢⭐"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text(AppStrings.tr("חפש מודל (שם, תפקיד, תמונה, שמע, free)...", "Search model (name, role, vision, audio, free)..."), fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotBlank()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = AppStrings.tr("נקה", "Clear"), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Model Type / Capability Filter Chips (All, Free, Text, Vision, Image Gen, Audio, Video, Files)
                                    Text(
                                        text = AppStrings.tr("סינון לפי סוג מודל:", "Filter by model type:"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModelFilterType.entries.forEach { filterType ->
                                            val isSelected = activeModelFilter == filterType
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { activeModelFilter = filterType },
                                                label = {
                                                    Text(
                                                        text = "${filterType.emoji} ${AppStrings.tr(filterType.labelHe, filterType.labelEn)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                                } else null
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val filteredFreeCount = filteredModels.count { it.effectivelyFree }
                                        Text(
                                            text = AppStrings.tr(
                                                "מוצגים: ${filteredModels.size} מודלים ($filteredFreeCount חינמיים ראשונים)",
                                                "Showing: ${filteredModels.size} models ($filteredFreeCount free first)"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 310.dp)
                                    ) {
                                        if (filteredModels.isEmpty()) {
                                            Text(
                                                text = AppStrings.tr("לא נמצאו מודלים העונים לסינון.", "No models match your filter."),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(filteredModels, key = { it.id }) { modelInfo ->
                                                    val isSelected = currentModelId == modelInfo.id
                                                    val isModelFree = modelInfo.effectivelyFree
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                        else MaterialTheme.colorScheme.surfaceContainer,
                                                        border = BorderStroke(
                                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                selectedModel = modelInfo.id
                                                                customModelInput = modelInfo.id
                                                                selectedTasks = selectedTasks + modelInfo.capabilities
                                                                showModelSelectionModal = false
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RadioButton(
                                                                selected = isSelected,
                                                                onClick = {
                                                                    selectedModel = modelInfo.id
                                                                    customModelInput = modelInfo.id
                                                                    selectedTasks = selectedTasks + modelInfo.capabilities
                                                                    showModelSelectionModal = false
                                                                },
                                                                colors = RadioButtonDefaults.colors(
                                                                    selectedColor = MaterialTheme.colorScheme.primary
                                                                ),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = modelInfo.id,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                    if (modelInfo.isRecommended) {
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Icon(
                                                                            imageVector = Icons.Default.Star,
                                                                            contentDescription = AppStrings.tr("מומלץ בראש הרשימה", "Top recommended"),
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(13.dp)
                                                                        )
                                                                    }
                                                                }

                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(top = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = modelInfo.roleBadge,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontSize = 10.sp,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                    Text(
                                                                        text = "•",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.outline
                                                                    )
                                                                    Text(
                                                                        text = if (isModelFree) AppStrings.tr("חינמי 🟢", "Free 🟢") else AppStrings.tr("בתשלום 💳", "Paid 💳"),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isModelFree) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                    if (modelInfo.contextWindow != null) {
                                                                        Text(
                                                                            text = "• ${modelInfo.contextWindow / 1000}K",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            fontSize = 10.sp,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                                    OutlinedTextField(
                                        value = customModelInput,
                                        onValueChange = {
                                            customModelInput = it.trim()
                                            selectedModel = it.trim()
                                        },
                                        label = { Text(AppStrings.tr("או הזן שם מודל מותאם ידנית", "Or enter custom model ID manually"), fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_model_input")
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showModelSelectionModal = false }) {
                                    Text(AppStrings.tr("אישור", "Confirm"))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showModelSelectionModal = false }) {
                                    Text(AppStrings.tr("סגור", "Close"))
                                }
                            }
                        )
                    }

                    // Playground Advanced Settings Toggle
                    OutlinedButton(
                        onClick = { showPlaygroundAdvanced = !showPlaygroundAdvanced },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showPlaygroundAdvanced) {
                                AppStrings.tr("הסתר פרמטרים של Playground", "Hide Playground Parameters")
                            } else {
                                AppStrings.tr("הגדרות מתקדמות (טמפרטורה, System Prompt, טוקנים)", "Advanced Settings (Temperature, System Prompt, Max Tokens)")
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = showPlaygroundAdvanced) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = AppStrings.tr("טמפרטורה (Temperature):", "Temperature:"),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.2f", temperature),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = temperature,
                                    onValueChange = { temperature = it },
                                    valueRange = 0f..1.5f,
                                    steps = 14
                                )

                                OutlinedTextField(
                                    value = maxTokensText,
                                    onValueChange = { if (it.all { ch -> ch.isDigit() }) maxTokensText = it },
                                    label = { Text(AppStrings.tr("מקסימום טוקנים לתשובה (Max Tokens)", "Max Output Tokens")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = systemPrompt,
                                    onValueChange = { systemPrompt = it },
                                    label = { Text(AppStrings.tr("הנחיית מערכת (System Prompt מותאם)", "Custom System Prompt")) },
                                    placeholder = { Text(AppStrings.tr("ענה תמיד בצורה תמציתית ומדויקת...", "Always answer concisely and accurately...")) },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                    // Priority selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.tr("סדר עדיפות בניתוב (1 = הכי גבוה):", "Routing Priority (1 = Highest):"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppStrings.tr(
                                    "בעמוד המפתחות ניתן גם לגרור מפתחות כדי לשנות את סדר הקדימויות שלהם.",
                                    "In the Keys screen you can also drag and drop keys to reorder priorities."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (priority > 1) priority-- },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = AppStrings.tr("הורד עדיפות", "Lower priority"))
                            }
                            Text(
                                text = priority.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { priority++ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = AppStrings.tr("העלה עדיפות", "Raise priority"))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (existingKey == null && apiKeyText.isBlank() && selectedProvider != ProviderType.POLLINATIONS) {
                            errorMessage = AppStrings.tr("נא להזין מפתח API תקין", "Please enter a valid API key")
                            return@Button
                        }
                        val finalModel = customModelInput.ifBlank { selectedModel.ifBlank { selectedProvider.defaultModel } }
                        val parsedTokens = maxTokensText.toIntOrNull()?.coerceIn(64, 32768) ?: 4096
                        onSave(
                            selectedProvider,
                            name.trim(),
                            apiKeyText.trim(),
                            finalModel,
                            priority,
                            temperature,
                            parsedTokens,
                            systemPrompt.trim().takeIf { it.isNotBlank() },
                            AiTaskType.toCsv(selectedTasks),
                            enabledFallbackModels.filter { it != finalModel }.joinToString(","),
                            customBaseUrl.trim().takeIf { it.isNotBlank() },
                            customProtocol,
                            rememberKeyOnDevice,
                            keyIsEnabled
                        )
                    },
                    modifier = Modifier.testTag("save_api_key_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(AppStrings.tr("שמור מפתח והגדרות", "Save Key & Settings"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }
}
