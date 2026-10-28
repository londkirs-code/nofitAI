package com.example.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Full-app localization engine for NotifAI (Task #15).
 * Automatically adapts to the device's system language (or user override in Settings)
 * and translates all UI labels, titles, explanations, buttons, and notification strings.
 */
object AppStrings {

    enum class Lang(val code: String, val nativeLabel: String) {
        AUTO("auto", "אוטומטי (שפת המכשיר) / Auto"),
        HE("he", "עברית"),
        EN("en", "English"),
        AR("ar", "العربية"),
        RU("ru", "Русский"),
        ES("es", "Español"),
        FR("fr", "Français"),
        DE("de", "Deutsch"),
        PT("pt", "Português"),
        ZH("zh", "中文"),
        JA("ja", "日本語"),
        TR("tr", "Türkçe"),
        IT("it", "Italiano")
    }

    private const val PREF_LANG_OVERRIDE = "app_lang_override"
    var langOverrideState by mutableStateOf(Lang.AUTO)
        private set

    fun initFromPrefs(context: Context) {
        val saved = context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
            .getString(PREF_LANG_OVERRIDE, Lang.AUTO.code) ?: Lang.AUTO.code
        langOverrideState = Lang.entries.find { it.code == saved } ?: Lang.AUTO
    }

    fun setLanguageOverride(context: Context, lang: Lang) {
        langOverrideState = lang
        context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LANG_OVERRIDE, lang.code)
            .apply()
    }

    fun currentLang(): Lang {
        val override = langOverrideState
        if (override != Lang.AUTO) return override
        val code = Locale.getDefault().language.lowercase(Locale.ROOT)
        return when (code) {
            "he", "iw" -> Lang.HE
            "ar" -> Lang.AR
            "ru" -> Lang.RU
            "es" -> Lang.ES
            "fr" -> Lang.FR
            "de" -> Lang.DE
            "pt" -> Lang.PT
            "zh" -> Lang.ZH
            "ja" -> Lang.JA
            "tr" -> Lang.TR
            "it" -> Lang.IT
            "en" -> Lang.EN
            else -> Lang.EN
        }
    }

    fun isRtl(): Boolean {
        return currentLang() in setOf(Lang.HE, Lang.AR)
    }

    fun layoutDirection(): LayoutDirection {
        return if (isRtl()) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    fun defaultAiLanguageInstruction(): String {
        val langName = when (currentLang()) {
            Lang.HE, Lang.AUTO -> "Hebrew"
            Lang.EN -> "English"
            Lang.AR -> "Arabic"
            Lang.RU -> "Russian"
            Lang.ES -> "Spanish"
            Lang.FR -> "French"
            Lang.DE -> "German"
            Lang.PT -> "Portuguese"
            Lang.ZH -> "Chinese"
            Lang.JA -> "Japanese"
            Lang.TR -> "Turkish"
            Lang.IT -> "Italian"
        }
        return "You are NotifAI, a helpful AI assistant. Respond in fluent $langName (unless asked otherwise) with complete, detailed answers."
    }

    /**
     * Translates any UI string across the app based on the active language.
     */
    fun tr(
        he: String,
        en: String = "",
        ar: String = "",
        ru: String = "",
        es: String = "",
        fr: String = ""
    ): String {
        val active = currentLang()
        if (active == Lang.HE) return he
        val resolvedEn = en.ifBlank { translateHebrewPhraseToEnglish(he) }
        return when (active) {
            Lang.EN -> resolvedEn
            Lang.AR -> ar.ifBlank { translateEnglishToTarget(resolvedEn, Lang.AR) }
            Lang.RU -> ru.ifBlank { translateEnglishToTarget(resolvedEn, Lang.RU) }
            Lang.ES -> es.ifBlank { translateEnglishToTarget(resolvedEn, Lang.ES) }
            Lang.FR -> fr.ifBlank { translateEnglishToTarget(resolvedEn, Lang.FR) }
            else -> translateEnglishToTarget(resolvedEn, active)
        }
    }

    private val heToEnTerms = listOf(
        "התראת NotifAI פעילה" to "NotifAI Notification Active",
        "התראה מושהית" to "Notification Paused",
        "מוכן לקבלת שאלות ישירות ממרכז ההתראות" to "Ready to answer questions directly from the notification shade",
        "הפעל כדי להציג את שורת הצ'אט בהתראות" to "Enable to show the inline AI chat bar in notifications",
        "🚀 הפעל התראה עכשיו" to "🚀 Activate Notification Now",
        "רענן התראה" to "Refresh Notification",
        "סגור התראה" to "Close Notification",
        "מפתחות מוכנים" to "Ready Keys",
        "מצב התראה" to "Notification Status",
        "קבועה (פעילה)" to "Pinned (Active)",
        "כבויה / נסגרה" to "Off / Closed",
        "איך זה עובד?" to "How It Works",
        "בדיקת שאילתה וצירוף קובץ" to "Test Query & Attach File",
        "באפשרותך לצרף תמונה או מסמך ולשלוח שאלה. התשובה תופיע מיידית בהתראה במכשירך:" to "Attach an image or file and send a question. The answer appears directly in your notification:",
        "קובץ מצורף מוכן לשליחה" to "Attachment ready to send",
        "יועבר למודל ה-AI יחד עם שאלתך" to "Will be sent to the AI model with your prompt",
        "שאל משהו את NotifAI..." to "Ask NotifAI anything...",
        "למשל: תסכם לי בקצרה מה זה מחשוב קוונטי" to "e.g., Summarize quantum computing briefly",
        "שלח להתראה" to "Send to Notification",
        "📸 צילום מסך" to "📸 Screenshot",
        "צרף מדיה" to "Attach Media",
        "✨ שלח תוכן" to "✨ Send Content",
        "⚙️ פעולות ומודלים" to "⚙️ Actions & Models",
        "🧠 מה דיברנו בשיחות הקודמות?" to "🧠 Recall Past Chats",
        "🧹 שיחה חדשה" to "🧹 New Chat",
        "זיכרון שיחה והמשכיות חכמה" to "Conversation Memory & Smart Continuity",
        "סדרי עדיפויות, משימות ומפתחות" to "Priorities, Tasks & API Keys",
        "הוסף מפתח" to "Add Key",
        "טרם הוגדרו מפתחות API" to "No API Keys Configured Yet",
        "הוסף מפתח ראשון עכשיו" to "Add First API Key Now",
        "שכפל מפתח" to "Duplicate Key",
        "העלה" to "Move Up",
        "הורד" to "Move Down",
        "ניתוב מפתח ומודל לפי סוג בקשה" to "Key & Model Routing by Task Type",
        "בחר מפתח ייעודי לכל סוג משימה (או אוטומטי לפי עדיפות):" to "Assign a dedicated key per task type (or Auto by priority):",
        "אוטומטי (לפי עדיפות)" to "Auto (By Priority)",
        "היסטוריית שיחות" to "Chat History",
        "שיחות שמורות באפליקציה" to "saved conversations",
        "חיפוש בהיסטוריית השיחות..." to "Search chat history...",
        "אין היסטוריית שיחות עדיין" to "No Chat History Yet",
        "לא נמצאו שיחות תואמות" to "No matching chats found",
        "כל שאלה שתענה עליה מתוך ההתראה תישמר כאן אוטומטית לעיון, חיפוש והעתקה בכל עת." to "Every question answered from the notification is saved here automatically for search and copy.",
        "מחיקת כל ההיסטוריה" to "Clear All History",
        "מחק הכל" to "Delete All",
        "ביטול" to "Cancel",
        "תוכן השיחה" to "Conversation Messages",
        "אתה" to "You",
        "הודעות" to "messages",
        "מרכז הרשאות האפליקציה" to "App Permissions Center",
        "סטטוס חי של כל ההרשאות, הסבר על תפקידן וקיצורי דרך להפעלה" to "Live status of all permissions, why each is needed, and quick activation shortcuts",
        "הצגת התראות (חובה לפעולת NotifAI)" to "Notifications (Required for NotifAI)",
        "הגדרות התראות" to "Notification Settings",
        "אשר התראות" to "Grant Notifications",
        "מיקרופון (הקלטת קול ודיבור לטקסט)" to "Microphone (Voice Record & Speech-to-Text)",
        "מאושר (פתח הגדרות)" to "Granted (Open Settings)",
        "אשר מיקרופון" to "Grant Microphone",
        "צילום מסך מיידי בלחיצה אחת (נגישות)" to "1-Tap Instant Screenshot (Accessibility)",
        "פעיל (הגדרות נגישות)" to "Active (Accessibility Settings)",
        "הפעל צילום מיידי" to "Enable Instant Screenshot",
        "ריצה רציפה ברקע (ללא הגבלת סוללה)" to "Unrestricted Background Run (Battery)",
        "מוגדר ללא הגבלה" to "Unrestricted",
        "אפשר ריצה ברקע" to "Allow Background Run",
        "מאושר ✓" to "Granted ✓",
        "טרם אושר" to "Not Granted",
        "הגדרות התראה והפעלה אוטומטית" to "Notification & Auto-Start Settings",
        "הפעלה אוטומטית בהדלקת המכשיר" to "Auto-Start on Device Boot",
        "משחזר את ההתראה הקבועה מיד לאחר הפעלת הטלפון מחדש" to "Restores the pinned notification automatically after reboot",
        "פתח הגדרות התראות מערכת אנדרואיד" to "Open Android Notification Settings",
        "שמירת מדיה בצ'אט (חיסכון באחסון)" to "Save Chat Media (Storage Saver)",
        "נפח מדיה שמורה נוכחי:" to "Current Saved Media Size:",
        "נקה את כל קבצי המדיה מהמכשיר" to "Clear All Saved Media Files",
        "הצפנת מפתחות בחומרה (Hardware Keystore)" to "Hardware Keystore Encryption",
        "שפת האפליקציה / App Language" to "App Language",
        "שלח תוכן ל-NotifAI" to "Send Content to NotifAI",
        "פעולות, פלט וניהול מפתחות" to "Actions, Output & API Keys",
        "הקלטת קול מיידית" to "Instant Voice Recording",
        "דיבור לטקסט (שליחה מיידית)" to "Speech to Text (Auto-Send)",
        "העלאת קובץ מצורף" to "Attach File",
        "פעולות על התשובה והשיחה" to "Response & Conversation Actions",
        "העתק" to "Copy",
        "שתף" to "Share",
        "נקה תצוגה" to "Clear Display",
        "התחל שיחה חדשה" to "Start New Chat",
        "מדיה / קובץ מהתשובה האחרונה בהתראה" to "Media / File from Latest Notification Response",
        "הורד קובץ למכשיר" to "Download File to Device",
        "נגן שמע בהתראה" to "Play Audio in Notification",
        "סגור התראת שמע" to "Close Audio Notification",
        "שמור תשובה כקובץ" to "Save Response as File",
        "סוג פלט מבוקש למענה הבא (ברירת מחדל: טקסט)" to "Requested Output Type for Next Reply (Default: Text)",
        "בחר איזה סוג תוצר תרצה לקבל מה-AI בשאלה הבאה מתוך ההתראה:" to "Choose the output format to receive from the AI on your next question:",
        "📝 טקסט (ברירת מחדל)" to "📝 Text (Default)",
        "🎨 תמונה" to "🎨 Image",
        "🔊 סאונד / שמע" to "🔊 Audio / Sound",
        "🎬 וידאו" to "🎬 Video",
        "📎 קובץ" to "📎 File",
        "כיבוי/הדלקה של מפתחות ובחירת מודל" to "Toggle Keys ON/OFF & Select Model",
        "הדלק/כבה מפתחות, שכפל מפתח או החלף מודל ישירות מכאן" to "Toggle keys, duplicate a key, or switch models & tasks directly here",
        "קבע כראשי" to "Set Primary",
        "🛑 סגור התראה" to "🛑 Close Notification",
        "הסר את התראת NotifAI מווילון ההתראות" to "Remove NotifAI notification from the notification shade",
        "סגור" to "Close",
        "אישור" to "OK",
        "שמור מפתח והגדרות" to "Save Key & Settings",
        "שאל AI" to "Ask AI",
        "שלח תוכן" to "Send Content",
        "⚙️ פעולות" to "⚙️ Actions"
    )

    private fun translateHebrewPhraseToEnglish(input: String): String {
        var result = input
        for ((heTerm, enTerm) in heToEnTerms) {
            if (result == heTerm) return enTerm
            if (result.contains(heTerm)) {
                result = result.replace(heTerm, enTerm)
            }
        }
        return result
    }

    private fun translateEnglishToTarget(enText: String, target: Lang): String {
        if (target == Lang.EN) return enText
        val wordMap: List<Pair<String, String>> = when (target) {
            Lang.AR -> listOf(
                "Notification" to "الإشعار",
                "API Keys" to "مفاتيح API",
                "History" to "السجل",
                "Settings" to "الإعدادات",
                "Copy" to "نسخ",
                "Share" to "مشاركة",
                "Clear Display" to "مسح العرض",
                "Start New Chat" to "بدء محادثة جديدة",
                "Add Key" to "إضافة مفتاح",
                "Duplicate Key" to "تكرار المفتاح",
                "Close" to "إغلاق",
                "Cancel" to "إلغاء",
                "Send Content" to "إرسال محتوى",
                "Ask AI" to "اسأل الذكاء الاصطناعي",
                "⚙️ Actions" to "⚙️ إجراءات"
            )
            Lang.RU -> listOf(
                "Notification" to "Уведомление",
                "API Keys" to "Ключи API",
                "History" to "История",
                "Settings" to "Настройки",
                "Copy" to "Копировать",
                "Share" to "Поделиться",
                "Clear Display" to "Очистить экран",
                "Start New Chat" to "Новый чат",
                "Add Key" to "Добавить ключ",
                "Duplicate Key" to "Дублировать ключ",
                "Close" to "Закрыть",
                "Cancel" to "Отмена",
                "Send Content" to "Отправить контент",
                "Ask AI" to "Спросить ИИ",
                "⚙️ Actions" to "⚙️ Действия"
            )
            Lang.ES -> listOf(
                "Notification" to "Notificación",
                "API Keys" to "Claves API",
                "History" to "Historial",
                "Settings" to "Ajustes",
                "Copy" to "Copiar",
                "Share" to "Compartir",
                "Clear Display" to "Limpiar vista",
                "Start New Chat" to "Nuevo chat",
                "Add Key" to "Añadir clave",
                "Duplicate Key" to "Duplicar clave",
                "Close" to "Cerrar",
                "Cancel" to "Cancelar",
                "Send Content" to "Enviar contenido",
                "Ask AI" to "Preguntar a IA",
                "⚙️ Actions" to "⚙️ Acciones"
            )
            Lang.FR -> listOf(
                "Notification" to "Notification",
                "API Keys" to "Clés API",
                "History" to "Historique",
                "Settings" to "Paramètres",
                "Copy" to "Copier",
                "Share" to "Partager",
                "Clear Display" to "Effacer l'affichage",
                "Start New Chat" to "Nouvelle discussion",
                "Add Key" to "Ajouter une clé",
                "Duplicate Key" to "Dupliquer la clé",
                "Close" to "Fermer",
                "Cancel" to "Annuler",
                "Send Content" to "Envoyer du contenu",
                "Ask AI" to "Demander à l'IA",
                "⚙️ Actions" to "⚙️ Actions"
            )
            else -> emptyList()
        }
        var out = enText
        for ((k, v) in wordMap) {
            if (out == k) return v
            if (out.contains(k)) out = out.replace(k, v)
        }
        return out
    }

    val tabNotification: String
        get() = tr("התראה", "Notification", "الإشعار", "Уведомление", "Notificación", "Notification")

    val tabKeys: String
        get() = tr("מפתחות", "API Keys", "مفاتيح API", "Ключи API", "Claves API", "Clés API")

    val tabHistory: String
        get() = tr("היסטוריה", "History", "السجل", "История", "Historial", "Historique")

    val tabSettings: String
        get() = tr("הגדרות", "Settings", "الإعدادات", "Настройки", "Ajustes", "Paramètres")

    val notifPermissionTitle: String
        get() = tr(
            he = "🔔 למה נדרשת הרשאת התראות?",
            en = "🔔 Why is Notification Permission Needed?",
            ar = "🔔 لماذا مطلوب إذن الإشعارات؟",
            ru = "🔔 Зачем нужно разрешение на уведомления?",
            es = "🔔 ¿Por qué se necesita permiso de notificaciones?",
            fr = "🔔 Pourquoi l'autorisation de notification est-elle requise ?"
        )

    val notifPermissionExplanation: String
        get() = tr(
            he = "NotifAI פועלת כמרכז בינה מלאכותית חכם ישירות בתוך וילון ההתראות של המכשיר שלך.\n\nכדי להציג את שורת הכתיבה המהירה בהתראה ולקבל תשובות ממודלי ה-AI בלי לצאת מהאפליקציה שבה אתה נמצא, נדרשת הרשאת הצגת התראות.",
            en = "NotifAI operates as a smart AI assistant directly inside your device's notification shade.\n\nTo display the inline reply bar and show AI answers without leaving your current app, Notification permission is required."
        )

    val notifPermissionApproveBtn: String
        get() = tr(
            he = "הבנתי, אשר התראות עכשיו",
            en = "Got it, Grant Notification Permission"
        )

    val cancelBtn: String
        get() = tr("אחר כך", "Later", "لاحقاً", "Позже", "Más tarde", "Plus tard")
}
