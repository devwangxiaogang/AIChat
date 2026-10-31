package com.yuguang.lumo.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 翻译记录和聊天会话分开。kind 为 TEXT 或 LIVE。 */
@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val targetText: String,
    val option: String,
    val createdAt: Long,
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TranslationEntity>>

    @Insert
    suspend fun insert(entity: TranslationEntity)

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun delete(id: Long)
}
