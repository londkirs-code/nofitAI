package com.example.data.remote

import com.example.data.model.ApiKeyEntity
import com.example.data.model.ProviderType
import com.example.util.AppStrings
import okhttp3.Headers
import org.json.JSONObject
import java.util.regex.Pattern

enum class ApiErrorCategory {
    SERVER_OVERLOAD,
    SERVER_ERROR,
    MODEL_UNAVAILABLE,
    ACCOUNT_TIER_MISMATCH,
    MODEL_QUOTA_EXHAUSTED,
    KEY_QUOTA_EXHAUSTED,
    INVALID_API_KEY,
    NETWORK_ERROR,
    GENERAL_ERROR;

    val badgeHe: String
        get() = when (this) {
            SERVER_OVERLOAD -> AppStrings.tr(
                he = "עומס בשרת",
                en = "Server Overload",
                ar = "ضغط على الخادم",
                ru = "Перегрузка сервера",
                es = "Servidor sobrecargado",
                fr = "Surcharge du serveur",
                de = "Server überlastet",
                pt = "Sobrecarga no servidor",
                zh = "服务器过载",
                ja = "サーバー過負荷",
                tr = "Sunucu Yoğunluğu",
                it = "Sovraccarico del server"
            )
            SERVER_ERROR -> AppStrings.tr(
                he = "תקלת שרת",
                en = "Server Error",
                ar = "خطأ في الخادم",
                ru = "Ошибка сервера",
                es = "Error del servidor",
                fr = "Erreur du serveur",
                de = "Serverfehler",
                pt = "Erro no servidor",
                zh = "服务器错误",
                ja = "サーバーエラー",
                tr = "Sunucu Hatası",
                it = "Errore del server"
            )
            MODEL_UNAVAILABLE -> AppStrings.tr(
                he = "מודל לא זמין / לא קיים",
                en = "Model Unavailable / Not Found",
                ar = "النموذج غير متاح / غير موجود",
                ru = "Модель недоступна / не найдена",
                es = "Modelo no disponible / inexistente",
                fr = "Modèle indisponible / introuvable",
                de = "Modell nicht verfügbar / existiert nicht",
                pt = "Modelo indisponível / não encontrado",
                zh = "模型不可用 / 不存在",
                ja = "モデル利用不可 / 存在しません",
                tr = "Model Kullanılamıyor / Bulunamadı",
                it = "Modello non disponibile / inesistente"
            )
            ACCOUNT_TIER_MISMATCH -> AppStrings.tr(
                he = "לא מתאים לסוג החשבון",
                en = "Account Tier Mismatch",
                ar = "غير متوافق مع نوع الحساب",
                ru = "Не подходит для тарифа аккаунта",
                es = "No compatible con tu tipo de cuenta",
                fr = "Incompatible avec le niveau du compte",
                de = "Nicht für diesen Kontotyp verfügbar",
                pt = "Incompatível com o nível da conta",
                zh = "账户等级不匹配",
                ja = "アカウントプラン非対応",
                tr = "Hesap Planı Uyuşmazlığı",
                it = "Non compatibile con il livello dell'account"
            )
            MODEL_QUOTA_EXHAUSTED -> AppStrings.tr(
                he = "נגמרו הטוקנים למודל הספציפי",
                en = "Model Quota Exhausted",
                ar = "انتهت حصة الرموز لهذا النموذج",
                ru = "Исчерпан лимит токенов для модели",
                es = "Cuota de tokens agotada para este modelo",
                fr = "Quota de jetons épuisé pour ce modèle",
                de = "Token-Limit für dieses Modell erschöpft",
                pt = "Cota de tokens esgotada para este modelo",
                zh = "当前模型配额已用尽",
                ja = "特定モデルのトークン上限到達",
                tr = "Modele Özel Kota Doldu",
                it = "Quota token esaurita per questo modello"
            )
            KEY_QUOTA_EXHAUSTED -> AppStrings.tr(
                he = "נגמרו הטוקנים לכל המפתח",
                en = "Entire API Key Quota Exhausted",
                ar = "انتهت حصة الرموز للمفتاح بالكامل",
                ru = "Исчерпан лимит токенов для всего ключа",
                es = "Cuota de tokens agotada para toda la clave",
                fr = "Quota de jetons épuisé pour toute la clé",
                de = "Token-Limit für den gesamten API-Schlüssel erschöpft",
                pt = "Cota de tokens esgotada para toda a chave",
                zh = "整个API密钥配额已用尽",
                ja = "APIキー全体のトークン上限到達",
                tr = "Tüm Anahtar Kotası Doldu",
                it = "Quota token esaurita per l'intera chiave API"
            )
            INVALID_API_KEY -> AppStrings.tr(
                he = "מפתח API שגוי",
                en = "Invalid API Key",
                ar = "مفتاح API غير صالح",
                ru = "Неверный ключ API",
                es = "Clave API inválida",
                fr = "Clé API invalide",
                de = "Ungültiger API-Schlüssel",
                pt = "Chave de API inválida",
                zh = "API密钥无效",
                ja = "無効なAPIキー",
                tr = "Geçersiz API Anahtarı",
                it = "Chiave API non valida"
            )
            NETWORK_ERROR -> AppStrings.tr(
                he = "בעיית חיבור רשת",
                en = "Network Connection Error",
                ar = "مشكلة في الاتصال بالشبكة",
                ru = "Ошибка подключения к сети",
                es = "Error de conexión de red",
                fr = "Erreur de connexion réseau",
                de = "Netzwerkverbindungsfehler",
                pt = "Erro de conexão de rede",
                zh = "网络连接错误",
                ja = "ネットワーク接続エラー",
                tr = "Ağ Bağlantı Hatası",
                it = "Errore di connessione di rete"
            )
            GENERAL_ERROR -> AppStrings.tr(
                he = "שגיאת API",
                en = "API Error",
                ar = "خطأ في API",
                ru = "Ошибка API",
                es = "Error de API",
                fr = "Erreur API",
                de = "API-Fehler",
                pt = "Erro de API",
                zh = "API错误",
                ja = "APIエラー",
                tr = "API Hatası",
                it = "Errore API"
            )
        }
}

