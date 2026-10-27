package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NotifAiApplication
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FetchModelsResponse
import com.example.data.model.ProviderType
import com.example.data.repository.QueryResult
import com.example.service.NotificationChatService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotifAiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as NotifAiApplication).aiRepository
    private val prefs = application.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)

    val apiKeys: StateFlow<List<ApiKeyEntity>> = repository.allKeysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ChatConversationEntity>> = repository.allConversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isServiceActive = MutableStateFlow(prefs.getBoolean("service_enabled", true))
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _selectedConversationId = MutableStateFlow<Long?>(null)
    val selectedConversationId: StateFlow<Long?> = _selectedConversationId.asStateFlow()

    private val _messagesForSelected = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messagesForSelected: StateFlow<List<ChatMessageEntity>> = _messagesForSelected.asStateFlow()

    private val _selectedAttachmentUri = MutableStateFlow<Uri?>(null)
    val selectedAttachmentUri: StateFlow<Uri?> = _selectedAttachmentUri.asStateFlow()

    private val _isQueryProcessing = MutableStateFlow(false)
    val isQueryProcessing: StateFlow<Boolean> = _isQueryProcessing.asStateFlow()

    private val _queryError = MutableStateFlow<String?>(null)
    val queryError: StateFlow<String?> = _queryError.asStateFlow()

    fun toggleService(enable: Boolean) {
        _isServiceActive.value = enable
        prefs.edit().putBoolean("service_enabled", enable).apply()
        if (enable) {
            NotificationChatService.startService(getApplication())
        } else {
            NotificationChatService.stopService(getApplication())
        }
    }

    fun syncServiceActiveState() {
        _isServiceActive.value = NotificationChatService.isRunning
    }

    fun selectConversation(id: Long?) {
        _selectedConversationId.value = id
        if (id != null) {
            viewModelScope.launch {
                repository.getMessagesForConversationFlow(id).collect { messages ->
                    _messagesForSelected.value = messages
                }
            }
        } else {
            _messagesForSelected.value = emptyList()
        }
    }

    fun setAttachmentUri(uri: Uri?) {
        _selectedAttachmentUri.value = uri
    }

    fun sendTestQuery(queryText: String) {
        if (queryText.isBlank() && _selectedAttachmentUri.value == null) return
        val attach = _selectedAttachmentUri.value
        _selectedAttachmentUri.value = null
        NotificationChatService.sendQueryToService(getApplication(), queryText, attach)
    }

    fun sendScreenshot(activity: android.app.Activity, customPrompt: String = "אנא נתח והסבר את מה שמופיע בצילום המסך") {
        com.example.util.ScreenshotHelper.captureActivityScreenshot(activity) { attachment, uri ->
            _selectedAttachmentUri.value = uri
            NotificationChatService.sendQueryToService(activity, customPrompt, uri)
            android.widget.Toast.makeText(activity, "צילום המסך נשלח בהצלחה ל-AI! 📸", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            repository.createNewConversation()
            _selectedConversationId.value = null
            _selectedAttachmentUri.value = null
            val serviceIntent = android.content.Intent(getApplication(), NotificationChatService::class.java).apply {
                action = NotificationChatService.ACTION_CLEAR_CHAT
            }
            getApplication<Application>().startService(serviceIntent)
        }
    }

    fun sendRecallPastChatsQuery() {
        val prompt = "זוכר מה דיברנו בשיחות הקודמות? אנא תן לי סיכום קצר של מה ששוחחנו עליו בשיחות הקודמות"
        NotificationChatService.sendQueryToService(getApplication(), prompt, null)
    }

    fun executeDirectQuery(prompt: String) {
        if (prompt.isBlank() && _selectedAttachmentUri.value == null) return
        val uri = _selectedAttachmentUri.value
        _selectedAttachmentUri.value = null
        _isQueryProcessing.value = true
        _queryError.value = null

        viewModelScope.launch {
            try {
                repository.askAi(
                    prompt = prompt.ifBlank { "אנא נתח את הקובץ המצורף" },
                    attachmentUri = uri,
                    conversationId = _selectedConversationId.value
                )
            } catch (e: Exception) {
                _queryError.value = e.message ?: "שגיאה בביצוע השאילתה"
            } finally {
                _isQueryProcessing.value = false
            }
        }
    }

    fun addOrUpdateKey(
        id: String? = null,
        provider: ProviderType,
        name: String,
        rawKey: String,
        model: String,
        priority: Int,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        systemPrompt: String? = null
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
                systemPrompt = systemPrompt
            )
        }
    }

    suspend fun fetchAvailableModels(provider: ProviderType, apiKey: String): List<String> {
        return repository.fetchAvailableModels(provider, apiKey)
    }

    suspend fun fetchAvailableModelsDetailed(provider: ProviderType, apiKey: String): FetchModelsResponse {
        return repository.fetchAvailableModelsDetailed(provider, apiKey)
    }

    suspend fun fetchModelsForKeyId(keyId: String): FetchModelsResponse {
        return repository.fetchModelsForKeyId(keyId)
    }

    fun movePriorityUp(key: ApiKeyEntity) {
        val currentKeys = apiKeys.value.sortedBy { it.priority }
        val index = currentKeys.indexOfFirst { it.id == key.id }
        if (index > 0) {
            val previousKey = currentKeys[index - 1]
            viewModelScope.launch {
                repository.swapPriorities(key, previousKey)
            }
        }
    }

    fun movePriorityDown(key: ApiKeyEntity) {
        val currentKeys = apiKeys.value.sortedBy { it.priority }
        val index = currentKeys.indexOfFirst { it.id == key.id }
        if (index >= 0 && index < currentKeys.size - 1) {
            val nextKey = currentKeys[index + 1]
            viewModelScope.launch {
                repository.swapPriorities(key, nextKey)
            }
        }
    }

    fun toggleKeyEnabled(key: ApiKeyEntity) {
        viewModelScope.launch {
            repository.updateKeyEntity(key.copy(isEnabled = !key.isEnabled))
        }
    }

    fun clearKeySuspension(keyId: String) {
        viewModelScope.launch {
            repository.clearKeySuspension(keyId)
        }
    }

    fun deleteKey(key: ApiKeyEntity) {
        viewModelScope.launch {
            repository.deleteKey(key)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_selectedConversationId.value == id) {
                _selectedConversationId.value = null
            }
            repository.deleteConversation(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            _selectedConversationId.value = null
            repository.clearAllHistory()
        }
    }

    suspend fun getDecryptedKey(id: String): String {
        return repository.getDecryptedKey(id)
    }

    fun dismissError() {
        _queryError.value = null
    }
}
