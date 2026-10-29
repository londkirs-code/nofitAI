package com.example.util

import android.content.Context
import com.example.data.security.CryptoEngine

/**
 * Supported aspect ratios for Pollinations image generation:
 * 1:1, 3:4, 4:3, 9:16, and 16:9.
 */
enum class PollinationsAspectRatio(
    val ratioLabel: String,
    val width: Int,
    val height: Int,
    val displayHe: String,
    val displayEn: String
) {
    RATIO_1_1("1:1", 1024, 1024, "1:1 (ריבוע)", "1:1 (Square)"),
    RATIO_3_4("3:4", 768, 1024, "3:4 (לאורך)", "3:4 (Portrait)"),
    RATIO_4_3("4:3", 1024, 768, "4:3 (לרוחב)", "4:3 (Landscape)"),
    RATIO_9_16("9:16", 720, 1280, "9:16 (סטורי)", "9:16 (Story)"),
    RATIO_16_9("16:9", 1280, 720, "16:9 (מסך רחב)", "16:9 (Widescreen)");

    val label: String
        get() = ratioLabel

    companion object {
        fun fromLabel(label: String?): PollinationsAspectRatio {
            return entries.find { it.ratioLabel == label } ?: RATIO_1_1
        }
    }
}

/**
 * Manages Pollinations built-in image generation settings, privacy-aware optional Google key
 * ("Remember" checkbox), request filtering (polite refusal of general non-image questions),
 * automatic Hebrew-to-English prompt translation, and the strict modesty instruction.
 */
object PollinationsImageManager {

    private const val PREFS_NAME = "notifai_pollinations_prefs"
    private const val KEY_ASPECT_RATIO = "pollinations_aspect_ratio"
    private const val KEY_IMAGE_COUNT = "pollinations_image_count"
    private const val KEY_REMEMBER_GOOGLE_KEY = "pollinations_remember_google_key"
    private const val KEY_ENCRYPTED_GOOGLE_KEY = "pollinations_encrypted_google_key"
    private const val KEY_SELECTED_MODEL = "pollinations_selected_model"

    // Session-only Google key held strictly in RAM when "Remember" ("זכור") is NOT checked.
    @Volatile
    private var sessionGoogleKey: String = ""

    // Session-only map for any API keys added with "Remember" unchecked.
    private val sessionOnlyKeysMap = mutableMapOf<String, String>()

    fun putSessionOnlyKey(keyId: String, rawKey: String) {
        synchronized(sessionOnlyKeysMap) {
            sessionOnlyKeysMap[keyId] = rawKey
        }
    }

    fun getSessionOnlyKey(keyId: String): String? {
        synchronized(sessionOnlyKeysMap) {
            return sessionOnlyKeysMap[keyId]
        }
    }

    fun clearSessionOnlyKey(keyId: String) {
        synchronized(sessionOnlyKeysMap) {
            sessionOnlyKeysMap.remove(keyId)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAspectRatio(context: Context): PollinationsAspectRatio {
        val saved = prefs(context).getString(KEY_ASPECT_RATIO, PollinationsAspectRatio.RATIO_1_1.ratioLabel)
        return PollinationsAspectRatio.fromLabel(saved)
    }

    fun setAspectRatio(context: Context, ratio: PollinationsAspectRatio) {
        prefs(context).edit().putString(KEY_ASPECT_RATIO, ratio.ratioLabel).apply()
    }

    fun getImageCount(context: Context): Int {
        return prefs(context).getInt(KEY_IMAGE_COUNT, 1).coerceIn(1, 4)
    }

    fun setImageCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_IMAGE_COUNT, count.coerceIn(1, 4)).apply()
    }

    fun getSelectedModel(context: Context): String {
        return prefs(context).getString(KEY_SELECTED_MODEL, "flux")?.takeIf { it.isNotBlank() } ?: "flux"
    }