enum class BlockScope {
    MODEL_ONLY,
    ENTIRE_KEY,
    NONE;

    val labelHe: String
        get() = when (this) {
            MODEL_ONLY -> AppStrings.tr(
                he = "מודל ספציפי בלבד (שאר המודלים במפתח פעילים)",
                en = "Specific Model Only (other models in key are active)",
                ar = "نموذج محدد فقط (باقي النماذج في المفتاح نشطة)",
                ru = "Только эта модель (остальные модели ключа активны)",
                es = "Solo este modelo (los demás modelos de la clave están activos)",
                fr = "Modèle spécifique uniquement (les autres modèles de la clé sont actifs)",
                de = "Nur dieses Modell (andere Modelle im Schlüssel sind aktiv)",
                pt = "Apenas este modelo (outros modelos da chave estão ativos)",
                zh = "仅限该特定模型（密钥中的其他模型仍可用）",
                ja = "特定モデルのみ（キー内の他モデルは利用可能）",
                tr = "Yalnızca Belirli Model (anahtardaki diğer modeller aktif)",
                it = "Solo modello specifico (gli altri modelli nella chiave sono attivi)"
            )
            ENTIRE_KEY -> AppStrings.tr(
                he = "כל המפתח / ה-API",
                en = "Entire API Key",
                ar = "المفتاح بالكامل / API",
                ru = "Весь ключ / API",
                es = "Toda la clave / API",
                fr = "Toute la clé / API",
                de = "Gesamter Schlüssel / API",
                pt = "Toda a chave / API",
                zh = "整个API密钥",
                ja = "APIキー全体",
                tr = "Tüm Anahtar / API",
                it = "Intera chiave / API"
            )
            NONE -> AppStrings.tr(
                he = "ללא חסימה",
                en = "Not Blocked",
                ar = "بدون حظر",
                ru = "Без блокировки",
                es = "Sin bloqueo",
                fr = "Aucun blocage",
                de = "Keine Sperre",
                pt = "Sem bloqueio",
                zh = "未封锁",
                ja = "ブロックなし",
                tr = "Engelleme Yok",
                it = "Nessun blocco"
            )
        }
}

data class AnalyzedApiError(
    val category: ApiErrorCategory,
    val scope: BlockScope,
    val blockDurationSeconds: Long,
    val hebrewSummary: String,
    val rawDetail: String
)

object ApiErrorAnalyzer {

