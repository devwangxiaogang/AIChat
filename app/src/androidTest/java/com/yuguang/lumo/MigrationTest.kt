package com.yuguang.lumo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.MIGRATION_2_3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 迁移 2→3 的真机测试。
 *
 * 手工造一个 v2 库（只有 conversations / messages / translations，没有附件表），
 * 再用正式配置打开。Room 会执行 [MIGRATION_2_3]，并按导出的 schema 校验每一张表，
 * 一旦迁移 SQL 与实体定义不一致就会抛异常，从而暴露问题。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dbName = "migration-2-3.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        createVersion2Database(context.getDatabasePath(dbName))
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2To3KeepsMessagesAndAddsAttachments() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_2_3)
            .build()
        try {
            // 第一次拿 writableDatabase 时执行迁移并校验 schema。
            db.openHelper.writableDatabase

            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM messages").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM message_attachments").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    /** 建库 SQL 取自导出的 v3 schema，去掉 2→3 才新增的 message_attachments。 */
    private fun createVersion2Database(path: File) {
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                    "`systemPrompt` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`conversationId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                    "`reasoning` TEXT NOT NULL, `status` TEXT NOT NULL, `error` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `translations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `sourceLang` TEXT NOT NULL, `targetLang` TEXT NOT NULL, " +
                    "`sourceText` TEXT NOT NULL, `targetText` TEXT NOT NULL, `option` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO conversations (id, title, provider, model, systemPrompt, updatedAt) " +
                    "VALUES (1, '旧会话', 'DEEPSEEK', 'deepseek-flash', '你是一个有帮助的助手。', 0)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (1, 1, 'USER', '迁移前的一条消息', '', 'DONE', '', 0)",
            )
            db.version = 2
        } finally {
            db.close()
        }
    }
}
