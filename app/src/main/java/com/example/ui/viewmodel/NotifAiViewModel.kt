package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NotifAiApplication
import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FetchModelsResponse
import com.example.data.model.ProviderType
import com.example.data.repository.AiRepository
import com.example.service.NotificationChatService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotifAiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AiRepository = (application as NotifAiApplication).aiRepository

    val apiKeys: StateFlow<List<ApiKeyEntity>> = repository.allKeysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ChatConversationEntity>> = repository.allConversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedConversationId = MutableStateFlow<Long?>(null)
    val selectedConversationId: StateFlow<Long?> = _selectedConversationId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedConversationMessages: StateFlow<List<ChatMessageEntity>> = _selectedConversationId
        .flatMapLatest { convId ->
            if (convId != null) {
                repository.getMessagesForConversationFlow(convId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messagesForSelected: StateFlow<List<ChatMessageEntity>> = selectedConversationMessages

    private val _isServiceActive = MutableStateFlow(
        NotificationChatService.isRunning ||
            application.getSharedPreferences("notifai_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("service_enabled", true)
    )
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _selectedAttachmentUri = MutableStateFlow<Uri?>(null)
    val selectedAttachmentUri: StateFlow<Uri?> = _selectedAttachmentUri.asStateFlow()

    fun selectConversation(id: Long?) {
        _selectedConversationId.value = id
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val conv = repository.createNewConversation()
            _selectedConversationId.value = conv.id
            NotificationChatService.clearAllResponses(getApplication())
        }
    }

    fun setAttachmentUri(uri: Uri?) {
        _selectedAttachmentUri.value = uri
    }

    fun toggleService(enable: Boolean) {
        val context = getApplication<Application>()
        if (enable) {
            NotificationChatService.startService(context)
            _isServiceActive.value = true
        } else {
            NotificationChatService.stopService(context)
            _isServiceActive.value = false
        }
    }

    fun syncServiceActiveState() {
        val prefs = getApplication<Application>().getSharedPreferences("notifai_prefs", android.content.Context.MODE_PRIVATE)
        val shouldBeRunning = prefs.getBoolean("service_enabled", true)
        if (shouldBeRunning && !NotificationChatService.isRunning) {
            NotificationChatService.startService(getApplication())
        }
        _isServiceActive.value = NotificationChatService.isRunning || shouldBeRunning
    }

    init {
        viewModelScope.launch {
            try {
                repository.ensureBuiltInPollinationsKey()
            } catch (_: Exception) {}
        }
    }

    fun addOrUpdateApiKey(
        id: String? = null,
        provider: ProviderType,
        name: String,
        rawKey: String,
        model: String,
        priority: Int,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String?,
        supportedTasks: String = AiTaskType.DEFAULT_TASKS_CSV,
        fallbackModels: String = "",
        customBaseUrl: String? = null,
        customProtocol: String = "OPENAI",
        rememberOnDevice: Boolean = true,
        isEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            repository.saveApiKey(
                id = id,
                provider = provider,
                name = name,
                plainKey = rawKey,
                model = model,
                priority = priority,
                temperature = temperature,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
                supportedTasks = supportedTasks,
                fallbackModels = fallbackModels,
                customBaseUrl = customBaseUrl,
                customProtocol = customProtocol,
                rememberOnDevice = rememberOnDevice,
                isEnabled = isEnabled
            )
            val msg = if (rememberOnDevice) {
                "המפתח נשמר והוצפן בחומרה בהצלחה! 🔒"
            } else {
                "המפתח הוגדר לסשן הנוכחי בלבד (ללא שמירה במכשיר)! 🛡️"
            }
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun duplicateKey(keyEntity: ApiKeyEntity) {
        viewModelScope.launch {
            val copy = repository.duplicateKey(keyEntity.id)
            if (copy != null) {
                Toast.makeText(
                    getApplication(),
                    "המפתח שוכפל כ-'${copy.name}' עם מודל ${copy.selectedModel} בעדיפות #${copy.priority}!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    suspend fun getDecryptedKey(id: String): String {
        return repository.getDecryptedKey(id)
    }

    suspend fun fetchModelsFromProvider(provider: ProviderType, apiKey: String): List<String> {
        _isFetchingModels.value = true
        return try {
            repository.fetchAvailableModels(provider, apiKey)
        } finally {
            _isFetchingModels.value = false
        }
    }

    suspend fun fetchDetailedModels(
        provider: ProviderType,
        apiKey: String,
        customBaseUrl: String? = null,
        customProtocol: String = "OPENAI"
    ): FetchModelsResponse {
        _isFetchingModels.value = true
        return try {
            repository.fetchAvailableModelsDetailed(provider, apiKey, customBaseUrl, customProtocol)
        } finally {
            _isFetchingModels.value = false
        }
    }

    suspend fun fetchDetailedModelsForExistingKey(keyId: String): FetchModelsResponse {
        _isFetchingModels.value = true
        return try {
            repository.fetchModelsForKeyId(keyId)
        } finally {
            _isFetchingModels.value = false
        }
    }

    fun toggleKeyEnabled(entity: ApiKeyEntity) {
        viewModelScope.launch {
            repository.updateKeyEntity(entity.copy(isEnabled = !entity.isEnabled))
        }
    }

    fun setAllKeysEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAllKeysEnabled(enabled)
            Toast.makeText(
                getApplication(),
                if (enabled) com.example.util.AppStrings.tr("כל המפתחות הופעלו בהצלחה ✅", "All API keys enabled ✅")
                else com.example.util.AppStrings.tr("כל המפתחות כובו ⏸️", "All API keys disabled ⏸️"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deleteKey(entity: ApiKeyEntity) {
        viewModelScope.launch {
            repository.deleteKey(entity)
        }
    }

    fun deleteAllKeys() {
        viewModelScope.launch {
            repository.deleteAllKeys()
            Toast.makeText(
                getApplication(),
                com.example.util.AppStrings.tr("כל המפתחות השמורים נמחקו בהצלחה 🗑️", "All saved API keys deleted 🗑️"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun reorderKeys(orderedIds: List<String>) {
        viewModelScope.launch {
            repository.reorderKeys(orderedIds)
        }
    }

    fun importParsedKeys(items: List<com.example.util.ParsedImportKeyItem>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = repository.importParsedKeys(items)
            Toast.makeText(
                getApplication(),
                com.example.util.AppStrings.tr(
                    "יובאו ונשמרו בהצלחה $count מפתחות מוצפנים! 🔒",
                    "Successfully imported and encrypted $count API keys! 🔒"
                ),
                Toast.LENGTH_LONG
            ).show()
            onComplete(count)
        }
    }

    suspend fun exportKeysAsJson(): String {
        val pairs = repository.exportAllKeysWithPlain()
        return com.example.util.SmartKeyImporter.exportKeysToJson(pairs)
    }

    suspend fun exportKeysAsTxt(): String {
        val pairs = repository.exportAllKeysWithPlain()
        return com.example.util.SmartKeyImporter.exportKeysToTxt(pairs)
    }

    fun clearSuspension(entity: ApiKeyEntity) {
        viewModelScope.launch {
            repository.clearKeySuspension(entity.id)
            Toast.makeText(getApplication(), "חסימת המפתח והמודלים אופסה", Toast.LENGTH_SHORT).show()
        }
    }

    fun movePriorityUp(index: Int) {
        val currentList = apiKeys.value
        if (index > 0 && index < currentList.size) {
            val item = currentList[index]
            val prev = currentList[index - 1]
            viewModelScope.launch {
                repository.swapPriorities(item, prev)
            }
        }
    }

    fun movePriorityDown(index: Int) {
        val currentList = apiKeys.value
        if (index >= 0 && index < currentList.size - 1) {
            val item = currentList[index]
            val next = currentList[index + 1]
            viewModelScope.launch {
                repository.swapPriorities(item, next)
            }
        }
    }

    fun sendTestQuery(prompt: String) {
        if (prompt.isBlank() && _selectedAttachmentUri.value == null) return
        val context = getApplication<Application>()
        val uri = _selectedAttachmentUri.value
        _selectedAttachmentUri.value = null
        NotificationChatService.sendQueryToService(
            context = context,
            query = prompt.ifBlank { "אנא נתח את הקובץ המצורף" },
            attachmentUri = uri
        )
        Toast.makeText(context, "השאלה נשלחה למענה בהתראה...", Toast.LENGTH_SHORT).show()
    }

    fun sendRecallPastChatsQuery() {
        val context = getApplication<Application>()
        NotificationChatService.sendQueryToService(
            context = context,
            query = "אתה זוכר על מה דיברנו בשיחות הקודמות? תן לי סיכום קצר."
        )
        Toast.makeText(context, "בודק היסטוריית שיחות...", Toast.LENGTH_SHORT).show()
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_selectedConversationId.value == id) {
                _selectedConversationId.value = null
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _selectedConversationId.value = null
            Toast.makeText(getApplication(), "היסטוריית השיחות נמחקה", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllHistory() = clearHistory()
}
