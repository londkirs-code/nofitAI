package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.repository.AiRepository
import com.example.ui.dialogs.AddKeyDialog
import com.example.ui.viewmodel.NotifAiViewModel
import com.example.util.AppStrings

@Composable
fun ApiKeysScreen(
    viewModel: NotifAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiKeys by viewModel.apiKeys.collectAsState()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var keyToEdit by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var showTaskRoutingSection by remember { mutableStateOf(false) }
    var routingRefreshTick by remember { mutableStateOf(0) }

    val sortedKeys = remember(apiKeys) { apiKeys.sortedBy { it.priority } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top header with Add button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppStrings.tr("סדרי עדיפויות, משימות ומפתחות"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.tr(
                        "${apiKeys.size} מפתחות מוגדרים • תמיכה בשכפול מהיר, גיבוי מודלים והתאמה אישית",
                        "${apiKeys.size} keys configured • Quick duplicate, fallback models & custom routing"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    keyToEdit = null
                    showAddKeyDialog = true
                },
                modifier = Modifier.testTag("add_key_fab_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.tr("הוסף מפתח"), fontWeight = FontWeight.Bold)
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
                                text = AppStrings.tr("ניתוב מפתח ומודל לפי סוג בקשה"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppStrings.tr("בחר מפתח ייעודי לכל סוג משימה (או אוטומטי לפי עדיפות):"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { showTaskRoutingSection = !showTaskRoutingSection },
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
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
                                            text = AppStrings.tr("אוטומטי (לפי עדיפות)"),
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
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(sortedKeys, key = { _, item -> item.id }) { index, key ->
                    KeyPriorityCard(
                        keyEntity = key,
                        isFirst = index == 0,
                        isLast = index == sortedKeys.size - 1,
                        onMoveUp = { viewModel.movePriorityUp(index) },
                        onMoveDown = { viewModel.movePriorityDown(index) },
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
    }

    if (showAddKeyDialog) {
        AddKeyDialog(
            existingKey = keyToEdit,
            nextPriority = (sortedKeys.maxOfOrNull { it.priority } ?: 0) + 1,
            onFetchDetailedModels = { provider, rawKey ->
                viewModel.fetchDetailedModels(provider, rawKey)
            },
            onFetchDetailedModelsForExistingKey = { keyId ->
                viewModel.fetchDetailedModelsForExistingKey(keyId)
            },
            onDismiss = {
                showAddKeyDialog = false
                keyToEdit = null
            },
            onSave = { provider, name, rawKey, model, priority, temperature, maxTokens, systemPrompt, supportedTasks, fallbackModels, customBaseUrl, customProtocol ->
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
                    customProtocol = customProtocol
                )
                showAddKeyDialog = false
                keyToEdit = null
            }
        )
    }
}

@Composable
fun KeyPriorityCard(
    keyEntity: ApiKeyEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Priority Badge, Name, Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                                text = "גיבוי אוטומטי במפתח: ${candidateModels.drop(1).joinToString(" ← ")}",
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
                            text = "${task.emoji} ${task.titleHe}",
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
                                contentDescription = "אפס השהייה",
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = AppStrings.tr("שכפל מפתח"),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.tr("שכפל מפתח"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Reordering priority buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFirst) {
                        AppStrings.tr("קדימות ראשית במענה (#1)", "Primary Priority (#1)")
                    } else {
                        AppStrings.tr("גיבוי בעדיפות #${keyEntity.priority}", "Fallback Priority #${keyEntity.priority}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = AppStrings.tr("העלה"), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.tr("העלה"), fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = AppStrings.tr("הורד"), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.tr("הורד"), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyKeysPlaceholder(onAddClick: () -> Unit) {
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
                text = AppStrings.tr("טרם הוגדרו מפתחות API"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = AppStrings.tr(
                    he = "הוסף מפתח אחד או יותר מכל ספק (Gemini, Grok, Claude, OpenAI, OpenRouter, Hugging Face או שרת מותאם אישית). תוכל לשכפל מפתח בלחיצה, להגדיר מודלים שונים לטקסט, תמונה, סאונד, וידאו או קבצים, ולקבוע סדר גיבוי אוטומטי.",
                    en = "Add one or more API keys from any provider (Gemini, Grok, Claude, OpenAI, OpenRouter, Hugging Face, or a Custom API). Duplicate keys in 1 tap, assign models for text, images, audio, video, or files, and set automatic fallback priority."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.tr("הוסף מפתח ראשון עכשיו"), fontWeight = FontWeight.Bold)
            }
        }
    }
}
