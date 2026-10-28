package com.example.util

import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ProviderType
import org.json.JSONArray
import org.json.JSONObject

data class ParsedImportKeyItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawKey: String,
    val provider: ProviderType,
    val name: String,
    val preferredModel: String,
    val fallbackModels: String = "",
    val supportedTasks: String = AiTaskType.DEFAULT_TASKS_CSV,
    val customBaseUrl: String? = null,
    val customProtocol: String = "OPENAI",
    val wasModelAutoDetectedFromFile: Boolean = false
)

object SmartKeyImporter {

    private val keyRegex = Regex(
        """(AIza[0-9A-Za-z\-_]{25,60}|sk-or-v1-[0-9A-Za-z\-_]{30,120}|sk-or-[0-9A-Za-z\-_]{20,120}|gsk_[0-9A-Za-z\-_]{20,100}|sk-ant-[0-9A-Za-z\-_]{20,140}|xai-[0-9A-Za-z\-_]{20,120}|hf_[0-9A-Za-z]{20,100}|sk-proj-[0-9A-Za-z\-_]{20,180}|sk-[0-9A-Za-z\-_]{20,140})"""
    )

    private val modelTokenRegex = Regex(
        """([a-zA-Z0-9_\-]+/[a-zA-Z0-9._\-]+(?::free)?|(?:gemini|gpt|claude|llama|deepseek|grok|mistral|pixtral|codestral|qwen|qwq|gemma|dall-e|tts|o1|o3)[a-zA-Z0-9._\-:]{2,60})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses raw text from a .json file, .txt file, or clipboard paste into a list of [ParsedImportKeyItem].
     * Automatically detects the provider type for each key, and if a preferred model is specified in the file/text,
     * selects and marks it automatically.
     */
    fun parseKeysFromContent(rawContent: String): List<ParsedImportKeyItem> {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Try parsing as JSON Array or JSON Object first
        val jsonParsed = tryParseJson(trimmed)
        if (jsonParsed.isNotEmpty()) {
            return jsonParsed.distinctBy { "${it.rawKey}|${it.preferredModel}" }
        }

        // 2. Parse line-by-line or multi-key text blocks (.txt, .env, CSV, free-form paste)
        val results = mutableListOf<ParsedImportKeyItem>()
        val lines = trimmed.lines()

        var pendingContextHint = ""
        var pendingModelHint: String? = null

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isBlank() || cleanLine.startsWith("#") || cleanLine.startsWith("//")) {
                if (cleanLine.isNotBlank()) {
                    pendingContextHint = cleanLine
                    extractModelFromText(cleanLine, null)?.let { pendingModelHint = it }
                }
                continue
            }

            val matches = keyRegex.findAll(cleanLine).map { it.value.trim() }.toList()
            if (matches.isNotEmpty()) {
                for ((idx, rawKey) in matches.withIndex()) {
                    val textWithoutKey = cleanLine.replace(rawKey, " ")
                    val combinedHint = "$pendingContextHint $textWithoutKey"
                    val provider = ProviderType.detectProviderFromKey(rawKey, combinedHint) ?: ProviderType.OPENAI
                    val detectedModel = extractModelFromText(textWithoutKey, provider) ?: pendingModelHint
                    val chosenModel = detectedModel ?: provider.defaultModel
                    val customLabel = extractLabelFromLine(textWithoutKey, provider, results.size + idx + 1)

                    results.add(
                        ParsedImportKeyItem(
                            rawKey = rawKey,
                            provider = provider,
                            name = customLabel,
                            preferredModel = chosenModel,
                            wasModelAutoDetectedFromFile = detectedModel != null
                        )
                    )
                }
                pendingContextHint = ""
                pendingModelHint = null
            } else {
                // Check if line is a standalone 32+ char token (e.g. Mistral or custom key) or key=value
                val kvSplit = cleanLine.split("=", ":").map { it.trim().trim('"', '\'', ',', ';') }
                if (kvSplit.size == 2) {
                    val left = kvSplit[0]
                    val right = kvSplit[1]
                    if (left.contains("model", ignoreCase = true)) {
                        if (results.isNotEmpty() && !results.last().wasModelAutoDetectedFromFile && right.length >= 3) {
                            val last = results.removeAt(results.lastIndex)
                            results.add(last.copy(preferredModel = right, wasModelAutoDetectedFromFile = true))
                        } else {
                            pendingModelHint = right
                        }
                    } else if (right.length >= 20 && !right.contains(" ")) {
                        val provider = ProviderType.detectProviderFromKey(right, "$pendingContextHint $left") ?: ProviderType.CUSTOM
                        val detectedModel = pendingModelHint ?: extractModelFromText(left, provider)
                        results.add(
                            ParsedImportKeyItem(
                                rawKey = right,
                                provider = provider,
                                name = left.takeIf { it.isNotBlank() && !it.equals("key", true) && !it.equals("api_key", true) }
                                    ?: "${provider.displayName.substringBefore(" ")} #${results.size + 1}",
                                preferredModel = detectedModel ?: provider.defaultModel,
                                wasModelAutoDetectedFromFile = detectedModel != null
                            )
                        )
                        pendingModelHint = null
                    } else {
                        pendingContextHint = cleanLine
                        extractModelFromText(cleanLine, null)?.let { pendingModelHint = it }
                    }
                } else if (cleanLine.length in 24..120 && !cleanLine.contains(" ") && cleanLine.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                    val provider = ProviderType.detectProviderFromKey(cleanLine, pendingContextHint) ?: ProviderType.MISTRAL
                    results.add(
                        ParsedImportKeyItem(
                            rawKey = cleanLine,
                            provider = provider,
                            name = "${provider.displayName.substringBefore(" ")} #${results.size + 1}",
                            preferredModel = pendingModelHint ?: provider.defaultModel,
                            wasModelAutoDetectedFromFile = pendingModelHint != null
                        )
                    )
                    pendingModelHint = null
                } else {
                    pendingContextHint = cleanLine
                    extractModelFromText(cleanLine, null)?.let { pendingModelHint = it }
                }
            }
        }

        return results.distinctBy { "${it.rawKey}|${it.preferredModel}" }
    }

    private fun tryParseJson(content: String): List<ParsedImportKeyItem> {
        val list = mutableListOf<ParsedImportKeyItem>()
        try {
            if (content.startsWith("[")) {
                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    val elem = arr.opt(i)
                    when (elem) {
                        is JSONObject -> parseJsonObjectItem(elem, list.size + 1)?.let { list.add(it) }
                        is String -> {
                            val provider = ProviderType.detectProviderFromKey(elem) ?: ProviderType.OPENAI
                            list.add(
                                ParsedImportKeyItem(
                                    rawKey = elem.trim(),
                                    provider = provider,
                                    name = "${provider.displayName.substringBefore(" ")} #${list.size + 1}",
                                    preferredModel = provider.defaultModel
                                )
                            )
                        }
                    }
                }
            } else if (content.startsWith("{")) {
                val root = JSONObject(content)
                // Check if root has a "keys" or "api_keys" or "data" array
                val arrayField = root.optJSONArray("keys")
                    ?: root.optJSONArray("api_keys")
                    ?: root.optJSONArray("apiKeys")
                    ?: root.optJSONArray("data")

                if (arrayField != null) {
                    for (i in 0 until arrayField.length()) {
                        val obj = arrayField.optJSONObject(i)
                        if (obj != null) {
                            parseJsonObjectItem(obj, list.size + 1)?.let { list.add(it) }
                        } else {
                            val str = arrayField.optString(i, "").trim()
                            if (str.length >= 10) {
                                val provider = ProviderType.detectProviderFromKey(str) ?: ProviderType.OPENAI
                                list.add(
                                    ParsedImportKeyItem(
                                        rawKey = str,
                                        provider = provider,
                                        name = "${provider.displayName.substringBefore(" ")} #${list.size + 1}",
                                        preferredModel = provider.defaultModel
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Could be a single key JSON object or a map of provider/label -> key/object
                    val single = parseJsonObjectItem(root, 1)
                    if (single != null) {
                        list.add(single)
                    } else {
                        val iter = root.keys()
                        while (iter.hasNext()) {
                            val fieldName = iter.next()
                            val value = root.opt(fieldName)
                            when (value) {
                                is JSONObject -> {
                                    parseJsonObjectItem(value, list.size + 1, fieldName)?.let { list.add(it) }
                                }
                                is String -> {
                                    val trimmedVal = value.trim()
                                    if (trimmedVal.length >= 12) {
                                        val provider = ProviderType.detectProviderFromKey(trimmedVal, fieldName) ?: ProviderType.OPENAI
                                        val modelInKeyName = extractModelFromText(fieldName, provider)
                                        list.add(
                                            ParsedImportKeyItem(
                                                rawKey = trimmedVal,
                                                provider = provider,
                                                name = fieldName.ifBlank { "${provider.displayName.substringBefore(" ")} #${list.size + 1}" },
                                                preferredModel = modelInKeyName ?: provider.defaultModel,
                                                wasModelAutoDetectedFromFile = modelInKeyName != null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }

    private fun parseJsonObjectItem(obj: JSONObject, index: Int, parentKeyHint: String = ""): ParsedImportKeyItem? {
        val rawKey = listOf("key", "apiKey", "api_key", "rawKey", "token", "secret", "value")
            .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotBlank() } }
            ?: return null

        val providerStr = listOf("provider", "type", "service", "vendor")
            .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotBlank() } }
            .orEmpty()

        val nameStr = listOf("name", "label", "title", "nickname")
            .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotBlank() } }
            .orEmpty()

        val modelStr = listOf("model", "preferredModel", "preferred_model", "selectedModel", "selected_model", "defaultModel", "default_model")
            .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotBlank() } }

        val fallbacksStr = listOf("fallbackModels", "fallback_models", "fallbacks", "models")
            .firstNotNullOfOrNull { k ->
                val arr = obj.optJSONArray(k)
                if (arr != null) {
                    (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, "").trim().takeIf { it.isNotBlank() } }.joinToString(",")
                } else {
                    obj.optString(k, "").trim().takeIf { it.isNotBlank() }
                }
            }
            .orEmpty()

        val tasksStr = obj.optString("supportedTasks", AiTaskType.DEFAULT_TASKS_CSV).ifBlank { AiTaskType.DEFAULT_TASKS_CSV }
        val customUrl = obj.optString("customBaseUrl", obj.optString("baseUrl", "")).trim().takeIf { it.isNotBlank() }
        val customProto = obj.optString("customProtocol", "OPENAI").ifBlank { "OPENAI" }

        val explicitProvider = ProviderType.entries.find {
            it.name.equals(providerStr, ignoreCase = true) ||
                it.displayName.contains(providerStr, ignoreCase = true) && providerStr.length >= 3
        }
        val detectedProvider = explicitProvider
            ?: ProviderType.detectProviderFromKey(rawKey, "$providerStr $nameStr $parentKeyHint ${modelStr.orEmpty()}")
            ?: ProviderType.OPENAI

        val finalModel = modelStr?.takeIf { it.isNotBlank() } ?: detectedProvider.defaultModel
        val finalName = nameStr.ifBlank {
            parentKeyHint.ifBlank { "${detectedProvider.displayName.substringBefore(" ")} #$index" }
        }

        return ParsedImportKeyItem(
            rawKey = rawKey,
            provider = detectedProvider,
            name = finalName,
            preferredModel = finalModel,
            fallbackModels = fallbacksStr,
            supportedTasks = tasksStr,
            customBaseUrl = customUrl,
            customProtocol = customProto,
            wasModelAutoDetectedFromFile = !modelStr.isNullOrBlank()
        )
    }

    private fun extractModelFromText(text: String, provider: ProviderType?): String? {
        if (text.isBlank()) return null

        // Check known suggested models across all providers first
        val allKnownModels = ProviderType.entries.flatMap { it.suggestedModels }.distinct()
        for (known in allKnownModels.sortedByDescending { it.length }) {
            if (text.contains(known, ignoreCase = true)) {
                return known
            }
        }

        // Check regex match for model-like tokens (e.g., google/gemini-2.0-flash-exp:free, gpt-4o-mini, etc.)
        val match = modelTokenRegex.find(text)?.value?.trim(',', ';', '"', '\'', ')', '(', '[', ']')
        if (!match.isNullOrBlank() && !match.startsWith("sk-", true) && !match.startsWith("AIza")) {
            return match
        }
        return null
    }

    private fun extractLabelFromLine(textWithoutKey: String, provider: ProviderType, index: Int): String {
        val cleaned = textWithoutKey
            .replace(modelTokenRegex, "")
            .replace(Regex("""[=:|,\-\[\]()"]+"""), " ")
            .trim()
        return if (cleaned.length in 2..35) {
            cleaned
        } else {
            "${provider.displayName.substringBefore(" ")} #$index"
        }
    }

    /**
     * Exports a list of keys (with their decrypted raw keys) to a formatted JSON string for backup/sharing.
     */
    fun exportKeysToJson(keysWithPlain: List<Pair<ApiKeyEntity, String>>): String {
        val root = JSONObject()
        val arr = JSONArray()
        keysWithPlain.forEach { (entity, rawKey) ->
            val obj = JSONObject().apply {
                put("name", entity.name)
                put("provider", entity.provider.name)
                put("key", rawKey)
                put("preferredModel", entity.selectedModel)
                if (entity.fallbackModels.isNotBlank()) {
                    put("fallbackModels", entity.fallbackModels)
                }
                put("supportedTasks", entity.supportedTasks)
                put("priority", entity.priority)
                if (!entity.customBaseUrl.isNullOrBlank()) {
                    put("customBaseUrl", entity.customBaseUrl)
                    put("customProtocol", entity.customProtocol)
                }
            }
            arr.put(obj)
        }
        root.put("keys", arr)
        return root.toString(2)
    }

    /**
     * Exports a list of keys (with their decrypted raw keys) to a clean human-readable .txt format.
     */
    fun exportKeysToTxt(keysWithPlain: List<Pair<ApiKeyEntity, String>>): String {
        return keysWithPlain.joinToString("\n") { (entity, rawKey) ->
            "${entity.name} | ${entity.provider.name} | model=${entity.selectedModel} | $rawKey"
        }
    }
}
