package com.example.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.data.model.ProviderType
import com.example.util.AppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddKeyDialog(
    existingKey: ApiKeyEntity? = null,
    nextPriority: Int = 1,
    onFetchDetailedModels: suspend (ProviderType, String) -> FetchModelsResponse,
    onFetchDetailedModelsForExistingKey: (suspend (String) -> FetchModelsResponse)? = null,
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
        customProtocol: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(existingKey?.provider ?: ProviderType.GROQ) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var name by remember {
        mutableStateOf(
            existingKey?.name ?: "${selectedProvider.displayName} #${nextPriority}"
        )
    }
    var apiKeyText by remember { mutableStateOf("") }

    // Custom API Provider fields (Task #17)
    var customBaseUrl by remember { mutableStateOf(existingKey?.customBaseUrl.orEmpty()) }
    var customProtocol by remember { mutableStateOf(existingKey?.customProtocol ?: CustomApiProtocol.OPENAI.name) }

    // Supported Task/Modality Types for this key/model (Tasks #2 & #6)
    var selectedTasks by remember {
        mutableStateOf(
            existingKey?.parsedSupportedTasks ?: setOf(AiTaskType.TEXT, AiTaskType.IMAGE_INPUT, AiTaskType.FILE_INPUT)
        )
    }

    // Enabled Fallback Models within this key in priority order (Task #3 - default empty/off)
    var enabledFallbackModels by remember {
        val initialExtras = existingKey?.fallbackModels
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        mutableStateOf(initialExtras)
    }
    var showFallbackModelsSection by remember { mutableStateOf(enabledFallbackModels.isNotEmpty()) }

    // Models state sorted with best models first (Task #5)
    var modelsList by remember(selectedProvider) {
        val initial = AiModelInfo.sortBestFirst(
            selectedProvider.suggestedModels.mapIndexed { idx, modelId ->
                AiModelInfo(
                    id = modelId,
                    displayName = modelId,
                    isFree = selectedProvider == ProviderType.GROQ || modelId.contains("free", ignoreCase = true),
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
    var showOnlyFreeModels by remember { mutableStateOf(selectedProvider == ProviderType.OPENROUTER) }
    var showModelSelectionModal by remember { mutableStateOf(false) }

    var priority by remember { mutableIntStateOf(existingKey?.priority ?: nextPriority) }
    var showPlaygroundAdvanced by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(existingKey?.temperature ?: 0.7f) }
    var maxTokensText by remember { mutableStateOf((existingKey?.maxTokens ?: 4096).toString()) }
    var systemPrompt by remember { mutableStateOf(existingKey?.systemPrompt.orEmpty()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerLiveFetch(provider: ProviderType, key: String) {
        if (key.isBlank()) return
        isFetchingModels = true
        fetchStatusMessage = AppStrings.tr(
            "שולף וממיין מודלים מובילים משרתי ${provider.displayName}...",
            "Fetching & sorting top models from ${provider.displayName}..."
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
                fetchStatusMessage = AppStrings.tr(
                    "נמצאו ${sorted.size} מודלים (המודלים הטובים ביותר בראש הרשימה ⭐)",
                    "Found ${sorted.size} models (best models sorted first ⭐)"
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

    LaunchedEffect(existingKey?.id) {
        if (existingKey != null && onFetchDetailedModelsForExistingKey != null) {
            isFetchingModels = true
            fetchStatusMessage = AppStrings.tr(
                "שולף אוטומטית מודלים מאומתים משרתי ${selectedProvider.displayName}...",
                "Automatically fetching verified models from ${selectedProvider.displayName}..."
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
                    fetchStatusMessage = AppStrings.tr(
                        "נמצאו ${sorted.size} מודלים זמינים (הטובים ביותר בראש הרשימה)!",
                        "Found ${sorted.size} available models (best models at top)!"
                    )
                    fetchStatusIsError = false
                    if (sorted.none { it.id == selectedModel }) {
                        val best = sorted.first()
                        selectedModel = best.id
                        customModelInput = best.id
                    }
                }
            }
        }
    }

    LaunchedEffect(apiKeyText, selectedProvider) {
        if (apiKeyText.isNotBlank() && apiKeyText.length >= 8) {
            delay(500)
            triggerLiveFetch(selectedProvider, apiKeyText)
        }
    }

    val filteredModels = remember(modelsList, searchQuery, showOnlyFreeModels, selectedProvider) {
        AiModelInfo.sortBestFirst(
            modelsList.filter { model ->
                val matchesSearch = searchQuery.isBlank() ||
                    model.id.contains(searchQuery, ignoreCase = true) ||
                    model.displayName.contains(searchQuery, ignoreCase = true) ||
                    model.roleBadge.contains(searchQuery, ignoreCase = true)
                val matchesFree = !showOnlyFreeModels || model.isFree
                matchesSearch && matchesFree
            }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides AppStrings.layoutDirection()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 8.dp),
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
                                "מיון מודלים חכם • הגדרת משימות • מודלי גיבוי",
                                "Smart model sorting • Task routing • Fallback models"
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
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Security Notice Banner
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.tr(
                                    "המפתח מוצפן בחומרת המכשיר (AES-256 GCM). המודלים הטובים ביותר מוצגים תמיד בראש הרשימה.",
                                    "Key is hardware-encrypted (AES-256 GCM). Best models are always sorted first."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 1. Provider Selection Dropdown
                    Text(
                        text = AppStrings.tr(
                            "1. בחר ספק AI / שירות (או מפתח מותאם אישית):",
                            "1. Choose AI Provider / Service (or Custom API):"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { providerDropdownExpanded = !providerDropdownExpanded },
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
                                        modifier = Modifier.size(32.dp)
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
                                        Text(
                                            text = selectedProvider.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = when (selectedProvider) {
                                                ProviderType.GROQ -> AppStrings.tr("מודלי קוד פתוח במהירות קיצונית (Llama, DeepSeek)", "Ultra-fast open models (Llama, DeepSeek)")
                                                ProviderType.OPENROUTER -> AppStrings.tr("גישה מאוחדת למאות מודלים מובילים וחינמיים", "Unified access to hundreds of top & free models")
                                                ProviderType.GEMINI -> AppStrings.tr("דגמי Gemini 2.5 / 2.0 של Google (טקסט, ראייה ותמונה)", "Google Gemini 2.5 / 2.0 (text, vision & image)")
                                                ProviderType.OPENAI -> AppStrings.tr("GPT-4o, o3-mini, DALL-E 3 ו-TTS", "GPT-4o, o3-mini, DALL-E 3 & TTS")
                                                ProviderType.CLAUDE -> AppStrings.tr("Claude 3.7 Sonnet, Claude 3.5 Haiku", "Claude 3.7 Sonnet, Claude 3.5 Haiku")
                                                ProviderType.DEEPSEEK -> AppStrings.tr("DeepSeek-V3 ו-DeepSeek-R1", "DeepSeek-V3 & DeepSeek-R1")
                                                ProviderType.MISTRAL -> AppStrings.tr("Mistral Large, Codestral ו-Pixtral", "Mistral Large, Codestral & Pixtral")
                                                ProviderType.GROK -> AppStrings.tr("xAI Grok 3 ו-Grok 2 Vision", "xAI Grok 3 & Grok 2 Vision")
                                                ProviderType.HUGGINGFACE -> AppStrings.tr("HuggingFace Serverless Inference", "HuggingFace Serverless Inference")
                                                ProviderType.CUSTOM -> AppStrings.tr("שרת API מותאם אישית (OpenAI / Gemini / Claude)", "Custom API Server (OpenAI / Gemini / Claude)")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (providerDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = providerDropdownExpanded,
                            onDismissRequest = { providerDropdownExpanded = false },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            ProviderType.values().forEach { provider ->
                                val isSelected = selectedProvider == provider
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = provider.displayName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${provider.keyHint} • ${provider.documentationUrl}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedProvider = provider
                                        selectedModel = provider.defaultModel
                                        customModelInput = provider.defaultModel
                                        modelsList = AiModelInfo.sortBestFirst(
                                            provider.suggestedModels.mapIndexed { idx, it ->
                                                AiModelInfo(
                                                    id = it,
                                                    displayName = it,
                                                    isFree = provider == ProviderType.GROQ || it.contains("free", ignoreCase = true),
                                                    isRecommended = idx < 3
                                                )
                                            }
                                        )
                                        showOnlyFreeModels = provider == ProviderType.OPENROUTER
                                        if (existingKey == null) {
                                            name = "${provider.displayName} #${priority}"
                                        }
                                        providerDropdownExpanded = false
                                        if (apiKeyText.isNotBlank()) {
                                            triggerLiveFetch(provider, apiKeyText)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Direct Link Button to Provider's API Key Page (Task #4)
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

                    // Custom API Provider Configuration (Task #17)
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
                        onValueChange = { name = it },
                        label = { Text(AppStrings.tr("כינוי למפתח", "Key Nickname")) },
                        placeholder = { Text(AppStrings.tr("למשל: Groq ראשי או Gemini לתמונות", "e.g., Main Groq or Gemini Vision")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_name_input")
                    )

                    // API Key input - PERMANENTLY MASKED & SECURE
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it.trim()
                            errorMessage = null
                        },
                        label = {
                            Text(
                                if (existingKey == null) {
                                    AppStrings.tr("מפתח API (${selectedProvider.keyHint})", "API Key (${selectedProvider.keyHint})")
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
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = AppStrings.tr("המפתח חסוי ומוצפן תמיד", "Key is always masked and encrypted"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = {
                            if (errorMessage != null) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else if (existingKey != null) {
                                Text(
                                    AppStrings.tr(
                                        "המפתח שמור ומוצפן. השאר ריק לשמירתו, או הקלד מפתח חדש לדריסה.",
                                        "Key is saved & encrypted. Leave blank to keep it, or type a new key to overwrite."
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                Text(
                                    AppStrings.tr(
                                        "המפתח יוצפן בחומרה ולא יוצג בגלוי לעולם",
                                        "Key will be hardware-encrypted and never shown in plain text"
                                    ),
                                    fontSize = 11.sp
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    // Live Fetch Action Button & Dynamic Banner
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
                        Column(modifier = Modifier.padding(10.dp)) {
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
                                        imageVector = when {
                                            isFetchingModels -> Icons.Default.CloudSync
                                            fetchStatusIsError -> Icons.Default.Warning
                                            fetchSuccessCount != null -> Icons.Default.CheckCircle
                                            else -> Icons.Default.Refresh
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            fetchStatusIsError -> MaterialTheme.colorScheme.error
                                            fetchSuccessCount != null -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = fetchStatusMessage ?: AppStrings.tr(
                                            "לחץ לבדיקת המפתח ושליפת מודלים עדכניים מהשרת",
                                            "Tap to verify key and fetch live models from server"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (fetchSuccessCount != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (fetchStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (apiKeyText.isBlank()) {
                                            errorMessage = AppStrings.tr(
                                                "הזן תחילה מפתח API כדי לשלוף מודלים מהשרת",
                                                "Enter an API key first to fetch models from server"
                                            )
                                            return@Button
                                        }
                                        triggerLiveFetch(selectedProvider, apiKeyText)
                                    },
                                    enabled = !isFetchingModels,
                                    modifier = Modifier.height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(AppStrings.tr("שלוף מ-API", "Fetch Models"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 2. Model Selection Section (Sorted Best First - Task #5 & #6)
                    val currentModelId = customModelInput.ifBlank { selectedModel.ifBlank { selectedProvider.defaultModel } }
                    val currentModelInfo = modelsList.find { it.id == currentModelId } ?: AiModelInfo(id = currentModelId)

                    Text(
                        text = AppStrings.tr(
                            "2. מודל ראשי במפתח זה (הטובים ביותר בראש הרשימה):",
                            "2. Primary Model for this Key (Best Models First):"
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

                                        val isModelFree = currentModelInfo.isFree || currentModelId.contains(":free", ignoreCase = true)
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
                                Text(AppStrings.tr("החלף מודל", "Change Model"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // 3. Task / Modality Type Assignment (Tasks #2 & #6)
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

                    // 4. Priority Order of Fallback Models within this Key (Task #3)
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
                                            "4. מודלי גיבוי וסדר עדיפויות בתוך המפתח",
                                            "4. Fallback Models & Priority Order within Key"
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (enabledFallbackModels.isEmpty()) {
                                            AppStrings.tr(
                                                "ברירת מחדל: שאר המודלים מכובים. הדלק מודלים נוספים למעבר אוטומטי אם המודל הראשי לא זמין.",
                                                "Default: all other models are OFF. Turn on extra models to auto-fallback if primary is unavailable."
                                            )
                                        } else {
                                            AppStrings.tr(
                                                "מופעלים ${enabledFallbackModels.size} מודלי גיבוי לפי סדר עדיפות:",
                                                "${enabledFallbackModels.size} fallback models enabled in priority order:"
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
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (enabledFallbackModels.isNotEmpty()) {
                                        Text(
                                            text = AppStrings.tr(
                                                "סדר מעבר אוטומטי במפתח זה (אחרי $currentModelId):",
                                                "Auto-fallback order in this key (after $currentModelId):"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        enabledFallbackModels.forEachIndexed { idx, fallbackId ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "#${idx + 2} • $fallbackId",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = {
                                                                if (idx > 0) {
                                                                    val mut = enabledFallbackModels.toMutableList()
                                                                    val tmp = mut[idx - 1]
                                                                    mut[idx - 1] = mut[idx]
                                                                    mut[idx] = tmp
                                                                    enabledFallbackModels = mut
                                                                }
                                                            },
                                                            enabled = idx > 0,
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowUpward, contentDescription = AppStrings.tr("העלה", "Move up"), modifier = Modifier.size(15.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (idx < enabledFallbackModels.size - 1) {
                                                                    val mut = enabledFallbackModels.toMutableList()
                                                                    val tmp = mut[idx + 1]
                                                                    mut[idx + 1] = mut[idx]
                                                                    mut[idx] = tmp
                                                                    enabledFallbackModels = mut
                                                                }
                                                            },
                                                            enabled = idx < enabledFallbackModels.size - 1,
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowDownward, contentDescription = AppStrings.tr("הורד", "Move down"), modifier = Modifier.size(15.dp))
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
                                    }

                                    Text(
                                        text = AppStrings.tr(
                                            "הדלק או כבה מודלים נוספים במפתח זה (ברירת מחדל: כבוי):",
                                            "Toggle additional models in this key ON/OFF (default: OFF):"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    modelsList
                                        .filter { it.id != currentModelId && it.id !in enabledFallbackModels }
                                        .take(12)
                                        .forEach { candidate ->
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
                                                        Text(
                                                            text = candidate.id,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium
                                                        )
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

                    // Modal Dialog for Clean, Directional Model Selection
                    if (showModelSelectionModal) {
                        AlertDialog(
                            onDismissRequest = { showModelSelectionModal = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false),
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .padding(vertical = 8.dp),
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
                                            "המודלים הטובים והמומלצים ביותר מופיעים אוטומטית בראש הרשימה ⭐",
                                            "Best & top-ranked models appear automatically at the top ⭐"
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
                                        placeholder = { Text(AppStrings.tr("חפש מודל (שם, תפקיד, תמונה, free)...", "Search model (name, role, vision, free)..."), fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotBlank()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = AppStrings.tr("נקה", "Clear"), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = AppStrings.tr(
                                                "זמינים: ${filteredModels.size} מודלים (ממוינים לפי איכות)",
                                                "Available: ${filteredModels.size} models (sorted by quality)"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (selectedProvider == ProviderType.OPENROUTER || modelsList.any { it.isFree }) {
                                            FilterChip(
                                                selected = showOnlyFreeModels,
                                                onClick = { showOnlyFreeModels = !showOnlyFreeModels },
                                                label = { Text(AppStrings.tr("חינמיים בלבד", "Free Only"), fontSize = 10.sp) },
                                                leadingIcon = if (showOnlyFreeModels) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(11.dp)) }
                                                } else null
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                    ) {
                                        if (filteredModels.isEmpty()) {
                                            Text(
                                                text = AppStrings.tr("לא נמצאו מודלים העונים לחיפוש.", "No models match your search."),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                filteredModels.forEach { modelInfo ->
                                                    val isSelected = currentModelId == modelInfo.id
                                                    val isModelFree = modelInfo.isFree || modelInfo.id.contains(":free", ignoreCase = true)
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
                                    "אם מודל מחזיר שגיאה, המערכת עוברת אוטומטית למודל הבא אחריו.",
                                    "If a model returns an error, the system automatically falls back to the next one."
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
                        if (existingKey == null && apiKeyText.isBlank()) {
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
                            customProtocol
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
