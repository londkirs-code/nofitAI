package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ProviderType

class DatabaseConverters {
    @TypeConverter
    fun fromProviderType(value: ProviderType): String = value.name

    @TypeConverter
    fun toProviderType(value: String): ProviderType = try {
        ProviderType.valueOf(value)
    } catch (_: Exception) {
        ProviderType.GROQ
    }
}

@Database(
    entities = [
        ApiKeyEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE api_keys ADD COLUMN supportedTasks TEXT NOT NULL DEFAULT 'TEXT,IMAGE_INPUT,FILE_INPUT'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE api_keys ADD COLUMN fallbackModels TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE api_keys ADD COLUMN customProtocol TEXT NOT NULL DEFAULT 'OPENAI'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE api_keys ADD COLUMN modelSuspensionsCsv TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notifai_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
