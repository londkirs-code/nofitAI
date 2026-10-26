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

class Converters {
    @TypeConverter
    fun fromProviderType(value: ProviderType): String = value.name

    @TypeConverter
    fun toProviderType(value: String): ProviderType = try {
        ProviderType.valueOf(value)
    } catch (_: Exception) {
        ProviderType.GEMINI
    }
}

@Database(
    entities = [
        ApiKeyEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE api_keys ADD COLUMN temperature REAL NOT NULL DEFAULT 0.7")
                db.execSQL("ALTER TABLE api_keys ADD COLUMN maxTokens INTEGER NOT NULL DEFAULT 4096")
                db.execSQL("ALTER TABLE api_keys ADD COLUMN systemPrompt TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notifai_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
