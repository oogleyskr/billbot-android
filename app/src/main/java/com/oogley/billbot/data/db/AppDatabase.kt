package com.oogley.billbot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, SessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `label` TEXT,
                        `agentId` TEXT,
                        `messageCount` INTEGER NOT NULL DEFAULT 0,
                        `lastActiveAt` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `previewText` TEXT
                    )
                """.trimIndent())
            }
        }
    }
}
