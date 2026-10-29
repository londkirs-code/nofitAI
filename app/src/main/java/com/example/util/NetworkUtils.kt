package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Utility to check active internet connectivity before making API calls or network requests.
 * Prevents unnecessary timeouts, retries, and battery drain when offline.
 */
object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val netInfo = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                netInfo != null && netInfo.isConnected
            }
        } catch (_: Exception) {
            true // In case of permission or OEM glitch, don't hard-block
        }
    }

    fun getNoInternetErrorMessage(): String {
        return AppStrings.tr(
            he = "📡 אין חיבור פעיל לאינטרנט. אנא בדוק את חיבור ה-Wi-Fi או הנתונים הסלולריים ונסה שוב.",
            en = "📡 No active internet connection. Please check your Wi-Fi or mobile data and try again.",
            ar = "📡 لا يوجد اتصال نشط بالإنترنت. يرجى التحقق من شبكة Wi-Fi أو بيانات الهاتف والمحاولة مرة أخرى.",
            ru = "📡 Нет активного подключения к Интернету. Проверьте Wi-Fi или мобильные данные и повторите попытку.",
            es = "📡 Sin conexión a Internet activa. Por favor, comprueba tu Wi-Fi o datos móviles e inténtalo de nuevo.",
            fr = "📡 Aucune connexion Internet active. Veuillez vérifier votre Wi-Fi ou vos données mobiles et réessayer.",
            de = "📡 Keine aktive Internetverbindung. Bitte überprüfen Sie Ihr WLAN oder mobile Daten und versuchen Sie es erneut.",
            pt = "📡 Sem conexão com a Internet ativa. Verifique seu Wi-Fi ou dados móveis e tente novamente.",
            zh = "📡 没有可用的互联网连接。请检查您的 Wi-Fi 或移动数据后重试。",
            ja = "📡 アクティブなインターネット接続がありません。Wi-Fiまたはモバイルデータを確認して再試行してください。",
            tr = "📡 Etkin internet bağlantısı yok. Lütfen Wi-Fi veya mobil verinizi kontrol edip tekrar deneyin.",
            it = "📡 Nessuna connessione Internet attiva. Controlla il Wi-Fi o i dati mobili e riprova."
        )
    }
}
