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
    private const val KEY_ENGINE_ENABLED = "pollinations_engine_enabled"

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

    /**
     * Controls whether the built-in Pollinations image generation engine is enabled.
     * Per user instruction, this engine is turned OFF by default (false).
     */
    fun isEngineEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENGINE_ENABLED, false)
    }

    fun setEngineEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENGINE_ENABLED, enabled).apply()
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
        return AppStrings.tr(
            he = POLITE_REFUSAL_MESSAGE_HE,
            en = POLITE_REFUSAL_MESSAGE_EN,
            ar = "أعتذر بلطف 🙏، ولكن مولد الصور مخصص لإنشاء الصور فقط ولا يجيب على الأسئلة العامة. يرجى كتابة وصف للصورة أو المشهد الذي تريد إنشاؤه 🎨",
            ru = "Вежливо извиняюсь 🙏, но генератор изображений предназначен только для создания картинок и не отвечает на общие вопросы. Пожалуйста, опишите изображение или сцену 🎨",
            es = "Me disculpo cortésmente 🙏, pero el generador de imágenes solo crea imágenes y no responde preguntas generales. Describe la imagen o escena que deseas generar 🎨",
            fr = "Je m'excuse poliment 🙏, mais le générateur d'images ne crée que des images et ne répond pas aux questions générales. Veuillez décrire l'image ou la scène souhaitée 🎨",
            de = "Ich entschuldige mich höflich 🙏, aber der Bildgenerator erstellt nur Bilder und beantwortet keine allgemeinen Fragen. Bitte beschreiben Sie das gewünschte Bild 🎨",
            pt = "Peço desculpas educadamente 🙏, mas o gerador de imagens apenas cria imagens e não responde a perguntas gerais. Descreva a imagem ou cena que deseja gerar 🎨",
            zh = "礼貌地抱歉 🙏，图像生成器仅用于生成图片，不回答一般性问题。请描述您想要生成的画面或场景 🎨",
            ja = "恐れ入りますが 🙏、画像生成モードは画像の作成専用であり、一般的な質問には回答いたしません。生成したい画像やシーンを描写してください 🎨",
            tr = "Nazikçe özür dilerim 🙏, görsel oluşturucu yalnızca resim üretir ve genel soruları yanıtlamaz. Lütfen oluşturmak istediğiniz resmi veya sahneyi tarif edin 🎨",
            it = "Mi scuso cortesemente 🙏, ma il generatore di immagini crea solo immagini e non risponde a domande generali. Descrivi l'immagine o la scena che desideri generare 🎨"
        )
    }

    /**
     * Maximum character length for automatic image generation trigger detection (up to 300 chars).
     */
    const val MAX_AUTO_IMAGE_TRIGGER_CHARS = 300

    /**
     * Multi-language trigger terms across all 12 supported languages.
     * When any of these appear in a short message (up to 300 characters), the request is automatically
     * routed to the image generation model (Pollinations by default).
     */
    val AUTO_IMAGE_TRIGGER_TERMS: List<String> = listOf(
        // Hebrew (עברית)
        "צור לי תמונה", "צור תמונה", "תמונה של", "תכין לי תמונה", "תכין תמונה",
        "הכן לי תמונה", "הכן תמונה", "ייצר לי תמונה", "ייצר תמונה", "תיצור לי תמונה",
        "תיצור תמונה", "ליצור תמונה", "יצירת תמונה", "צייר לי", "צייר תמונה",
        "תצייר לי", "תצייר ", "ציור של", "איור של", "צור איור", "צור לי איור",
        "צור ציור", "צור לי ציור", "הפק תמונה", "תפיק תמונה", "עצב לי תמונה",
        "תעצב לי תמונה", "תראה לי תמונה", "הראה לי תמונה", "שלח לי תמונה",
        "תביא לי תמונה", "אפשר תמונה של", "אני רוצה תמונה של", "תעשה לי תמונה",
        "עשה לי תמונה", "צילום של", "תמונת נוף של", "צור לוגו", "תכין לוגו",
        // English
        "create an image", "create image", "create a picture", "create picture",
        "create a photo", "make an image", "make a picture", "make me an image",
        "make me a picture", "generate an image", "generate image", "generate a picture",
        "generate picture", "generate a photo", "generate art", "image of", "picture of",
        "photo of", "illustration of", "drawing of", "painting of", "sketch of",
        "portrait of", "draw me", "draw a ", "draw an ", "paint me", "paint a ",
        "sketch a ", "render an image", "design an image", "show me an image",
        "show me a picture", "create a logo",
        // Arabic (العربية)
        "أنشئ لي صورة", "انشئ لي صورة", "أنشئ صورة", "انشئ صورة", "صورة لـ", "صورة ل",
        "صورة عن", "ارسم لي", "ارسم صورة", "توليد صورة", "صمم لي صورة", "صمم صورة",
        "اصنع لي صورة", "اصنع صورة", "أرني صورة", "اعطني صورة", "أريد صورة",
        "لوحة لـ", "رسمة لـ",
        // Russian (Русский)
        "создай мне картинку", "создай картинку", "создай изображение", "создай мне изображение",
        "создай фото", "сделай мне картинку", "сделай картинку", "сделай изображение",
        "картинка с", "картинка ", "изображение ", "фотография ", "нарисуй мне", "нарисуй ",
        "сгенерируй изображение", "сгенерируй картинку", "покажи картинку", "покажи изображение",
        // Spanish (Español)
        "créame una imagen", "crea una imagen", "crear una imagen", "hazme una imagen",
        "haz una imagen", "genera una imagen", "generar imagen", "imagen de", "foto de",
        "pintura de", "ilustración de", "dibujo de", "dibuja un", "dibuja una", "dibújame",
        "píntame", "muéstrame una imagen",
        // French (Français)
        "crée-moi une image", "crée une image", "créer une image", "fais-moi une image",
        "fais une image", "génère une image", "générer une image", "image de", "image d'",
        "photo de", "photo d'", "illustration de", "dessin de", "dessine-moi", "dessine un",
        "dessine une", "montre-moi une image",
        // German (Deutsch)
        "erstell mir ein bild", "erstelle mir ein bild", "erstelle ein bild", "generiere ein bild",
        "mach mir ein bild", "mache ein bild", "bild von", "foto von", "zeichnung von",
        "illustration von", "zeichne mir", "zeichne ein", "zeichne eine", "male mir",
        "zeig mir ein bild",
        // Portuguese (Português)
        "crie para mim uma imagem", "crie uma imagem", "criar uma imagem", "cria uma imagem",
        "faça uma imagem", "faz uma imagem", "gere uma imagem", "gerar imagem", "imagem de",
        "foto de", "ilustração de", "desenho de", "desenhe para mim", "desenhe um",
        "desenhe uma", "mostre uma imagem",
        // Chinese (中文)
        "生成一张图片", "生成图片", "生成图像", "给我生成一张", "创建图片", "制作图片",
        "画一张", "画一个", "画一幅", "帮我画", "生成照片", "的图片", "的图像", "的插画",
        // Japanese (日本語)
        "画像を作成", "画像を生成", "画像を作って", "絵を描いて", "イラストを作成",
        "イラストを描いて", "写真を作成", "の画像", "のイラスト", "の絵を描",
        // Turkish (Türkçe)
        "bana bir resim oluştur", "resim oluştur", "görsel oluştur", "resim yap",
        "bana resim çiz", "resim çiz", "fotoğraf oluştur", "bana bir resim göster",
        "resmi çiz", "görseli oluştur",
        // Italian (Italiano)
        "creami un'immagine", "crea un'immagine", "crea una immagine", "genera un'immagine",
        "fammi un'immagine", "fai un'immagine", "immagine di", "foto di", "illustrazione di",
        "disegno di", "disegnami", "disegna un", "disegna una", "mostrami un'immagine"
    )

    /**
     * Checks if the message is short (up to 300 characters) and contains any of the multi-language
     * automatic image generation trigger terms.
     */
    fun isShortMessageAutoImageTrigger(rawPrompt: String): Boolean {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_AUTO_IMAGE_TRIGGER_CHARS) {
            return false
        }
        val lower = trimmed.lowercase()
        return AUTO_IMAGE_TRIGGER_TERMS.any { term -> lower.contains(term) }
    }

    /**
     * Heuristic filter to detect general questions that are NOT image descriptions
     * (used both as a pre-check and when no Google API key is entered).
     */
    fun isGeneralQuestionNotImageDescription(rawPrompt: String): Boolean {
        val text = rawPrompt.trim()
        if (text.isBlank()) return true
        val lower = text.lowercase()

        // If the message contains any multi-language auto image trigger term, it IS an image request
        if (AUTO_IMAGE_TRIGGER_TERMS.any { lower.contains(it) }) {
            return false
        }

        // Additional single visual nouns in multiple languages
        val explicitImageKeywords = listOf(
            "תמונה", "צייר", "ציור", "איור", "צילום", "תמונת", "דיוקן", "נוף", "סצנה", "רקע", "לוגו", "אייקון", "פוסטר", "טפט",
            "image", "picture", "photo", "draw", "paint", "illustration", "wallpaper", "portrait", "landscape", "scene", "render", "sketch", "logo", "poster",
            "صورة", "ارسم", "لوحة", "منظر", "خلفية", "شعار",
            "картинк", "изображени", "нарисуй", "пейзаж", "портрет", "обои", "логотип",
            "imagen", "dibuj", "pintura", "paisaje", "retrato", "fondo",
            "image", "dessin", "peinture", "paysage", "portrait",
            "bild", "zeichn", "gemälde", "landschaft",
            "imagem", "desenh", "pintura", "paisagem",
            "图片", "图像", "照片", "插画", "壁纸", "风景",
            "画像", "イラスト", "写真", "風景", "壁紙",
            "resim", "görsel", "çizim", "manzara", "duvar kağıdı",
            "immagine", "disegno", "paesaggio", "sfondo"
        )
        if (explicitImageKeywords.any { lower.contains(it) }) {
            return false
        }

        // Question mark at the end without visual keywords is a general question
        if (text.endsWith("?") || text.endsWith("؟") || text.endsWith("？")) {
            return true
        }

        // Common general-question / non-visual command starters in multiple languages
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
     * Strips explicit "create an image of" prefixes in all languages so the visual subject is clean.
     */
    fun stripImageCommandPrefix(prompt: String): String {
        var cleaned = prompt.trim()
        val prefixes = listOf(
            // Hebrew (longer prefixes first)
            "אני רוצה תמונה של ", "אפשר תמונה של ", "צור לי תמונה של ", "צור תמונה של ",
            "ייצר לי תמונה של ", "ייצר תמונה של ", "תכין לי תמונה של ", "תכין תמונה של ",
            "הכן לי תמונה של ", "הכן תמונה של ", "תיצור לי תמונה של ", "תיצור תמונה של ",
            "תעשה לי תמונה של ", "עשה לי תמונה של ", "תראה לי תמונה של ", "הראה לי תמונה של ",
            "שלח לי תמונה של ", "תביא לי תמונה של ", "עצב לי תמונה של ", "תעצב לי תמונה של ",
            "צייר לי תמונה של ", "תצייר לי תמונה של ", "צור לי איור של ", "צור איור של ",
            "צור לי ציור של ", "צור ציור של ", "צור לי תמונה ", "צור תמונה: ", "צור תמונה ",
            "תכין לי תמונה ", "הכן לי תמונה ", "ייצר לי תמונה ", "תעשה לי תמונה ",
            "צייר לי ", "תצייר לי ", "צייר ", "תצייר ", "תמונה של: ", "תמונה של ",
            "איור של ", "ציור של ", "צילום של ",
            // English
            "show me an image of ", "show me a picture of ", "create an image of ",
            "generate an image of ", "draw an image of ", "make me an image of ",
            "make me a picture of ", "make an image of ", "make a picture of ",
            "generate a picture of ", "create a picture of ", "create a photo of ",
            "generate image of ", "create image of ", "illustration of ", "painting of ",
            "drawing of ", "picture of ", "image of ", "photo of ", "draw me ", "paint me ", "draw ",
            // Arabic
            "أنشئ لي صورة لـ ", "أنشئ لي صورة عن ", "أنشئ لي صورة ", "انشئ لي صورة ",
            "أنشئ صورة لـ ", "أنشئ صورة ", "انشئ صورة ", "ارسم لي صورة ", "ارسم لي ",
            "ارسم صورة ", "صمم لي صورة ", "اصنع لي صورة ", "صورة لـ ", "صورة عن ", "صورة ل",
            // Russian
            "создай мне картинку с ", "создай мне картинку ", "создай картинку ",
            "создай изображение ", "сделай мне картинку ", "сделай картинку ",
            "сгенерируй изображение ", "сгенерируй картинку ", "нарисуй мне ", "нарисуй ",
            "картинка с ", "изображение ",
            // Spanish
            "créame una imagen de ", "crea una imagen de ", "crear una imagen de ",
            "hazme una imagen de ", "genera una imagen de ", "muéstrame una imagen de ",
            "imagen de ", "foto de ", "ilustración de ", "dibujo de ", "dibújame ", "dibuja ",
            // French
            "crée-moi une image de ", "crée-moi une image d'", "crée une image de ",
            "crée une image d'", "génère une image de ", "fais-moi une image de ",
            "image de ", "image d'", "photo de ", "illustration de ", "dessine-moi ", "dessine ",
            // German
            "erstelle mir ein bild von ", "erstell mir ein bild von ", "erstelle ein bild von ",
            "generiere ein bild von ", "mach mir ein bild von ", "bild von ", "foto von ",
            "zeichnung von ", "zeichne mir ", "zeichne ",
            // Portuguese
            "crie para mim uma imagem de ", "crie uma imagem de ", "criar uma imagem de ",
            "faça uma imagem de ", "gere uma imagem de ", "imagem de ", "foto de ",
            "ilustração de ", "desenhe para mim ", "desenhe ",
            // Chinese
            "给我生成一张", "生成一张图片", "生成图片：", "生成图片:", "生成图片",
            "生成图像", "创建图片", "制作图片", "帮我画一张", "帮我画", "画一张", "画一幅",
            // Japanese
            "画像を作成：", "画像を作成:", "画像を作成 ", "画像を生成 ", "画像を作って ", "絵を描いて ",
            // Turkish
            "bana bir resim oluştur ", "resim oluştur ", "görsel oluştur ", "bana resim çiz ", "resim çiz ",
            // Italian
            "creami un'immagine di ", "crea un'immagine di ", "genera un'immagine di ",
            "fammi un'immagine di ", "immagine di ", "foto di ", "illustrazione di ", "disegnami ", "disegna "
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
