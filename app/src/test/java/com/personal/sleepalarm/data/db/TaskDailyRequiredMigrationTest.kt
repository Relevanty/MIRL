package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDailyRequiredMigrationTest {
    @Test
    fun databaseIncludesDailyTaskMigrationAndCurrentVersion() {
        assertTrue(APP_DATABASE_VERSION >= 24)
        assertEquals(23, AppDatabase.MIGRATION_23_24.startVersion)
        assertEquals(24, AppDatabase.MIGRATION_23_24.endVersion)
    }

    @Test
    fun migrationAddsOptInColumnWithSafeFalseDefault() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args?.firstOrNull() as String
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_23_24.migrate(database)

        assertEquals(
            listOf("ALTER TABLE tasks ADD COLUMN isDailyRequired INTEGER NOT NULL DEFAULT 0"),
            statements
        )
    }
}
