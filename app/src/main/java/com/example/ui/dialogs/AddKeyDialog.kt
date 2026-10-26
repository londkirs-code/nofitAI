package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ProviderType
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddKeyDialog(
    existingKey: ApiKeyEntity? = null,
    initialDecryptedKey: String = "",
    nextPriority: Int = 1,
    onFetchLiveModels: suspend (ProviderType, String) -> List<String> = { p, _ -> p.suggestedModels },
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

    var availableModels by remember(selectedProvider) {
        val base = selectedProvider.suggestedModels.toMutableList()
        if (existingKey != null && existingKey.provider == selectedProvider && !base.contains(existingKey.selectedModel)) {
            base.add(0, existingKey.selectedModel)
        }
        mutableStateOf(base.toList())
    }
    var selectedModel by remember {
        mutableStateOf(existingKey?.selectedModel ?: selectedProvider.defaultModel)
    }
    var customModelInput by remember { mutableStateOf(existingKey?.selectedModel ?: selectedProvider.defaultModel) }
    var isFetchingModels by remember { mutableStateOf(false) }

    var priority by remember { mutableIntStateOf(existingKey?.priority ?: nextPriority) }
    var showPlaygroundAdvanced by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(existingKey?.temperature ?: 0.7f) }
    var maxTokensText by remember { mutableStateOf((existingKey?.maxTokens ?: 4096).toString()) }
    var systemPrompt by remember { mutableStateOf(existingKey?.systemPrompt.orEmpty()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                    Text(
                        text = if (existingKey == null) "הוספת מפתח API ו-Playground" else "עריכת מפתח ו-Playground",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                                text = "המפתח מוצפן בחומרת המכשיר (AES-256 GCM). כולל תמיכה מלאה ב-GroqCloud Playground ו-xAI Grok.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Provider Selection
                    Text(
                        text = "בחר ספק AI / Playground:",
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
                                    availableModels = provider.suggestedModels
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
                        placeholder = { Text("למשל: Groq Playground מהיר") },
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
                                Text("אתר הספק וה-Playground: ${selectedProvider.documentationUrl}", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    // Model Selection + Live Fetch from API
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "מודל פעיל:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = {
                                if (apiKeyText.isBlank()) {
                                    errorMessage = "הזן תחילה מפתח API כדי לשלוף את רשימת המודלים מהשרת"
                                    return@OutlinedButton
                                }
                                isFetchingModels = true
                                scope.launch {
                                    val fetched = onFetchLiveModels(selectedProvider, apiKeyText)
                                    isFetchingModels = false
                                    if (fetched.isNotEmpty()) {
                                        availableModels = fetched
                                        selectedModel = fetched.first()
                                        customModelInput = fetched.first()
                                        Toast.makeText(
                                            context,
                                            "נשלפו ${fetched.size} מודלים זמינים מ-${selectedProvider.displayName}!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isFetchingModels,
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("שלוף מודלים מה-API", fontSize = 11.sp)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableModels.forEach { modelName ->
                            val isSelected = customModelInput == modelName
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedModel = modelName
                                    customModelInput = modelName
                                },
                                label = { Text(modelName, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Custom Model Name TextField (allows typing any Playground model)
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = {
                            customModelInput = it.trim()
                            selectedModel = it.trim()
                        },
                        label = { Text("שם מודל (ניתן לבחור למעלה או להקליד כל מודל מה-Playground)") },
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
                            text = if (showPlaygroundAdvanced) "הסתר פרמטרים של Playground" else "הגדרות Playground מתקדמות (טמפרטורה, System Prompt, טוקנים)",
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
                                    placeholder = { Text("למשל: ענה תמיד בעברית תמציתית ומדויקת...") },
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
