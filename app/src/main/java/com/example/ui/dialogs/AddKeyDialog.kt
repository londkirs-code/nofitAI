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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    initialDecryptedKey: String = "",
    nextPriority: Int = 1,
    onFetchDetailedModels: suspend (ProviderType, String) -> FetchModelsResponse,
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
    var name by remember {
        mutableStateOf(
            existingKey?.name ?: "${selectedProvider.displayName} #${nextPriority}"
        )
    }
    var apiKeyText by remember { mutableStateOf(initialDecryptedKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

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

    // Auto-fetch on initial load if key is present
    LaunchedEffect(Unit) {
        if (apiKeyText.isNotBlank()) {
            triggerLiveFetch(selectedProvider, apiKeyText)
        }
    }

    // Auto-fetch when provider changes if key is already filled
    LaunchedEffect(selectedProvider) {
        if (apiKeyText.isNotBlank()) {
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

                    // Provider Selection
                    Text(
                        text = "1. בחר ספק AI / Playground:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProviderType.values().forEach { provider ->
                            val isSelected = selectedProvider == provider
                            FilterChip(
                                selected = isSelected,
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
                                },
                                label = { Text(provider.displayName, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
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

                    // API Key input
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it.trim()
                            errorMessage = null
                        },
                        label = { Text("מפתח API (${selectedProvider.keyHint})") },
                        placeholder = { Text(selectedProvider.keyHint) },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "הסתר מפתח" else "הצג מפתח"
                                )
                            }
                        },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = {
                            if (errorMessage != null) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("קישור לספק: ${selectedProvider.documentationUrl}", fontSize = 11.sp)
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

                    // Model Selection Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. בחר מודל מתוך הרשימה הזמינה:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // OpenRouter or providers with free models toggle
                        if (selectedProvider == ProviderType.OPENROUTER || modelsList.any { it.isFree }) {
                            FilterChip(
                                selected = showOnlyFreeModels,
                                onClick = { showOnlyFreeModels = !showOnlyFreeModels },
                                label = { Text("חינמיים בלבד (Free)", fontSize = 11.sp) },
                                leadingIcon = if (showOnlyFreeModels) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null
                            )
                        }
                    }

                    // Search input if many models
                    if (modelsList.size > 8) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("חפש מודל ברשימה (למשל: llama, flash, sonnet)...", fontSize = 12.sp) },
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
                                .height(52.dp)
                        )
                    }

                    // Models chips
                    if (filteredModels.isEmpty()) {
                        Text(
                            text = "לא נמצאו מודלים העונים לסינון.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredModels.forEach { modelInfo ->
                                val isSelected = customModelInput == modelInfo.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedModel = modelInfo.id
                                        customModelInput = modelInfo.id
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(modelInfo.displayName, fontSize = 11.sp)
                                            if (modelInfo.isFree) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "חינמי",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else if (modelInfo.isRecommended) {
                                        { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Custom Model Name TextField (allows typing any Playground model)
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = {
                            customModelInput = it.trim()
                            selectedModel = it.trim()
                        },
                        label = { Text("שם מודל פעיל (נבחר מלמעלה או הזן ידנית)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_input")
                    )

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
                        if (apiKeyText.isBlank()) {
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
