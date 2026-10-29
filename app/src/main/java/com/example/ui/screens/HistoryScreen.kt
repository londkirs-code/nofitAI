package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import com.example.ui.viewmodel.NotifAiViewModel
import com.example.util.AppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: NotifAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLang = AppStrings.langOverrideState
    val conversations by viewModel.conversations.collectAsState()
    val selectedId by viewModel.selectedConversationId.collectAsState()
    val messages by viewModel.messagesForSelected.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.lastSnippet.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedId != null) {
            ConversationDetailView(
                messages = messages,
                onBack = { viewModel.selectConversation(null) },
                onDelete = { viewModel.deleteConversation(selectedId!!) }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = AppStrings.tr("היסטוריית שיחות", "Chat History"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.tr(
                            "${conversations.size} שיחות שמורות באפליקציה",
                            "${conversations.size} conversations saved in app"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (conversations.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.testTag("clear_all_history_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = AppStrings.tr("מחק הכל", "Clear All"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (conversations.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(AppStrings.tr("חיפוש בהיסטוריית השיחות...", "Search chat history...")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            if (filteredConversations.isEmpty()) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_custom_asset),
                            contentDescription = "NotifAI",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) {
                                AppStrings.tr("אין היסטוריית שיחות עדיין", "No chat history yet")
                            } else {
                                AppStrings.tr("לא נמצאו שיחות תואמות", "No matching conversations found")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.tr(
                                "כל שאלה שתענה עליה מתוך ההתראה תישמר כאן אוטומטית לעיון, חיפוש והעתקה בכל עת.",
                                "Every question answered from the notification is saved here automatically for browsing, searching, and copying."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredConversations, key = { it.id }) { conv ->
                        ConversationCard(
                            conversation = conv,
                            onClick = { viewModel.selectConversation(conv.id) },
                            onDelete = { viewModel.deleteConversation(conv.id) }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    AppStrings.tr("מחיקת כל ההיסטוריה", "Clear All History"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    AppStrings.tr(
                        "האם אתה בטוח שברצונך למחוק את כל שיחות ה-AI שנשמרו באפליקציה? פעולה זו היא בלתי הפיכה.",
                        "Are you sure you want to delete all saved AI conversations? This action cannot be undone."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmDialog = false
                        Toast.makeText(
                            context,
                            AppStrings.tr("ההיסטוריה נמחקה", "History cleared"),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(AppStrings.tr("מחק הכל", "Delete All"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(AppStrings.tr("ביטול", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun ConversationCard(
    conversation: ChatConversationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_custom_asset),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.tr("${conversation.messageCount} הודעות", "${conversation.messageCount} messages"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = conversation.lastSnippet.ifBlank { AppStrings.tr("שיחת AI", "AI Conversation") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = AppStrings.tr("מחק שיחה", "Delete conversation"),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ConversationDetailView(
    messages: List<ChatMessageEntity>,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.tr("חזור לרשימה", "Back to list")
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.tr("תוכן השיחה", "Conversation Messages"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = AppStrings.tr("מחק שיחה זו", "Delete this conversation"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    AppStrings.tr("טוען הודעות...", "Loading messages..."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessageEntity) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val isError = message.role == "error"

    val bubbleColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val textColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val copyAction = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", message.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            AppStrings.tr("הועתק ללוח! 📋", "Copied to clipboard! 📋"),
            Toast.LENGTH_SHORT
        ).show()
    }

    val shareAction = {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message.content.ifBlank { message.attachmentUri.orEmpty() })
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                AppStrings.tr("שתף הודעה", "Share message")
            )
        )
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        @OptIn(ExperimentalFoundationApi::class)
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .combinedClickable(
                    onClick = { copyAction() },
                    onLongClick = { shareAction() }
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isUser) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_custom_asset),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isUser) AppStrings.tr("אתה", "You") else (message.model ?: message.provider ?: "NotifAI"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Message", message.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(
                                    context,
                                    AppStrings.tr("הועתק ללוח! 📋", "Copied to clipboard! 📋"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = AppStrings.tr("העתק", "Copy"),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        AppStrings.tr("שתף הודעה", "Share message")
                                    )
                                )
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = AppStrings.tr("שתף", "Share"),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (!message.attachmentUri.isNullOrBlank() || !message.attachmentName.isNullOrBlank()) {
                    com.example.ui.components.MessageAttachmentView(
                        attachmentUri = message.attachmentUri,
                        attachmentMimeType = message.attachmentMimeType,
                        attachmentName = message.attachmentName,
                        isUser = isUser
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
