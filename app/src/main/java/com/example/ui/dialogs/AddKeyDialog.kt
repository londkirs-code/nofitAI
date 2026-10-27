package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiModelInfo
import com.example.data.model.ApiKeyEntity
import com.example.data.model.FetchModelsResponse
import com.example.data.model.ProviderType
import kotlinx.coroutines.Job
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
        systemPrompt: String?
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
    // Never store or display plaintext existing key. Remains empty unless user enters new key to overwrite!
    var apiKeyText by remember { mutableStateOf("") }

    // Models state
    var modelsList by remember(selectedProvider) {
        val initial = selectedProvider.suggestedModels.map { modelId ->
            AiModelInfo(
                id = modelId,
                displayName = modelId,
                isFree = selectedProvider == ProviderType.GROQ || modelId.contains("free", ignoreCase = true),
                isRecommended = true
            )
        }.toMutableList()

        if (existingKey != null && existingKey.provider == selectedProvider && initial.none { it.id == existingKey.selectedModel }) {
            initial.add(0, AiModelInfo(id = existingKey.selectedModel, displayName = existingKey.selectedModel, isRecommended = false))
        }
        mutableStateOf(initial.toList())
    }

    var selectedModel by remember {
        mutableStateOf(existingKey?.selectedModel ?: selectedProvider.defaultModel)
    }
    var customModelInput by remember { mutableStateOf(existingKey?.selectedModel ?: selectedProvider.defaultModel) }

    // Dynamic fetch status
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchStatusMessage by remember { mutableStateOf<String?>(null) }
    var fetchStatusIsError by remember { mutableStateOf(false) }
    var fetchSuccessCount by remember { mutableStateOf<Int?>(null) }

    // Filter state
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
        fetchStatusMessage = "שולף מודלים מאומתים משרתי ${provider.displayName}..."
        fetchStatusIsError = false
        fetchSuccessCount = null

        scope.launch {
            val result = onFetchDetailedModels(provider, key)
            isFetchingModels = false
            if (result.isSuccess && result.models.isNotEmpty()) {
                modelsList = result.models
                fetchSuccessCount = result.models.size
                fetchStatusMessage = "נמצאו ${result.models.size} מודלים זמינים ומאומתים מול ה-API!"
                fetchStatusIsError = false

                // If currently selected model is not in the live list, select the top recommended one
                if (result.models.none { it.id == selectedModel }) {
                    val best = result.models.firstOrNull { it.isRecommended } ?: result.models.first()
                    selectedModel = best.id
                    customModelInput = best.id
                }
            } else {
                fetchStatusIsError = true
                fetchStatusMessage = result.errorMessage ?: "שגיאה בשליפת המודלים. מוצגים מודלים מוצעים."
                if (result.models.isNotEmpty()) {
                    modelsList = result.models
                }
            }
        }
    }

    // Auto-fetch for existing key when dialog opens (without exposing raw key to UI)
    LaunchedEffect(existingKey?.id) {
        if (existingKey != null && onFetchDetailedModelsForExistingKey != null) {
            isFetchingModels = true
            fetchStatusMessage = "שולף אוטומטית מודלים מאומתים משרתי ${selectedProvider.displayName}..."
            fetchStatusIsError = false
            fetchSuccessCount = null
            scope.launch {
                val result = onFetchDetailedModelsForExistingKey(existingKey.id)
                isFetchingModels = false
                if (result.isSuccess && result.models.isNotEmpty()) {
                    modelsList = result.models
                    fetchSuccessCount = result.models.size
                    fetchStatusMessage = "נמצאו ${result.models.size} מודלים זמינים ומאומתים מול ה-API!"
                    fetchStatusIsError = false
                    if (result.models.none { it.id == selectedModel }) {
                        val best = result.models.firstOrNull { it.isRecommended } ?: result.models.first()
                        selectedModel = best.id
                        customModelInput = best.id
                    }
                }
            }
        }
    }

    // Auto-fetch models automatically whenever API key is typed or pasted (debounced)
    LaunchedEffect(apiKeyText, selectedProvider) {
        if (apiKeyText.isNotBlank() && apiKeyText.length >= 8) {
            delay(500)
            triggerLiveFetch(selectedProvider, apiKeyText)
        }
    }

    // Filter displayed models
    val filteredModels = remember(modelsList, searchQuery, showOnlyFreeModels, selectedProvider) {
        modelsList.filter { model ->
            val matchesSearch = searchQuery.isBlank() ||
                model.id.contains(searchQuery, ignoreCase = true) ||
                model.displayName.contains(searchQuery, ignoreCase = true)
            val matchesFree = !showOnlyFreeModels || model.isFree
            matchesSearch && matchesFree
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                            text = if (existingKey == null) "הוספת מפתח API ו-Playground" else "עריכת מפתח ו-Playground",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "זיהוי ואימות מודלים דינמי בזמן אמת",
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
                                text = "המפתח מוצפן בחומרת המכשיר (AES-256 GCM). המערכת שואלת את ה-API אילו מודלים פעילים עבורך.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Provider Selection Dropdown
                    Text(
                        text = "1. בחר ספק AI / שירות:",
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
                                                ProviderType.GROQ -> "מודלי קוד פתוח במהירות קיצונית (Llama, Gemma)"
                                                ProviderType.OPENROUTER -> "גישה מאוחדת למאות מודלים מובילים"
                                                ProviderType.GEMINI -> "דגמי Gemini 2.5 / 1.5 של Google"
                                                ProviderType.OPENAI -> "GPT-4o, GPT-4o-mini ודגמי o1/o3 Reasoning"
                                                ProviderType.CLAUDE -> "Claude 3.7 Sonnet, Claude 3.5 Haiku"
                                                ProviderType.DEEPSEEK -> "DeepSeek-V3 ו-DeepSeek-R1"
                                                ProviderType.MISTRAL -> "Mistral Large, Codestral ו-Pixtral"
                                                ProviderType.GROK -> "xAI Grok 2 ו-Grok Beta"
                                                ProviderType.HUGGINGFACE -> "HuggingFace Serverless Inference"
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
                                                    text = provider.keyHint,
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
                                        modelsList = provider.suggestedModels.map {
                                            AiModelInfo(
                                                id = it,
                                                displayName = it,
                                                isFree = provider == ProviderType.GROQ || it.contains("free", ignoreCase = true),
                                                isRecommended = true
                                            )
                                        }
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

                    // Key Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("כינוי למפתח") },
                        placeholder = { Text("למשל: Groq מהיר או Gemini ראשי") },
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
                                if (existingKey == null) "מפתח API (${selectedProvider.keyHint})"
                                else "מפתח API (שמור ומוצפן בחומרה 🔒)"
                            )
                        },
                        placeholder = {
                            Text(
                                if (existingKey == null) selectedProvider.keyHint
                                else "•••••••••••••••••••• (שמור - הזן חדש לדריסה בלבד)"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "המפתח חסוי ומוצפן תמיד",
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
                                    "המפתח שמור ומוצפן. השאר ריק לשמירתו, או הקלד מפתח חדש לדריסה.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                Text(
                                    "המפתח יוצפן בחומרה ולא יוצג בגלוי לעולם • קישור: ${selectedProvider.documentationUrl}",
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
                                        text = fetchStatusMessage ?: "לחץ לבדיקת המפתח ושליפת מודלים עדכניים מהשרת",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (fetchSuccessCount != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (fetchStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (apiKeyText.isBlank()) {
                                            errorMessage = "הזן תחילה מפתח API כדי לשלוף מודלים מהשרת"
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
                                    Text("שלוף מ-API", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Model Selection Section (Clean, compact, directional menu)
                    val currentModelId = customModelInput.ifBlank { selectedModel.ifBlank { selectedProvider.defaultModel } }
                    val currentModelInfo = modelsList.find { it.id == currentModelId } ?: AiModelInfo(id = currentModelId)

                    Text(
                        text = "2. מודל נבחר לתקשורת:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
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
                                                text = if (isModelFree) "🟢 חינמי" else "💳 בתשלום",
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
                                Text("החלף", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                        text = "בחירת מודל (${selectedProvider.displayName})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "תפריט מודלים מסודר ומיושר לפי תפקיד ועלות",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Search bar
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("חפש מודל (שם, תפקיד, free)...", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotBlank()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "נקה", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    )

                                    // Filter Toggle Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "זמינים: ${filteredModels.size} מודלים",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (selectedProvider == ProviderType.OPENROUTER || modelsList.any { it.isFree }) {
                                            FilterChip(
                                                selected = showOnlyFreeModels,
                                                onClick = { showOnlyFreeModels = !showOnlyFreeModels },
                                                label = { Text("חינמיים בלבד", fontSize = 10.sp) },
                                                leadingIcon = if (showOnlyFreeModels) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(11.dp)) }
                                                } else null
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Scrollable single-direction list of models
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                    ) {
                                        if (filteredModels.isEmpty()) {
                                            Text(
                                                text = "לא נמצאו מודלים העונים לחיפוש.",
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
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                selectedModel = modelInfo.id
                                                                customModelInput = modelInfo.id
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
                                                                            contentDescription = "מומלץ",
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
                                                                        text = if (isModelFree) "חינמי 🟢" else "בתשלום 💳",
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

                                    // Custom Model Input field
                                    OutlinedTextField(
                                        value = customModelInput,
                                        onValueChange = {
                                            customModelInput = it.trim()
                                            selectedModel = it.trim()
                                        },
                                        label = { Text("או הזן שם מודל מותאם ידנית", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_model_input")
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showModelSelectionModal = false }) {
                                    Text("אישור")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showModelSelectionModal = false }) {
                                    Text("סגור")
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
                            text = if (showPlaygroundAdvanced) "הסתר פרמטרים של Playground" else "הגדרות מתקדמות (טמפרטורה, System Prompt, טוקנים)",
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
                                // Temperature Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "טמפרטורה (Temperature):",
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

                                // Max Tokens
                                OutlinedTextField(
                                    value = maxTokensText,
                                    onValueChange = { if (it.all { ch -> ch.isDigit() }) maxTokensText = it },
                                    label = { Text("מקסימום טוקנים לתשובה (Max Tokens)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Custom System Prompt
                                OutlinedTextField(
                                    value = systemPrompt,
                                    onValueChange = { systemPrompt = it },
                                    label = { Text("הנחיית מערכת (System Prompt מותאם)") },
                                    placeholder = { Text("ענה תמיד בעברית תמציתית ומדויקת...") },
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
                                text = "סדר עדיפות בניתוב (1 = הכי גבוה):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "במקרה של עומס או חריגת מכסה, המערכת תדלג למפתח הבא.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (priority > 1) priority-- },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "הורד עדיפות")
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
                                Icon(Icons.Default.Add, contentDescription = "העלה עדיפות")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (existingKey == null && apiKeyText.isBlank()) {
                            errorMessage = "נא להזין מפתח API תקין"
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
                            systemPrompt.trim().takeIf { it.isNotBlank() }
                        )
                    },
                    modifier = Modifier.testTag("save_api_key_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("שמור מפתח והגדרות", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("ביטול")
                }
            }
        )
    }
}
