package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.AiRepository
import com.example.data.security.CryptoEngine
import kotlinx.coroutines.launch

class NotifAiApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var aiRepository: AiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            // Initialize AndroidKeyStore per-key envelope encryption
            CryptoEngine.init()
            // Purge any legacy PIN lock preferences from older versions
            deleteSharedPreferences("notifai_app_lock_prefs")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // Initialize Room Database
            database = AppDatabase.getInstance(this)

            // Initialize AiRepository
            aiRepository = AiRepository(
                apiKeyDao = database.apiKeyDao(),
                chatDao = database.chatDao(),
                context = this
            )
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    aiRepository.ensureBuiltInPollinationsKey()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        lateinit var instance: NotifAiApplication
            private set
    }
}