    fun setSelectedModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_SELECTED_MODEL, model.ifBlank { "flux" }).apply()
    }

    fun isRememberGoogleKeyEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMEMBER_GOOGLE_KEY, false)
    }

    /**
     * Updates the optional Google API key used for Hebrew-to-English translation & request filtering.
     * Privacy rule: The key is saved on the device ONLY if the user checks "Remember" ("זכור").
     * Otherwise it is kept strictly in memory for the current session and removed from disk.
     */
    fun updateGoogleTranslationKey(context: Context, rawKey: String, rememberOnDevice: Boolean) {
        val trimmed = rawKey.trim()
        sessionGoogleKey = trimmed
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_REMEMBER_GOOGLE_KEY, rememberOnDevice)
        if (rememberOnDevice && trimmed.isNotBlank()) {
            editor.putString(KEY_ENCRYPTED_GOOGLE_KEY, CryptoEngine.encrypt(trimmed))
        } else {
            editor.remove(KEY_ENCRYPTED_GOOGLE_KEY)
        }
        editor.apply()
    }

    /**
     * Retrieves the Google API key for direct translation calls to Google.
     * Checks session memory first, then device storage (if "Remember" was checked),
     * and finally falls back to any active Gemini key configured in the app.
     */
    fun getGoogleTranslationKey(context: Context, fallbackGeminiKey: String? = null): String {
        if (sessionGoogleKey.isNotBlank()) {
            return sessionGoogleKey
        }
        if (isRememberGoogleKeyEnabled(context)) {
            val enc = prefs(context).getString(KEY_ENCRYPTED_GOOGLE_KEY, null)
            if (!enc.isNullOrBlank()) {
                val dec = CryptoEngine.decrypt(enc)
                if (dec.isNotBlank()) {
                    sessionGoogleKey = dec
                    return dec
                }
            }
        }
        return fallbackGeminiKey?.trim().orEmpty()
    }

    fun hasUserEnteredTranslationKey(context: Context): Boolean {
        if (sessionGoogleKey.isNotBlank()) return true
        if (isRememberGoogleKeyEnabled(context)) {
            val enc = prefs(context).getString(KEY_ENCRYPTED_GOOGLE_KEY, null)
            return !enc.isNullOrBlank()
        }
        return false
    }

    /**
     * Strict Modesty System Instruction sent with every image prompt translation/generation:
     * Prevents generating female characters (women and girls).
     * Allowed: men, boys, and young children up to age 6; otherwise prefers a scene without people.
     */
    const val MODESTY_SYSTEM_INSTRUCTION_EN =
        "STRICT MODESTY RULE: You must NEVER generate or describe women, girls, or any female characters/figures of any age over 6. " +
            "Allowed human figures: men, boys, and young children/toddlers up to age 6 only. " +
            "In any other case (or when female figures are mentioned), replace them with a scenic view without people (nature, architecture, objects, atmosphere) or with men/boys/young children up to age 6."

    const val MODESTY_PROMPT_SUFFIX_EN =
        ", strict modesty rule: no women, no girls, no female characters, only men, boys, or young children under age 6, or a scenic view without people"

    const val POLITE_REFUSAL_MESSAGE_HE =
        "אני מצטער בנימוס 🙏, אך מצב יצירת התמונות מיועד ליצירת תמונות בלבד ואינו עונה על שאלות כלליות. אנא כתוב תיאור של התמונה או הסצנה שתרצה שאצור עבורך 🎨"

    const val POLITE_REFUSAL_MESSAGE_EN =
        "I politely apologize 🙏, but the image generator only creates images and does not answer general questions. Please describe the image or scene you would like me to generate 🎨"

    fun getPoliteRefusalMessage(): String {
        return AppStrings.tr(POLITE_REFUSAL_MESSAGE_HE, POLITE_REFUSAL_MESSAGE_EN)
    }

    /**
     * Heuristic filter to detect general questions that are NOT image descriptions
     * (used both as a pre-check and when no Google API key is entered).
     */
    fun isGeneralQuestionNotImageDescription(rawPrompt: String): Boolean {
        val text = rawPrompt.trim()
        if (text.isBlank()) return true
        val lower = text.lowercase()

        // If the user explicitly asks to draw/create/show an image, it IS an image request
        val explicitImageKeywords = listOf(
            "תמונה", "צייר", "ציור", "איור", "צילום", "תמונת", "צור לי תמונה", "ייצר תמונה",
            "דיוקן", "נוף", "סצנה", "רקע", "לוגו", "אייקון", "פוסטר", "טפט",
            "image", "picture", "photo", "draw", "paint", "illustration", "wallpaper",
            "portrait", "landscape", "scene", "render", "sketch", "logo", "poster"
        )
        if (explicitImageKeywords.any { lower.contains(it) }) {
            return false
        }

        // Question mark at the end without visual keywords is a general question
        if (text.endsWith("?") || text.endsWith("؟")) {
            return true
        }

        // Common general-question / non-visual command starters in Hebrew and English
        val questionStarters = listOf(
            "מה זה ", "מהי ", "מהו ", "מהם ", "מהן ", "מה השעה", "מה התאריך", "מה מזג האוויר",
            "מה ההבדל", "מה דעתך", "מה פירוש", "מה המשמעות", "מה קורה", "מה נשמע",
            "מי זה ", "מי היה ", "מי הייתה ", "מי ראש הממשלה", "מי נשיא", "מי המציא",
            "איך מכינים", "איך עושים", "איך פותרים", "איך אומרים", "איך כותבים", "איך מתקנים",
            "למה ", "מדוע ", "מתי ", "איפה נמצא", "היכן נמצא", "כמה זה ", "כמה עולה ", "כמה זמן ",
            "האם ", "תסביר לי", "הסבר לי", "תכתוב לי קוד", "כתוב קוד", "תכתוב מכתב", "נסח לי",
            "תרגם לי", "תסכם לי", "סכם לי", "פתור ", "חשב ", "מתכון ל", "בדיחה", "ספר לי בדיחה",
            "שלום", "היי", "אהלן", "בוקר טוב", "ערב טוב", "מה שלומך",
            "what is ", "what are ", "who is ", "who was ", "how do i ", "how to ",
            "why is ", "why do ", "when did ", "when is ", "where is ", "how much ", "how many ",
            "can you explain", "explain ", "write a code", "write code", "write an essay",
            "translate ", "summarize ", "solve ", "calculate ", "tell me a joke", "hello", "hi "
        )
        if (questionStarters.any { lower.startsWith(it) || lower == it.trim() }) {
            return true
        }

        // Pure math expression check (e.g., "2+2", "15 * 30")
        if (text.matches(Regex("^[0-9+\\-*/=()\\s.,^%]+$"))) {
            return true
        }

        return false
    }

    /**
     * Strips explicit "create an image of" prefixes so the visual subject is clean.
     */
    fun stripImageCommandPrefix(prompt: String): String {
        var cleaned = prompt.trim()
        val prefixes = listOf(
            "צור לי תמונה של ", "צור תמונה של ", "ייצר לי תמונה של ", "ייצר תמונה של ",
            "תכין לי תמונה של ", "תכין תמונה של ", "צייר לי תמונה של ", "צייר לי ", "צייר ",
            "תמונה של ", "צור תמונה: ", "צור תמונה ",
            "create an image of ", "generate an image of ", "draw an image of ",
            "generate image of ", "create image of ", "draw ", "image of ", "picture of "
        )
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }
        return cleaned.ifBlank { prompt.trim() }
    }

    /**
     * Enforces the Modesty Instruction (הנחיית צניעות) on any Hebrew or English prompt:
     * Removes/replaces references to women and girls (female characters), allowing only
     * men, boys, and young children up to age 6, or preferring a scene without people.
     */
    fun enforceModestyInstruction(prompt: String): Pair<String, Boolean> {
        var modified = prompt
        var modestyTriggered = false

        // Hebrew female terms -> replaced with scenic/allowed terms
        val hebrewFemalePatterns = listOf(
            Regex("\\b(אישה|אשה|נשים|בחורה|בחורות|גברת|גברות|עלמה|עלמות|נקבה|נקבות)\\b"),
            Regex("\\b(ילדה|ילדות|נערה|נערות|בת|בנות|תינוקת|פעוטה)\\b"),
            Regex("\\b(אמא|אימא|סבתא|דודה|אחות|אחיות|כלה|כלות|מלכה|מלכות|נסיכה|נסיכות)\\b"),
            Regex("\\b(שחקנית|זמרת|רקדנית|דוגמנית|ציירת|מורה|רופאה|אחות רחמנייה|חיילת|שוטרת)\\b")
        )
        for (regex in hebrewFemalePatterns) {
            if (regex.containsMatchIn(modified)) {
                modestyTriggered = true
                modified = regex.replace(modified, "סצנת נוף פסטורלית ללא אנשים")
            }
        }

        // English female terms -> replaced with scenic/allowed terms
        val englishFemalePatterns = listOf(
            Regex("\\b(woman|women|female|females|lady|ladies|gal|gals|feminine)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(girl|girls|schoolgirl|teenage girl|young woman|young lady|baby girl)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(mother|mom|grandmother|grandma|sister|sisters|aunt|bride|wife|wives|queen|princess|goddess|heroine)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(actress|waitress|ballerina|dancer girl|female model|supermodel|mermaid|witch|nun|waitress)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(she|her|hers|herself)\\b", RegexOption.IGNORE_CASE)
        )
        for (regex in englishFemalePatterns) {
            if (regex.containsMatchIn(modified)) {
                modestyTriggered = true
                modified = regex.replace(modified, "scenic landscape without people")
            }
        }

        val finalPrompt = if (modified.contains("strict modesty rule", ignoreCase = true)) {
            modified
        } else {
            "$modified$MODESTY_PROMPT_SUFFIX_EN"
        }

        return finalPrompt to modestyTriggered
    }

    /**
     * Built-in Hebrew-to-English visual dictionary fallback when no Google API key is provided,
     * ensuring Hebrew prompts still produce high-quality English keywords for Pollinations.
     */
    fun fallbackTranslateHebrewToEnglish(hebrewPrompt: String): String {
        val cleaned = stripImageCommandPrefix(hebrewPrompt)
        val dictionary = mapOf(
            "חתול" to "cat",
            "כלב" to "dog",
            "אריה" to "lion",
            "נמר" to "tiger",
            "סוס" to "horse",
            "ציפור" to "bird",
            "נשר" to "eagle",
            "דג" to "fish",
            "דולפין" to "dolphin",
            "לווייתן" to "whale",
            "פיל" to "elephant",
            "פרפר" to "butterfly",
            "בית" to "house",
            "ארמון" to "palace",
            "טירה" to "castle",
            "בניין" to "building",
            "גשר" to "bridge",
            "עיר" to "city",
            "כפר" to "village",
            "רחוב" to "street",
            "ירושלים" to "Jerusalem",
            "כותל" to "Western Wall Jerusalem",
            "בית המקדש" to "Holy Temple in Jerusalem",
            "בית כנסת" to "synagogue",
            "ספר תורה" to "Torah scroll",
            "שבת" to "Shabbat candles and table",
            "נרות" to "candles",
            "מנורה" to "golden menorah",
            "שמים" to "sky",
            "שמיים" to "sky",
            "שמש" to "sun",
            "ירח" to "moon",
            "כוכבים" to "stars",
            "עננים" to "clouds",
            "שקיעה" to "sunset",
            "זריחה" to "sunrise",
            "לילה" to "night",
            "יום" to "daylight",
            "בוקר" to "morning",
            "ערב" to "evening",
            "ים" to "sea ocean",
            "חוף" to "beach shore",
            "גלים" to "waves",
            "נהר" to "river",
            "אגם" to "lake",
            "מפל" to "waterfall",
            "מים" to "water",
            "הר" to "mountain",
            "הרים" to "mountains",
            "שלג" to "snow",
            "מושלג" to "snowy",
            "מושלגים" to "snow-covered",
            "יער" to "forest",
            "עץ" to "tree",
            "עצים" to "trees",
            "פרח" to "flower",
            "פרחים" to "flowers",
            "גינה" to "garden",
            "גן" to "garden park",
            "מדבר" to "desert",
            "שדה" to "field",
            "דשא" to "green grass",
            "גשם" to "rain",
            "קשת בענן" to "rainbow",
            "אש" to "fire flames",
            "אור" to "glowing light",
            "מכונית" to "car",
            "רכב" to "car vehicle",
            "מטוס" to "airplane",
            "ספינה" to "ship boat",
            "רכבת" to "train",
            "חללית" to "spaceship",
            "חלל" to "outer space galaxy",
            "רובוט" to "futuristic robot",
            "מחשב" to "computer",
            "ספר" to "book",
            "ספרים" to "books library",
            "שולחן" to "table",
            "כיסא" to "chair",
            "קפה" to "coffee cup",
            "עוגה" to "cake",
            "לחם" to "bread",
            "פירות" to "fruits",
            "תפוח" to "apple",
            "זהב" to "golden",
            "כסף" to "silver",
            "כחול" to "blue",
            "אדום" to "red",
            "ירוק" to "green",
            "צהוב" to "yellow",
            "סגול" to "purple",
            "לבן" to "white",
            "שחור" to "black",
            "עתידני" to "futuristic sci-fi",
            "עתיק" to "ancient historic",
            "קסום" to "magical enchanted",
            "יפה" to "beautiful",
            "מרהיב" to "stunning breathtaking",
            "ריאליסטי" to "photorealistic",
            "מצויר" to "illustrated digital art",
            "תלת מימד" to "3D render",
            "איש" to "man",
            "גבר" to "man",
            "גברים" to "men",
            "אנשים" to "men",
            "ילד" to "young boy",
            "ילדים" to "young boys",
            "תינוק" to "baby boy under age 6",
            "סבא" to "elderly man",
            "רב" to "rabbi scholar man",
            "מלך" to "king"
        )

        var working = cleaned
        val matchedEnglish = mutableListOf<String>()
        for ((heb, eng) in dictionary) {
            if (working.contains(heb)) {
                matchedEnglish.add(eng)
            }
        }

        return if (matchedEnglish.isNotEmpty()) {
            "${matchedEnglish.joinToString(", ")} ($cleaned), high quality, detailed digital art"
        } else {
            "$cleaned, high quality, detailed scenic visual"
        }
    }
}
