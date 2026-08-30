package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishLearningMigrationTest {
    @Test
    fun migrationCreatesDictionaryProgressAndIndices() {
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

        AppDatabase.MIGRATION_24_25.migrate(database)

        assertEquals(24, AppDatabase.MIGRATION_24_25.startVersion)
        assertEquals(25, AppDatabase.MIGRATION_24_25.endVersion)
        assertTrue(statements.any { it.contains("CREATE TABLE IF NOT EXISTS `english_words`") })
        assertTrue(statements.any { it.contains("CREATE TABLE IF NOT EXISTS `english_word_progress`") })
        assertTrue(statements.any { it.contains("CREATE TABLE IF NOT EXISTS `english_dictionary_metadata`") })
        assertTrue(statements.any { it.contains("index_english_words_word") })
        assertTrue(statements.any { it.contains("index_english_word_progress_dueAtMillis") })
    }
}