    fun analyzeHttpError(
        code: Int,
        headers: Headers?,
        responseBody: String,
        provider: ProviderType,
        model: String
    ): AnalyzedApiError {
        val rawMsg = extractErrorMessage(responseBody).orEmpty()
        val combined = "$rawMsg $responseBody".lowercase()
        val retryAfterSec = parseRetryAfterSeconds(headers, responseBody)

        // 1. Check Invalid Key (401 or explicit invalid key)
        if (code == 401 || combined.contains("invalid_api_key") || combined.contains("api_key_invalid") ||
            combined.contains("incorrect api key") || combined.contains("unauthorized")
        ) {
            val summary = AppStrings.tr(
                he = "🔑 מפתח לא תקין [כל ה-API במפתח]: המפתח עבור ${provider.displayName} שגוי או בוטל.",
                en = "🔑 Invalid API Key [Entire Key]: The key for ${provider.displayName} is incorrect or revoked.",
                ar = "🔑 مفتاح API غير صالح [المفتاح بالكامل]: المفتاح الخاص بـ ${provider.displayName} غير صحيح أو ملغى.",
                ru = "🔑 Неверный ключ API [Весь ключ]: ключ для ${provider.displayName} недействителен или отозван.",
                es = "🔑 Clave API inválida [Toda la clave]: la clave para ${provider.displayName} es incorrecta o fue revocada.",
                fr = "🔑 Clé API invalide [Toute la clé] : la clé pour ${provider.displayName} est incorrecte ou révoquée.",
                de = "🔑 Ungültiger API-Schlüssel [Gesamter Schlüssel]: Der Schlüssel für ${provider.displayName} ist falsch oder widerrufen.",
                pt = "🔑 Chave de API inválida [Toda a chave]: A chave para ${provider.displayName} está incorreta ou foi revogada.",
                zh = "🔑 API密钥无效 [整个密钥]：${provider.displayName} 的密钥不正确或已被撤销。",
                ja = "🔑 無効なAPIキー [キー全体]: ${provider.displayName} のキーが正しくないか無効化されています。",
                tr = "🔑 Geçersiz API Anahtarı [Tüm Anahtar]: ${provider.displayName} anahtarı hatalı veya iptal edilmiş.",
                it = "🔑 Chiave API non valida [Intera chiave]: la chiave per ${provider.displayName} è errata o revocata."
            )
            return AnalyzedApiError(
                category = ApiErrorCategory.INVALID_API_KEY,
                scope = BlockScope.ENTIRE_KEY,
                blockDurationSeconds = 3600L,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 2. Check Model Not Found / Decommissioned / Unavailable (404 or explicit message)
        if (code == 404 || combined.contains("model_not_found") || combined.contains("does not exist") ||
            combined.contains("decommissioned") || combined.contains("not found for api version") ||
            combined.contains("no endpoints found") || combined.contains("model is not supported")
        ) {
            val summary = AppStrings.tr(
                he = "🚫 מודל לא קיים או לא זמין [מודל '$model' בלבד]: המודל אינו קיים ב-${provider.displayName} או הוצא משימוש. שאר המודלים במפתח זמינים.",
                en = "🚫 Model Unavailable or Not Found [Model '$model' only]: This model does not exist on ${provider.displayName} or was deprecated. Other models in this key remain active.",
                ar = "🚫 النموذج غير موجود أو غير متاح [النموذج '$model' فقط]: النموذج غير متوفر في ${provider.displayName}. باقي النماذج في المفتاح متاحة.",
                ru = "🚫 Модель не найдена или недоступна [Только модель '$model']: модель отсутствует в ${provider.displayName} или устарела. Остальные модели ключа доступны.",
                es = "🚫 Modelo inexistente o no disponible [Solo modelo '$model']: el modelo no existe en ${provider.displayName} o fue retirado. Los demás modelos de la clave están activos.",
                fr = "🚫 Modèle introuvable ou indisponible [Modèle '$model' uniquement] : ce modèle n'existe pas sur ${provider.displayName} ou est obsolète. Les autres modèles restent actifs.",
                de = "🚫 Modell nicht verfügbar oder existiert nicht [Nur Modell '$model']: Dieses Modell existiert bei ${provider.displayName} nicht oder wurde eingestellt. Andere Modelle im Schlüssel sind aktiv.",
                pt = "🚫 Modelo inexistente ou indisponível [Apenas modelo '$model']: O modelo não existe no ${provider.displayName} ou foi descontinuado. Os outros modelos da chave estão ativos.",
                zh = "🚫 模型不存在或不可用 [仅限模型 '$model']：该模型在 ${provider.displayName} 中不存在或已停用。密钥中的其他模型仍可用。",
                ja = "🚫 モデルが存在しないか利用不可 [モデル '$model' のみ]: ${provider.displayName} でこのモデルが見つからないか廃止されています。キー内の他モデルは利用可能です。",
                tr = "🚫 Model Mevcut Değil veya Kullanılamıyor [Yalnızca '$model' modeli]: Model ${provider.displayName} üzerinde bulunamadı veya kaldırıldı. Anahtardaki diğer modeller kullanılabilir.",
                it = "🚫 Modello inesistente o non disponibile [Solo modello '$model']: il modello non esiste su ${provider.displayName} o è stato ritirato. Gli altri modelli nella chiave sono disponibili."
            )
            return AnalyzedApiError(
                category = ApiErrorCategory.MODEL_UNAVAILABLE,
                scope = BlockScope.MODEL_ONLY,
                blockDurationSeconds = 86400L, // Block just this broken model ID for 24h
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 3. Check Account Tier / Billing / Permission Mismatch (402, 403, or tier/credits errors)
        val isBillingEmpty = code == 402 || combined.contains("insufficient_balance") ||
                combined.contains("credit balance is too low") || combined.contains("insufficient credits") ||
                combined.contains("billing_hard_limit")
        if (isBillingEmpty) {
            val duration = if (retryAfterSec > 0) retryAfterSec else 7200L
            val timeStr = ApiKeyEntity.formatDurationHe(duration)
            val summary = AppStrings.tr(
                he = "💳 נגמרה יתרת הטוקנים בחשבון [כל ה-API במפתח]: נגמרו הקרדיטים/הטוקנים לכל המפתח ב-${provider.displayName} (חסום ל-$timeStr).",
                en = "💳 Account Token Balance Exhausted [Entire API Key]: Credits/tokens depleted for the entire key on ${provider.displayName} (blocked for $timeStr).",
                ar = "💳 نفد رصيد الرموز في الحساب [المفتاح بالكامل]: انتهت الأرصدة/الرموز للمفتاح بالكامل في ${provider.displayName} (محظور لمدة $timeStr).",
                ru = "💳 Исчерпан баланс токенов аккаунта [Весь ключ API]: закончились кредиты/токены для всего ключа в ${provider.displayName} (блокировка на $timeStr).",
                es = "💳 Saldo de tokens agotado en la cuenta [Toda la clave API]: se agotaron los créditos/tokens de toda la clave en ${provider.displayName} (bloqueado por $timeStr).",
                fr = "💳 Solde de jetons épuisé sur le compte [Toute la clé API] : crédits/jetons épuisés pour toute la clé sur ${provider.displayName} (bloqué pendant $timeStr).",
                de = "💳 Token-Guthaben des Kontos erschöpft [Gesamter API-Schlüssel]: Guthaben/Tokens für den gesamten Schlüssel bei ${provider.displayName} aufgebraucht (gesperrt für $timeStr).",
                pt = "💳 Saldo de tokens da conta esgotado [Toda a chave API]: Créditos/tokens esgotados para toda a chave em ${provider.displayName} (bloqueado por $timeStr).",
                zh = "💳 账户代币余额耗尽 [整个API密钥]：${provider.displayName} 整个密钥的额度/代币已用完（封锁 $timeStr）。",
                ja = "💳 アカウントのトークン残高不足 [APIキー全体]: ${provider.displayName} のキー全体のクレジット/トークンが尽きました（$timeStr ブロック）。",
                tr = "💳 Hesap Token Bakiyesi Bitti [Tüm API Anahtarı]: ${provider.displayName} üzerindeki tüm anahtar için kredi/token tükendi ($timeStr süreyle engellendi).",
                it = "💳 Saldo token dell'account esaurito [Intera chiave API]: crediti/token esauriti per l'intera chiave su ${provider.displayName} (bloccato per $timeStr)."
            )
            return AnalyzedApiError(
                category = ApiErrorCategory.KEY_QUOTA_EXHAUSTED,
                scope = BlockScope.ENTIRE_KEY,
                blockDurationSeconds = duration,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        val isAccountTierIssue = code == 403 || combined.contains("free tier") ||
                combined.contains("not available on your tier") || combined.contains("requires a paid") ||
                combined.contains("access to model") || combined.contains("permission_denied") ||
                combined.contains("must be verified")
        if (isAccountTierIssue) {
            val summary = AppStrings.tr(
                he = "🔒 מודל לא מתאים לסוג החשבון [מודל '$model' בלבד]: המודל דורש חשבון משודרג או הרשאה מיוחדת ב-${provider.displayName}. שאר המודלים במפתח פעילים.",
                en = "🔒 Account Tier Mismatch [Model '$model' only]: This model requires an upgraded plan or special permission on ${provider.displayName}. Other models in this key are active.",
                ar = "🔒 النموذج غير متوافق مع نوع الحساب [النموذج '$model' فقط]: يتطلب هذا النموذج حساباً مدفوعاً أو إذناً خاصاً في ${provider.displayName}. باقي النماذج نشطة.",
                ru = "🔒 Несоответствие тарифу аккаунта [Только модель '$model']: модель требует платного тарифа или особого доступа в ${provider.displayName}. Остальные модели активны.",
                es = "🔒 Modelo no compatible con el nivel de cuenta [Solo modelo '$model']: requiere un plan superior o permiso especial en ${provider.displayName}. Los demás modelos están activos.",
                fr = "🔒 Modèle incompatible avec le niveau du compte [Modèle '$model' uniquement] : nécessite un compte supérieur ou une autorisation sur ${provider.displayName}. Les autres modèles sont actifs.",
                de = "🔒 Modell passt nicht zum Kontotyp [Nur Modell '$model']: Erfordert ein Upgrade oder eine spezielle Berechtigung bei ${provider.displayName}. Andere Modelle sind aktiv.",
                pt = "🔒 Modelo incompatível com o tipo de conta [Apenas modelo '$model']: Requer conta atualizada ou permissão especial no ${provider.displayName}. Outros modelos estão ativos.",
                zh = "🔒 模型与账户等级不匹配 [仅限模型 '$model']：该模型在 ${provider.displayName} 需要升级账户或特殊权限。密钥中的其他模型仍有效。",
                ja = "🔒 アカウントプラン非対応 [モデル '$model' のみ]: ${provider.displayName} で上位プランまたは特別な権限が必要です。他のモデルは有効です。",
                tr = "🔒 Model Hesap Türüne Uygun Değil [Yalnızca '$model' modeli]: ${provider.displayName} üzerinde yükseltilmiş hesap veya özel izin gerektirir. Diğer modeller aktif.",
                it = "🔒 Modello non compatibile con il tipo di account [Solo modello '$model']: richiede un piano superiore o permessi speciali su ${provider.displayName}. Gli altri modelli sono attivi."
            )
            return AnalyzedApiError(
                category = ApiErrorCategory.ACCOUNT_TIER_MISMATCH,
                scope = BlockScope.MODEL_ONLY,
                blockDurationSeconds = 3600L,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 4. Server Overload (529, 503, overloaded) vs Server Error (500, 502, 504)
        if (code == 529 || code == 503 || combined.contains("overloaded") || combined.contains("capacity")) {
            val duration = if (retryAfterSec > 0) retryAfterSec else 5L
            val timeStr = ApiKeyEntity.formatDurationHe(duration)
            // Often overload is on the specific model (especially on OpenRouter / Gemini / Claude)
            val isModelSpecificOverload = combined.contains("model") || provider == ProviderType.OPENROUTER || provider == ProviderType.GEMINI
            val scope = if (isModelSpecificOverload) BlockScope.MODEL_ONLY else BlockScope.ENTIRE_KEY
            val summary = if (scope == BlockScope.MODEL_ONLY) {
                AppStrings.tr(
                    he = "⏳ עומס בשרת [מודל '$model' בלבד]: שרת ${provider.displayName} עמוס כעת. חסום זמנית ל-$timeStr.",
                    en = "⏳ Server Overload [Model '$model' only]: ${provider.displayName} server is currently overloaded. Temporarily blocked for $timeStr.",
                    ar = "⏳ ضغط على الخادم [النموذج '$model' فقط]: خادم ${provider.displayName} مزدحم حالياً. محظور مؤقتاً لمدة $timeStr.",
                    ru = "⏳ Перегрузка сервера [Только модель '$model']: сервер ${provider.displayName} перегружен. Временно заблокировано на $timeStr.",
                    es = "⏳ Sobrecarga del servidor [Solo modelo '$model']: el servidor de ${provider.displayName} está saturado. Bloqueado temporalmente por $timeStr.",
                    fr = "⏳ Surcharge du serveur [Modèle '$model' uniquement] : le serveur ${provider.displayName} est surchargé. Bloqué temporairement pendant $timeStr.",
                    de = "⏳ Server überlastet [Nur Modell '$model']: Der ${provider.displayName}-Server ist derzeit ausgelastet. Vorübergehend gesperrt für $timeStr.",
                    pt = "⏳ Sobrecarga no servidor [Apenas modelo '$model']: O servidor ${provider.displayName} está sobrecarregado. Bloqueado temporariamente por $timeStr.",
                    zh = "⏳ 服务器过载 [仅限模型 '$model']：${provider.displayName} 服务器当前繁忙。暂时封锁 $timeStr。",
                    ja = "⏳ サーバー過負荷 [モデル '$model' のみ]: ${provider.displayName} サーバーが混雑しています。$timeStr 一時ブロックされます。",
                    tr = "⏳ Sunucu Yoğunluğu [Yalnızca '$model' modeli]: ${provider.displayName} sunucusu şu anda yoğun. $timeStr süreyle geçici olarak engellendi.",
                    it = "⏳ Sovraccarico del server [Solo modello '$model']: il server ${provider.displayName} è attualmente sovraccarico. Bloccato temporaneamente per $timeStr."
                )
            } else {
                AppStrings.tr(
                    he = "⏳ עומס בשרת [כל ה-API במפתח]: שרת ${provider.displayName} עמוס כעת. חסום זמנית ל-$timeStr.",
                    en = "⏳ Server Overload [Entire API Key]: ${provider.displayName} server is currently overloaded. Temporarily blocked for $timeStr.",
                    ar = "⏳ ضغط على الخادم [المفتاح بالكامل]: خادم ${provider.displayName} مزدحم حالياً. محظور مؤقتاً لمدة $timeStr.",
                    ru = "⏳ Перегрузка сервера [Весь ключ API]: сервер ${provider.displayName} перегружен. Временно заблокировано на $timeStr.",
                    es = "⏳ Sobrecarga del servidor [Toda la clave API]: el servidor de ${provider.displayName} está saturado. Bloqueado temporalmente por $timeStr.",
                    fr = "⏳ Surcharge du serveur [Toute la clé API] : le serveur ${provider.displayName} est surchargé. Bloqué temporairement pendant $timeStr.",
                    de = "⏳ Server überlastet [Gesamter API-Schlüssel]: Der ${provider.displayName}-Server ist derzeit ausgelastet. Vorübergehend gesperrt für $timeStr.",
                    pt = "⏳ Sobrecarga no servidor [Toda a chave API]: O servidor ${provider.displayName} está sobrecarregado. Bloqueado temporariamente por $timeStr.",
                    zh = "⏳ 服务器过载 [整个API密钥]：${provider.displayName} 服务器当前繁忙。暂时封锁 $timeStr。",
                    ja = "⏳ サーバー過負荷 [APIキー全体]: ${provider.displayName} サーバーが混雑しています。$timeStr 一時ブロックされます。",
                    tr = "⏳ Sunucu Yoğunluğu [Tüm API Anahtarı]: ${provider.displayName} sunucusu şu anda yoğun. $timeStr süreyle geçici olarak engellendi.",
                    it = "⏳ Sovraccarico del server [Intera chiave API]: il server ${provider.displayName} è attualmente sovraccarico. Bloccato temporaneamente per $timeStr."
                )
            }
            return AnalyzedApiError(
                category = ApiErrorCategory.SERVER_OVERLOAD,
                scope = scope,
                blockDurationSeconds = duration,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        if (code in 500..599) {
            val duration = if (retryAfterSec > 0) retryAfterSec else 5L
            val timeStr = ApiKeyEntity.formatDurationHe(duration)
            val summary = AppStrings.tr(
                he = "🛠️ תקלת שרת ($code) [כל ה-API במפתח]: שרת ${provider.displayName} החזיר שגיאה פנימית. מושהה ל-$timeStr.",
                en = "🛠️ Server Error ($code) [Entire API Key]: ${provider.displayName} returned an internal server error. Paused for $timeStr.",
                ar = "🛠️ عطل في الخادم ($code) [المفتاح بالكامل]: أعاد خادم ${provider.displayName} خطأ داخلياً. متوقف مؤقتاً لمدة $timeStr.",
                ru = "🛠️ Ошибка сервера ($code) [Весь ключ API]: сервер ${provider.displayName} вернул внутреннюю ошибку. Приостановлено на $timeStr.",
                es = "🛠️ Error del servidor ($code) [Toda la clave API]: el servidor de ${provider.displayName} devolvió un error interno. Pausado por $timeStr.",
                fr = "🛠️ Erreur du serveur ($code) [Toute la clé API] : le serveur ${provider.displayName} a renvoyé une erreur interne. Mis en pause pendant $timeStr.",
                de = "🛠️ Serverfehler ($code) [Gesamter API-Schlüssel]: Der ${provider.displayName}-Server hat einen internen Fehler gemeldet. Pausiert für $timeStr.",
                pt = "🛠️ Erro no servidor ($code) [Toda a chave API]: O servidor ${provider.displayName} retornou um erro interno. Pausado por $timeStr.",
                zh = "🛠️ 服务器故障 ($code) [整个API密钥]：${provider.displayName} 服务器返回内部错误。暂停 $timeStr。",
                ja = "🛠️ サーバー障害 ($code) [APIキー全体]: ${provider.displayName} サーバーが内部エラーを返しました。$timeStr 一時停止します。",
                tr = "🛠️ Sunucu Hatası ($code) [Tüm API Anahtarı]: ${provider.displayName} sunucusu dahili bir hata döndürdü. $timeStr süreyle duraklatıldı.",
                it = "🛠️ Errore del server ($code) [Intera chiave API]: il server ${provider.displayName} ha restituito un errore interno. In pausa per $timeStr."
            )
            return AnalyzedApiError(
                category = ApiErrorCategory.SERVER_ERROR,
                scope = BlockScope.ENTIRE_KEY,
                blockDurationSeconds = duration,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 5. Rate Limit / Quota Exhausted (429 or RESOURCE_EXHAUSTED)
        // Distinguish between SPECIFIC MODEL quota/rate-limit vs ENTIRE API KEY quota!
        if (code == 429 || combined.contains("rate_limit") || combined.contains("resource_exhausted") ||
            combined.contains("quota") || combined.contains("too many requests")
        ) {
            val mentionsModelSpecifically = combined.contains(model.lowercase()) ||
                    combined.contains("per-model") ||
                    combined.contains("for model") ||
                    combined.contains("limit for this model") ||
                    combined.contains("requests per minute (rpm) for") ||
                    combined.contains("tokens per minute (tpm) for") ||
                    provider == ProviderType.GROQ ||
                    provider == ProviderType.OPENROUTER ||
                    provider == ProviderType.GEMINI

            val mentionsAccountWideQuota = combined.contains("exceeded your current quota, please check your plan and billing") ||
                    combined.contains("account_quota") ||
                    combined.contains("monthly limit") ||
                    combined.contains("organization quota") ||
                    combined.contains("daily key limit")

            if (mentionsAccountWideQuota && !mentionsModelSpecifically) {
                val duration = if (retryAfterSec > 0) retryAfterSec else 1800L
                val timeStr = ApiKeyEntity.formatDurationHe(duration)
                val summary = AppStrings.tr(
                    he = "🛑 נגמרו הטוקנים למפתח [כל ה-API במפתח]: מכסת המפתח ב-${provider.displayName} נוצלה במלואה. חסום ל-$timeStr.",
                    en = "🛑 Key Quota Exhausted [Entire API Key]: The quota for this key on ${provider.displayName} is fully used. Blocked for $timeStr.",
                    ar = "🛑 انتهت حصة الرموز للمفتاح [المفتاح بالكامل]: تم استهلاك حصة المفتاح بالكامل في ${provider.displayName}. محظور لمدة $timeStr.",
                    ru = "🛑 Исчерпан лимит токенов для ключа [Весь ключ API]: квота ключа в ${provider.displayName} полностью израсходована. Заблокировано на $timeStr.",
                    es = "🛑 Cuota de tokens agotada para la clave [Toda la clave API]: se agotó la cuota de la clave en ${provider.displayName}. Bloqueado por $timeStr.",
                    fr = "🛑 Quota de jetons épuisé pour la clé [Toute la clé API] : le quota de la clé sur ${provider.displayName} est entièrement utilisé. Bloqué pendant $timeStr.",
                    de = "🛑 Token-Limit für Schlüssel erschöpft [Gesamter API-Schlüssel]: Das Kontingent für diesen Schlüssel bei ${provider.displayName} ist aufgebraucht. Gesperrt für $timeStr.",
                    pt = "🛑 Cota de tokens da chave esgotada [Toda a chave API]: A cota da chave em ${provider.displayName} foi totalmente utilizada. Bloqueado por $timeStr.",
                    zh = "🛑 密钥代币配额已用尽 [整个API密钥]：${provider.displayName} 的密钥配额已全部用完。封锁 $timeStr。",
                    ja = "🛑 キーのトークン上限到達 [APIキー全体]: ${provider.displayName} でのキーのクォータが使い果たされました。$timeStr ブロックされます。",
                    tr = "🛑 Anahtar Token Kotası Doldu [Tüm API Anahtarı]: ${provider.displayName} üzerindeki anahtar kotası tamamen kullanıldı. $timeStr süreyle engellendi.",
                    it = "🛑 Quota token esaurita per la chiave [Intera chiave API]: la quota della chiave su ${provider.displayName} è stata interamente utilizzata. Bloccato per $timeStr."
                )
                return AnalyzedApiError(
                    category = ApiErrorCategory.KEY_QUOTA_EXHAUSTED,
                    scope = BlockScope.ENTIRE_KEY,
                    blockDurationSeconds = duration,
                    hebrewSummary = summary,
                    rawDetail = rawMsg
                )
            } else {
                val duration = if (retryAfterSec > 0) retryAfterSec else 60L
                val timeStr = ApiKeyEntity.formatDurationHe(duration)
                val summary = AppStrings.tr(
                    he = "⚡ נגמרו הטוקנים למודל הספציפי [מודל '$model' בלבד]: המכסה הזמנית של המודל '$model' ב-${provider.displayName} נוצלה (חסום ל-$timeStr). שאר המודלים במפתח זמינים!",
                    en = "⚡ Specific Model Quota Exhausted [Model '$model' only]: Temporary quota for model '$model' on ${provider.displayName} is exhausted (blocked for $timeStr). Other models in this key remain available!",
                    ar = "⚡ انتهت حصة الرموز للنموذج المحدد [النموذج '$model' فقط]: تم استهلاك الحصة المؤقتة للنموذج '$model' في ${provider.displayName} (محظور لمدة $timeStr). باقي النماذج في المفتاح متاحة!",
                    ru = "⚡ Исчерпан лимит токенов для конкретной модели [Только модель '$model']: временный лимит модели '$model' в ${provider.displayName} исчерпан (заблокировано на $timeStr). Остальные модели ключа доступны!",
                    es = "⚡ Cuota de tokens agotada para el modelo específico [Solo modelo '$model']: la cuota temporal del modelo '$model' en ${provider.displayName} se agotó (bloqueado por $timeStr). ¡Los demás modelos de la clave siguen disponibles!",
                    fr = "⚡ Quota de jetons épuisé pour le modèle spécifique [Modèle '$model' uniquement] : le quota temporaire du modèle '$model' sur ${provider.displayName} est épuisé (bloqué pendant $timeStr). Les autres modèles de la clé restent disponibles !",
                    de = "⚡ Token-Limit für dieses Modell erschöpft [Nur Modell '$model']: Das temporäre Kontingent für Modell '$model' bei ${provider.displayName} ist erschöpft (gesperrt für $timeStr). Andere Modelle im Schlüssel bleiben verfügbar!",
                    pt = "⚡ Cota de tokens esgotada para o modelo específico [Apenas modelo '$model']: A cota temporária do modelo '$model' em ${provider.displayName} foi esgotada (bloqueado por $timeStr). Os outros modelos da chave continuam disponíveis!",
                    zh = "⚡ 特定模型代币配额已用尽 [仅限模型 '$model']：${provider.displayName} 中模型 '$model' 的临时配额已用完（封锁 $timeStr）。密钥中的其他模型依然可用！",
                    ja = "⚡ 特定モデルのトークン上限到達 [モデル '$model' のみ]: ${provider.displayName} のモデル '$model' の一時クォータが尽きました（$timeStr ブロック）。キー内の他モデルは引き続き利用可能です！",
                    tr = "⚡ Modele Özel Token Kotası Doldu [Yalnızca '$model' modeli]: ${provider.displayName} üzerindeki '$model' modelinin geçici kotası doldu ($timeStr süreyle engellendi). Anahtardaki diğer modeller kullanılabilir!",
                    it = "⚡ Quota token esaurita per il modello specifico [Solo modello '$model']: la quota temporanea del modello '$model' su ${provider.displayName} è esaurita (bloccato per $timeStr). Gli altri modelli nella chiave restano disponibili!"
                )
                return AnalyzedApiError(
                    category = ApiErrorCategory.MODEL_QUOTA_EXHAUSTED,
                    scope = BlockScope.MODEL_ONLY,
                    blockDurationSeconds = duration,
                    hebrewSummary = summary,
                    rawDetail = rawMsg
                )
            }
        }

        // 6. Fallback General Error
        val defaultCodeStr = AppStrings.tr(
            he = "קוד שגיאה $code",
            en = "Error code $code",
            ar = "رمز الخطأ $code",
            ru = "Код ошибки $code",
            es = "Código de error $code",
            fr = "Code d'erreur $code",
            de = "Fehlercode $code",
            pt = "Código de erro $code",
            zh = "错误代码 $code",
            ja = "エラーコード $code",
            tr = "Hata kodu $code",
            it = "Codice errore $code"
        )
        val shortDetail = rawMsg.take(120).ifBlank { defaultCodeStr }
        val summary = AppStrings.tr(
            he = "⚠️ שגיאת בקשה ב-${provider.displayName} [מודל '$model']: $shortDetail",
            en = "⚠️ Request Error on ${provider.displayName} [Model '$model']: $shortDetail",
            ar = "⚠️ خطأ في الطلب على ${provider.displayName} [النموذج '$model']: $shortDetail",
            ru = "⚠️ Ошибка запроса в ${provider.displayName} [Модель '$model']: $shortDetail",
            es = "⚠️ Error de solicitud en ${provider.displayName} [Modelo '$model']: $shortDetail",
            fr = "⚠️ Erreur de requête sur ${provider.displayName} [Modèle '$model'] : $shortDetail",
            de = "⚠️ Anfragefehler bei ${provider.displayName} [Modell '$model']: $shortDetail",
            pt = "⚠️ Erro na solicitação em ${provider.displayName} [Modelo '$model']: $shortDetail",
            zh = "⚠️ ${provider.displayName} 请求错误 [模型 '$model']：$shortDetail",
            ja = "⚠️ ${provider.displayName} リクエストエラー [モデル '$model']: $shortDetail",
            tr = "⚠️ ${provider.displayName} İstek Hatası [Model '$model']: $shortDetail",
            it = "⚠️ Errore di richiesta su ${provider.displayName} [Modello '$model']: $shortDetail"
        )
        return AnalyzedApiError(
            category = ApiErrorCategory.GENERAL_ERROR,
            scope = BlockScope.MODEL_ONLY,
            blockDurationSeconds = 15L,
            hebrewSummary = summary,
            rawDetail = rawMsg
        )
    }

    fun parseRetryAfterSeconds(headers: Headers?, body: String): Long {
        if (headers != null) {
            val retryHeader = headers["Retry-After"]
                ?: headers["x-ratelimit-reset-requests"]
                ?: headers["x-ratelimit-reset-tokens"]
            if (!retryHeader.isNullOrBlank()) {
                val parsed = parseDurationString(retryHeader)
                if (parsed > 0) return parsed
            }
        }

        // Check body for patterns like "Please try again in 2m30s", "retry in 42.5s", "retryDelay": "35s"
        val minSecPattern = Pattern.compile("(\\d+)m\\s*(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE)
        val minSecMatcher = minSecPattern.matcher(body)
        if (minSecMatcher.find()) {
            val mins = minSecMatcher.group(1)?.toLongOrNull() ?: 0L
            val secs = minSecMatcher.group(2)?.toDoubleOrNull()?.toLong() ?: 0L
            return mins * 60 + secs
        }

        val retryDelayJsonPattern = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"", Pattern.CASE_INSENSITIVE)
        val retryDelayMatcher = retryDelayJsonPattern.matcher(body)
        if (retryDelayMatcher.find()) {
            val secs = retryDelayMatcher.group(1)?.toDoubleOrNull()?.toLong() ?: 0L
            if (secs > 0) return secs
        }

        val pattern = Pattern.compile("(?:in|after)\\s+(\\d+(?:\\.\\d+)?)\\s*(milliseconds?|ms|seconds?|secs?|minutes?|mins?|hours?|hrs?|s|m|h)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            val value = matcher.group(1)?.toDoubleOrNull() ?: return 0L
            val unit = matcher.group(2)?.lowercase().orEmpty()
            return when {
                unit.startsWith("ms") || unit.startsWith("millisecond") -> maxOf(1L, (value / 1000.0).toLong())
                unit.startsWith("m") -> (value * 60).toLong()
                unit.startsWith("h") -> (value * 3600).toLong()
                else -> maxOf(1L, value.toLong())
            }
        }
        return 0L
    }

    private fun parseDurationString(raw: String): Long {
        val clean = raw.trim().lowercase()
        clean.toDoubleOrNull()?.let { return maxOf(1L, it.toLong()) }
        val minSec = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+(?:\\.\\d+)?)s)?").matcher(clean)
        if (minSec.matches()) {
            val h = minSec.group(1)?.toLongOrNull() ?: 0L
            val m = minSec.group(2)?.toLongOrNull() ?: 0L
            val s = minSec.group(3)?.toDoubleOrNull()?.toLong() ?: 0L
            val total = h * 3600 + m * 60 + s
            if (total > 0) return total
        }
        return 0L
    }

    fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorObj = json.get("error")
                if (errorObj is JSONObject) {
                    val mainMsg = errorObj.optString("message", "")
                    val metadata = errorObj.optJSONObject("metadata")
                    val rawMeta = metadata?.optString("raw", "").orEmpty()
                    if (rawMeta.isNotBlank() && !mainMsg.contains(rawMeta)) {
                        "$mainMsg ($rawMeta)"
                    } else {
                        mainMsg.ifBlank { errorObj.optString("code", "") }.ifBlank { null }
                    }
                } else {
                    errorObj.toString()
                }
            } else {
                json.optString("message", "").ifBlank { null }
            }
        } catch (_: Exception) {
            body.take(200)
        }
    }
}
