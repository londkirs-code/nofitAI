package com.example.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Intelligent full-app localization engine for NotifAI.
 * Automatically adapts to the device's system language (or user override in Settings)
 * and translates all UI labels, titles, explanations, buttons, and notification strings
 * across 12 languages:
 * Hebrew, English, Arabic, Russian, Spanish, French, German, Portuguese, Chinese, Japanese, Turkish, Italian.
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
     * Translates any string across the app based on the active language.
     * Checks explicit parameters first, then queries the comprehensive 12-language vocabulary table,
     * and finally cascades to English and Hebrew.
     */
    fun tr(
        he: String,
        en: String = "",
        ar: String = "",
        ru: String = "",
        es: String = "",
        fr: String = "",
        de: String = "",
        pt: String = "",
        zh: String = "",
        ja: String = "",
        tr: String = "",
        it: String = ""
    ): String {
        val active = currentLang()
        if (active == Lang.HE) return he

        // 1. Direct parameter match if provided
        val directParam = when (active) {
            Lang.EN -> en
            Lang.AR -> ar
            Lang.RU -> ru
            Lang.ES -> es
            Lang.FR -> fr
            Lang.DE -> de
            Lang.PT -> pt
            Lang.ZH -> zh
            Lang.JA -> ja
            Lang.TR -> tr
            Lang.IT -> it
            Lang.HE, Lang.AUTO -> he
        }
        if (directParam.isNotBlank()) return directParam

        // 2. Query master 12-language dictionary by Hebrew key
        val entry = dictionary[he]
        if (entry != null) {
            val fromDict = entry[active]
            if (!fromDict.isNullOrBlank()) return fromDict
            val enFallback = entry[Lang.EN]
            if (!enFallback.isNullOrBlank()) return enFallback
        }

        // 3. Fallback to English parameter, or heuristic translate
        val resolvedEn = en.ifBlank { translateHebrewPhraseToEnglish(he) }
        if (active == Lang.EN) return resolvedEn

        // 4. Translate resolved English phrase to target language
        val targetMapped = translateEnglishToTarget(resolvedEn, active)
        if (targetMapped.isNotBlank() && targetMapped != resolvedEn) return targetMapped

        return resolvedEn.ifBlank { he }
    }

    /**
     * Master vocabulary dictionary supporting all 12 languages.
     */
    private val dictionary: Map<String, Map<Lang, String>> = mapOf(
        "התראה" to mapOf(
            Lang.EN to "Notification", Lang.AR to "الإشعار", Lang.RU to "Уведомление",
            Lang.ES to "Notificación", Lang.FR to "Notification", Lang.DE to "Benachrichtigung",
            Lang.PT to "Notificação", Lang.ZH to "通知", Lang.JA to "通知",
            Lang.TR to "Bildirim", Lang.IT to "Notifica"
        ),
        "מפתחות" to mapOf(
            Lang.EN to "API Keys", Lang.AR to "مفاتيح API", Lang.RU to "Ключи API",
            Lang.ES to "Claves API", Lang.FR to "Clés API", Lang.DE to "API-Schlüssel",
            Lang.PT to "Chaves API", Lang.ZH to "API 密钥", Lang.JA to "APIキー",
            Lang.TR to "API Anahtarları", Lang.IT to "Chiavi API"
        ),
        "היסטוריה" to mapOf(
            Lang.EN to "History", Lang.AR to "السجل", Lang.RU to "История",
            Lang.ES to "Historial", Lang.FR to "Historique", Lang.DE to "Verlauf",
            Lang.PT to "Histórico", Lang.ZH to "历史记录", Lang.JA to "履歴",
            Lang.TR to "Geçmiş", Lang.IT to "Cronologia"
        ),
        "הגדרות" to mapOf(
            Lang.EN to "Settings", Lang.AR to "الإعدادات", Lang.RU to "Настройки",
            Lang.ES to "Ajustes", Lang.FR to "Paramètres", Lang.DE to "Einstellungen",
            Lang.PT to "Configurações", Lang.ZH to "设置", Lang.JA to "設定",
            Lang.TR to "Ayarlar", Lang.IT to "Impostazioni"
        ),
        "העתק" to mapOf(
            Lang.EN to "Copy", Lang.AR to "نسخ", Lang.RU to "Копировать",
            Lang.ES to "Copiar", Lang.FR to "Copier", Lang.DE to "Kopieren",
            Lang.PT to "Copiar", Lang.ZH to "复制", Lang.JA to "コピー",
            Lang.TR to "Kopyala", Lang.IT to "Copia"
        ),
        "שתף" to mapOf(
            Lang.EN to "Share", Lang.AR to "مشاركة", Lang.RU to "Поделиться",
            Lang.ES to "Compartir", Lang.FR to "Partager", Lang.DE to "Teilen",
            Lang.PT to "Compartilhar", Lang.ZH to "分享", Lang.JA to "共有",
            Lang.TR to "Paylaş", Lang.IT to "Condividi"
        ),
        "נקה" to mapOf(
            Lang.EN to "Clear", Lang.AR to "مسح", Lang.RU to "Очистить",
            Lang.ES to "Limpiar", Lang.FR to "Effacer", Lang.DE to "Löschen",
            Lang.PT to "Limpar", Lang.ZH to "清除", Lang.JA to "クリア",
            Lang.TR to "Temizle", Lang.IT to "Cancella"
        ),
        "התחל שיחה חדשה" to mapOf(
            Lang.EN to "Start New Chat", Lang.AR to "بدء محادثة جديدة", Lang.RU to "Новый чат",
            Lang.ES to "Iniciar nuevo chat", Lang.FR to "Nouvelle discussion", Lang.DE to "Neuen Chat starten",
            Lang.PT to "Iniciar novo chat", Lang.ZH to "开始新对话", Lang.JA to "新しいチャットを開始",
            Lang.TR to "Yeni Sohbet Başlat", Lang.IT to "Inizia nuova chat"
        ),
        "🧹 שיחה חדשה" to mapOf(
            Lang.EN to "🧹 New Chat", Lang.AR to "🧹 محادثة جديدة", Lang.RU to "🧹 Новый чат",
            Lang.ES to "🧹 Nuevo chat", Lang.FR to "🧹 Nouvelle discussion", Lang.DE to "🧹 Neuer Chat",
            Lang.PT to "🧹 Novo chat", Lang.ZH to "🧹 新对话", Lang.JA to "🧹 新しいチャット",
            Lang.TR to "🧹 Yeni Sohbet", Lang.IT to "🧹 Nuova chat"
        ),
        "אתה" to mapOf(
            Lang.EN to "You", Lang.AR to "أنت", Lang.RU to "Вы",
            Lang.ES to "Tú", Lang.FR to "Vous", Lang.DE to "Du",
            Lang.PT to "Você", Lang.ZH to "你", Lang.JA to "あなた",
            Lang.TR to "Sen", Lang.IT to "Tu"
        ),
        "שאל AI" to mapOf(
            Lang.EN to "Ask AI", Lang.AR to "اسأل الذكاء الاصطناعي", Lang.RU to "Спросить ИИ",
            Lang.ES to "Preguntar a IA", Lang.FR to "Demander à l'IA", Lang.DE to "KI fragen",
            Lang.PT to "Perguntar à IA", Lang.ZH to "向 AI 提问", Lang.JA to "AIに質問",
            Lang.TR to "Yapay Zekaya Sor", Lang.IT to "Chiedi all'IA"
        ),
        "שלח תוכן" to mapOf(
            Lang.EN to "Send Content", Lang.AR to "إرسال محتوى", Lang.RU to "Отправить контент",
            Lang.ES to "Enviar contenido", Lang.FR to "Envoyer du contenu", Lang.DE to "Inhalt senden",
            Lang.PT to "Enviar conteúdo", Lang.ZH to "发送内容", Lang.JA to "コンテンツを送信",
            Lang.TR to "İçerik Gönder", Lang.IT to "Invia contenuto"
        ),
        "⚙️ פעולות" to mapOf(
            Lang.EN to "⚙️ Actions", Lang.AR to "⚙️ إجراءات", Lang.RU to "⚙️ Действия",
            Lang.ES to "⚙️ Acciones", Lang.FR to "⚙️ Actions", Lang.DE to "⚙️ Aktionen",
            Lang.PT to "⚙️ Ações", Lang.ZH to "⚙️ 操作", Lang.JA to "⚙️ アクション",
            Lang.TR to "⚙️ Eylemler", Lang.IT to "⚙️ Azioni"
        ),
        "ביטול" to mapOf(
            Lang.EN to "Cancel", Lang.AR to "إلغاء", Lang.RU to "Отмена",
            Lang.ES to "Cancelar", Lang.FR to "Annuler", Lang.DE to "Abbrechen",
            Lang.PT to "Cancelar", Lang.ZH to "取消", Lang.JA to "キャンセル",
            Lang.TR to "İptal", Lang.IT to "Annulla"
        ),
        "אישור" to mapOf(
            Lang.EN to "OK", Lang.AR to "موافق", Lang.RU to "ОК",
            Lang.ES to "Aceptar", Lang.FR to "OK", Lang.DE to "OK",
            Lang.PT to "OK", Lang.ZH to "确定", Lang.JA to "OK",
            Lang.TR to "Tamam", Lang.IT to "OK"
        ),
        "סגור" to mapOf(
            Lang.EN to "Close", Lang.AR to "إغلاق", Lang.RU to "Закрыть",
            Lang.ES to "Cerrar", Lang.FR to "Fermer", Lang.DE to "Schließen",
            Lang.PT to "Fechar", Lang.ZH to "关闭", Lang.JA to "閉じる",
            Lang.TR to "Kapat", Lang.IT to "Chiudi"
        ),
        "שמור מפתח והגדרות" to mapOf(
            Lang.EN to "Save Key & Settings", Lang.AR to "حفظ المفتاح والإعدادات", Lang.RU to "Сохранить ключ и настройки",
            Lang.ES to "Guardar clave y ajustes", Lang.FR to "Enregistrer la clé et les paramètres", Lang.DE to "Schlüssel und Einstellungen speichern",
            Lang.PT to "Salvar chave e configurações", Lang.ZH to "保存密钥和设置", Lang.JA to "キーと設定を保存",
            Lang.TR to "Anahtarı ve Ayarları Kaydet", Lang.IT to "Salva chiave e impostazioni"
        ),
        "הוסף מפתח" to mapOf(
            Lang.EN to "Add Key", Lang.AR to "إضافة مفتاح", Lang.RU to "Добавить ключ",
            Lang.ES to "Añadir clave", Lang.FR to "Ajouter une clé", Lang.DE to "Schlüssel hinzufügen",
            Lang.PT to "Adicionar chave", Lang.ZH to "添加密钥", Lang.JA to "キーを追加",
            Lang.TR to "Anahtar Ekle", Lang.IT to "Aggiungi chiave"
        ),
        "שכפל מפתח" to mapOf(
            Lang.EN to "Duplicate Key", Lang.AR to "تكرار المفتاح", Lang.RU to "Дублировать ключ",
            Lang.ES to "Duplicar clave", Lang.FR to "Dupliquer la clé", Lang.DE to "Schlüssel duplizieren",
            Lang.PT to "Duplicar chave", Lang.ZH to "复制密钥", Lang.JA to "キーを複製",
            Lang.TR to "Anahtarı Çoğalt", Lang.IT to "Duplica chiave"
        ),
        "קבע כראשי" to mapOf(
            Lang.EN to "Set Primary", Lang.AR to "تعيين كأساسي", Lang.RU to "Сделать основным",
            Lang.ES to "Establecer como principal", Lang.FR to "Définir comme principal", Lang.DE to "Als Standard festlegen",
            Lang.PT to "Definir como principal", Lang.ZH to "设为主要", Lang.JA to "メインに設定",
            Lang.TR to "Birincil Yap", Lang.IT to "Imposta come principale"
        ),
        "הורד קובץ למכשיר" to mapOf(
            Lang.EN to "Download File to Device", Lang.AR to "تنزيل الملف إلى الجهاز", Lang.RU to "Скачать файл на устройство",
            Lang.ES to "Descargar archivo al dispositivo", Lang.FR to "Télécharger le fichier sur l'appareil", Lang.DE to "Datei auf das Gerät herunterladen",
            Lang.PT to "Baixar arquivo para o dispositivo", Lang.ZH to "下载文件到设备", Lang.JA to "ファイルをデバイスにダウンロード",
            Lang.TR to "Dosyayı Cihaza İndir", Lang.IT to "Scarica file sul dispositivo"
        ),
        "🖼️ פתח תמונה" to mapOf(
            Lang.EN to "🖼️ View Image", Lang.AR to "🖼️ عرض الصورة", Lang.RU to "🖼️ Открыть фото",
            Lang.ES to "🖼️ Ver imagen", Lang.FR to "🖼️ Voir l'image", Lang.DE to "🖼️ Bild anzeigen",
            Lang.PT to "🖼️ Ver imagem", Lang.ZH to "🖼️ 查看图片", Lang.JA to "🖼️ 画像を表示",
            Lang.TR to "🖼️ Resmi Görüntüle", Lang.IT to "🖼️ Visualizza immagine"
        ),
        "🎬 נגן וידאו" to mapOf(
            Lang.EN to "🎬 Play Video", Lang.AR to "🎬 تشغيل الفيديو", Lang.RU to "🎬 Воспроизвести видео",
            Lang.ES to "🎬 Reproducir video", Lang.FR to "🎬 Lire la vidéo", Lang.DE to "🎬 Video abspielen",
            Lang.PT to "🎬 Reproduzir vídeo", Lang.ZH to "🎬 播放视频", Lang.JA to "🎬 動画を再生",
            Lang.TR to "🎬 Videoyu Oynat", Lang.IT to "🎬 Riproduci video"
        ),
        "▶️ נגן שמע" to mapOf(
            Lang.EN to "▶️ Play Audio", Lang.AR to "▶️ تشغيل الصوت", Lang.RU to "▶️ Слушать аудио",
            Lang.ES to "▶️ Reproducir audio", Lang.FR to "▶️ Écouter l'audio", Lang.DE to "▶️ Audio abspielen",
            Lang.PT to "▶️ Reproduzir áudio", Lang.ZH to "▶️ 播放音频", Lang.JA to "▶️ 音声を再生",
            Lang.TR to "▶️ Sesi Çal", Lang.IT to "▶️ Riproduci audio"
        ),
        "📄 פתח קובץ" to mapOf(
            Lang.EN to "📄 Open File", Lang.AR to "📄 فتح الملف", Lang.RU to "📄 Открыть файл",
            Lang.ES to "📄 Abrir archivo", Lang.FR to "📄 Ouvrir le fichier", Lang.DE to "📄 Datei öffnen",
            Lang.PT to "📄 Abrir arquivo", Lang.ZH to "📄 打开文件", Lang.JA to "📄 ファイルを開く",
            Lang.TR to "📄 Dosyayı Aç", Lang.IT to "📄 Apri file"
        ),
        "נגן שמע בהתראה" to mapOf(
            Lang.EN to "Play Audio in Notification", Lang.AR to "تشغيل الصوت في الإشعار", Lang.RU to "Воспроизвести аудио в уведомлении",
            Lang.ES to "Reproducir audio en notificación", Lang.FR to "Lire l'audio dans la notification", Lang.DE to "Audio in Benachrichtigung abspielen",
            Lang.PT to "Reproduzir áudio na notificação", Lang.ZH to "在通知中播放音频", Lang.JA to "通知で音声を再生",
            Lang.TR to "Bildirimde Sesi Çal", Lang.IT to "Riproduci audio nella notifica"
        ),
        "סגור התראת שמע" to mapOf(
            Lang.EN to "Close Audio Notification", Lang.AR to "إغلاق إشعار الصوت", Lang.RU to "Закрыть уведомление об аудио",
            Lang.ES to "Cerrar notificación de audio", Lang.FR to "Fermer la notification audio", Lang.DE to "Audio-Benachrichtigung schließen",
            Lang.PT to "Fechar notificação de áudio", Lang.ZH to "关闭音频通知", Lang.JA to "音声通知を閉じる",
            Lang.TR to "Ses Bildirimini Kapat", Lang.IT to "Chiudi notifica audio"
        ),
        "מחיקת כל המפתחות השמורים" to mapOf(
            Lang.EN to "Delete All Saved Keys", Lang.AR to "حذف جميع المفاتيح المحفوظة", Lang.RU to "Удалить все сохраненные ключи",
            Lang.ES to "Eliminar todas las claves guardadas", Lang.FR to "Supprimer toutes les clés enregistrées", Lang.DE to "Alle gespeicherten Schlüssel löschen",
            Lang.PT to "Excluir todas as chaves salvas", Lang.ZH to "删除所有已保存的密钥", Lang.JA to "保存されたすべてのキーを削除",
            Lang.TR to "Kayıtlı Tüm Anahtarları Sil", Lang.IT to "Elimina tutte le chiavi salvate"
        ),
        "מחיקת כל ההיסטוריה" to mapOf(
            Lang.EN to "Clear All History", Lang.AR to "مسح كامل السجل", Lang.RU to "Очистить всю историю",
            Lang.ES to "Borrar todo el historial", Lang.FR to "Effacer tout l'historique", Lang.DE to "Gesamten Verlauf löschen",
            Lang.PT to "Limpar todo o histórico", Lang.ZH to "清除所有历史记录", Lang.JA to "すべての履歴を消去",
            Lang.TR to "Tüm Geçmişi Temizle", Lang.IT to "Cancella tutta la cronologia"
        ),
        "שיחה פעילה" to mapOf(
            Lang.EN to "Active Conversation", Lang.AR to "المحادثة النشطة", Lang.RU to "Активный чат",
            Lang.ES to "Conversación activa", Lang.FR to "Discussion active", Lang.DE to "Aktiver Chat",
            Lang.PT to "Conversa ativa", Lang.ZH to "当前对话", Lang.JA to "アクティブな会話",
            Lang.TR to "Aktif Sohbet", Lang.IT to "Conversazione attiva"
        ),
        "אין חיבור פעיל לאינטרנט" to mapOf(
            Lang.EN to "No Active Internet Connection", Lang.AR to "لا يوجد اتصال بالإنترنت", Lang.RU to "Нет подключения к Интернету",
            Lang.ES to "Sin conexión a Internet", Lang.FR to "Pas de connexion Internet", Lang.DE to "Keine Internetverbindung",
            Lang.PT to "Sem conexão com a Internet", Lang.ZH to "无网络连接", Lang.JA to "インターネット接続がありません",
            Lang.TR to "İnternet Bağlantısı Yok", Lang.IT to "Nessuna connessione Internet"
        )
    )

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
        "זיכרון שיחה והמשכיות חכמה" to "Conversation Memory & Smart Continuity",
        "סדרי עדיפויות, משימות ומפתחות" to "Priorities, Tasks & API Keys",
        "טרם הוגדרו מפתחות API" to "No API Keys Configured Yet",
        "הוסף מפתח ראשון עכשיו" to "Add First API Key Now",
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
        "מחק הכל" to "Delete All",
        "תוכן השיחה" to "Conversation Messages",
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
        "נקה תצוגה" to "Clear Display",
        "מדיה / קובץ מהתשובה האחרונה בהתראה" to "Media / File from Latest Notification Response",
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
        "🛑 סגור התראה" to "🛑 Close Notification",
        "הסר את התראת NotifAI מווילון ההתראות" to "Remove NotifAI notification from the notification shade"
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
                "Notification" to "الإشعار", "API Keys" to "مفاتيح API", "History" to "السجل",
                "Settings" to "الإعدادات", "Copy" to "نسخ", "Share" to "مشاركة",
                "Clear Display" to "مسح العرض", "Start New Chat" to "بدء محادثة جديدة",
                "Add Key" to "إضافة مفتاح", "Duplicate Key" to "تكرار المفتاح", "Close" to "إغلاق",
                "Cancel" to "إلغاء", "Send Content" to "إرسال محتوى", "Ask AI" to "اسأل الذكاء الاصطناعي",
                "⚙️ Actions" to "⚙️ إجراءات", "Download File" to "تنزيل الملف", "Play Audio" to "تشغيل الصوت"
            )
            Lang.RU -> listOf(
                "Notification" to "Уведомление", "API Keys" to "Ключи API", "History" to "История",
                "Settings" to "Настройки", "Copy" to "Копировать", "Share" to "Поделиться",
                "Clear Display" to "Очистить экран", "Start New Chat" to "Новый чат",
                "Add Key" to "Добавить ключ", "Duplicate Key" to "Дублировать ключ", "Close" to "Закрыть",
                "Cancel" to "Отмена", "Send Content" to "Отправить контент", "Ask AI" to "Спросить ИИ",
                "⚙️ Actions" to "⚙️ Действия", "Download File" to "Скачать файл", "Play Audio" to "Слушать аудио"
            )
            Lang.ES -> listOf(
                "Notification" to "Notificación", "API Keys" to "Claves API", "History" to "Historial",
                "Settings" to "Ajustes", "Copy" to "Copiar", "Share" to "Compartir",
                "Clear Display" to "Limpiar vista", "Start New Chat" to "Nuevo chat",
                "Add Key" to "Añadir clave", "Duplicate Key" to "Duplicar clave", "Close" to "Cerrar",
                "Cancel" to "Cancelar", "Send Content" to "Enviar contenido", "Ask AI" to "Preguntar a IA",
                "⚙️ Actions" to "⚙️ Acciones", "Download File" to "Descargar archivo", "Play Audio" to "Reproducir audio"
            )
            Lang.FR -> listOf(
                "Notification" to "Notification", "API Keys" to "Clés API", "History" to "Historique",
                "Settings" to "Paramètres", "Copy" to "Copier", "Share" to "Partager",
                "Clear Display" to "Effacer l'affichage", "Start New Chat" to "Nouvelle discussion",
                "Add Key" to "Ajouter une clé", "Duplicate Key" to "Dupliquer la clé", "Close" to "Fermer",
                "Cancel" to "Annuler", "Send Content" to "Envoyer du contenu", "Ask AI" to "Demander à l'IA",
                "⚙️ Actions" to "⚙️ Actions", "Download File" to "Télécharger le fichier", "Play Audio" to "Lire l'audio"
            )
            Lang.DE -> listOf(
                "Notification" to "Benachrichtigung", "API Keys" to "API-Schlüssel", "History" to "Verlauf",
                "Settings" to "Einstellungen", "Copy" to "Kopieren", "Share" to "Teilen",
                "Clear Display" to "Anzeige löschen", "Start New Chat" to "Neuen Chat starten",
                "Add Key" to "Schlüssel hinzufügen", "Duplicate Key" to "Schlüssel duplizieren", "Close" to "Schließen",
                "Cancel" to "Abbrechen", "Send Content" to "Inhalt senden", "Ask AI" to "KI fragen",
                "⚙️ Actions" to "⚙️ Aktionen", "Download File" to "Datei herunterladen", "Play Audio" to "Audio abspielen"
            )
            Lang.PT -> listOf(
                "Notification" to "Notificação", "API Keys" to "Chaves API", "History" to "Histórico",
                "Settings" to "Configurações", "Copy" to "Copiar", "Share" to "Compartilhar",
                "Clear Display" to "Limpar tela", "Start New Chat" to "Iniciar novo chat",
                "Add Key" to "Adicionar chave", "Duplicate Key" to "Duplicar chave", "Close" to "Fechar",
                "Cancel" to "Cancelar", "Send Content" to "Enviar conteúdo", "Ask AI" to "Perguntar à IA",
                "⚙️ Actions" to "⚙️ Ações", "Download File" to "Baixar arquivo", "Play Audio" to "Reproduzir áudio"
            )
            Lang.ZH -> listOf(
                "Notification" to "通知", "API Keys" to "API 密钥", "History" to "历史记录",
                "Settings" to "设置", "Copy" to "复制", "Share" to "分享",
                "Clear Display" to "清除显示", "Start New Chat" to "开始新对话",
                "Add Key" to "添加密钥", "Duplicate Key" to "复制密钥", "Close" to "关闭",
                "Cancel" to "取消", "Send Content" to "发送内容", "Ask AI" to "向 AI 提问",
                "⚙️ Actions" to "⚙️ 操作", "Download File" to "下载文件", "Play Audio" to "播放音频"
            )
            Lang.JA -> listOf(
                "Notification" to "通知", "API Keys" to "APIキー", "History" to "履歴",
                "Settings" to "設定", "Copy" to "コピー", "Share" to "共有",
                "Clear Display" to "表示をクリア", "Start New Chat" to "新しいチャット",
                "Add Key" to "キーを追加", "Duplicate Key" to "キーを複製", "Close" to "閉じる",
                "Cancel" to "キャンセル", "Send Content" to "送信", "Ask AI" to "AIに質問",
                "⚙️ Actions" to "⚙️ アクション", "Download File" to "ファイルを保存", "Play Audio" to "音声を再生"
            )
            Lang.TR -> listOf(
                "Notification" to "Bildirim", "API Keys" to "API Anahtarları", "History" to "Geçmiş",
                "Settings" to "Ayarlar", "Copy" to "Kopyala", "Share" to "Paylaş",
                "Clear Display" to "Ekranı Temizle", "Start New Chat" to "Yeni Sohbet Başlat",
                "Add Key" to "Anahtar Ekle", "Duplicate Key" to "Anahtarı Çoğalt", "Close" to "Kapat",
                "Cancel" to "İptal", "Send Content" to "İçerik Gönder", "Ask AI" to "Yapay Zekaya Sor",
                "⚙️ Actions" to "⚙️ Eylemler", "Download File" to "Dosyayı İndir", "Play Audio" to "Sesi Çal"
            )
            Lang.IT -> listOf(
                "Notification" to "Notifica", "API Keys" to "Chiavi API", "History" to "Cronologia",
                "Settings" to "Impostazioni", "Copy" to "Copia", "Share" to "Condividi",
                "Clear Display" to "Cancella display", "Start New Chat" to "Inizia nuova chat",
                "Add Key" to "Aggiungi chiave", "Duplicate Key" to "Duplica chiave", "Close" to "Chiudi",
                "Cancel" to "Annulla", "Send Content" to "Invia contenuto", "Ask AI" to "Chiedi all'IA",
                "⚙️ Actions" to "⚙️ Azioni", "Download File" to "Scarica file", "Play Audio" to "Riproduci audio"
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
        get() = tr("התראה")

    val tabKeys: String
        get() = tr("מפתחות")

    val tabHistory: String
        get() = tr("היסטוריה")

    val tabSettings: String
        get() = tr("הגדרות")

    val notifPermissionTitle: String
        get() = tr(
            he = "🔔 למה נדרשת הרשאת התראות?",
            en = "🔔 Why is Notification Permission Needed?",
            ar = "🔔 لماذا مطلوب إذن الإشعارات؟",
            ru = "🔔 Зачем нужно разрешение на уведомления?",
            es = "🔔 ¿Por qué se necesita permiso de notificaciones?",
            fr = "🔔 Pourquoi l'autorisation de notification est-elle requise ?",
            de = "🔔 Warum ist die Benachrichtigungsberechtigung erforderlich?",
            pt = "🔔 Por que a permissão de notificação é necessária?",
            zh = "🔔 为什么需要通知权限？",
            ja = "🔔 なぜ通知権限が必要ですか？",
            tr = "🔔 Bildirim İzni Neden Gereklidir?",
            it = "🔔 Perché è necessaria l'autorizzazione di notifica?"
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
        get() = tr("ביטול")
}
