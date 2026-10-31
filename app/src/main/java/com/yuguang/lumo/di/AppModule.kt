package com.yuguang.lumo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.data.local.TranslationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        // 只在调试包记录请求行，并丢掉含密钥的日志。
        val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            val logging = HttpLoggingInterceptor { message ->
                val secret = message.contains("Authorization", ignoreCase = true) ||
                    message.contains("Bearer ", ignoreCase = true)
                if (!secret) android.util.Log.d("AiHttp", message)
            }.apply { level = HttpLoggingInterceptor.Level.BASIC }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ai_chat.db")
            .addMigrations(
                com.yuguang.lumo.data.local.MIGRATION_2_3,
                com.yuguang.lumo.data.local.MIGRATION_3_4,
            )
            // 未覆盖的跨版本变更才重建，避免丢掉 2→3 的会话数据。
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideAttachmentDao(database: AppDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideTranslationDao(database: AppDatabase): TranslationDao = database.translationDao()

    @Provides
    @Singleton
    fun provideSecretPrefs(@ApplicationContext context: Context): SharedPreferences {
        // 密钥用 Keystore 加密。设备不支持加密存储时退回普通偏好，避免设置页打不开。
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "provider_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            context.getSharedPreferences("provider_secrets_plain", Context.MODE_PRIVATE)
        }
    }
}
