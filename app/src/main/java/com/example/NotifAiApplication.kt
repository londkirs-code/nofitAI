package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.AiRepository
import com.example.data.security.CryptoEngine

class NotifAiApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var aiRepository: AiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            // Initialize AndroidKeyStore encryption
            CryptoEngine.init()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        lateinit var instance: NotifAiApplication
            private set
    }
}
