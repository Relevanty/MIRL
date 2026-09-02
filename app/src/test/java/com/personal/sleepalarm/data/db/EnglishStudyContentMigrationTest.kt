package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishStudyContentMigrationTest {
    @Test
    fun `english content migration remains twenty five to twenty six`() {
        assertTrue(APP_DATABASE_VERSION >= 26)
        assertEquals(25, AppDatabase.MIGRATION_25_26.startVersion)
        assertEquals(26, AppDatabase.MIGRATION_25_26.endVersion)
        assertEquals(24, AppDatabase.MIGRATION_24_25.startVersion)
        assertEquals(25, AppDatabase.MIGRATION_24_25.endVersion)
    }

    @Test
    fun `migration creates offline content tables and both directional histories`() {
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

        AppDatabase.MIGRATION_25_26.migrate(database)

        val sql = statements.joinToString("\n")
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `english_word_senses`"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `english_word_directional_progress`"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `english_study_sets`"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `english_study_cards`"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `english_card_progress`"))
        assertTrue(sql.contains("SELECT wordId, 'EN_TO_RU'"))
        assertTrue(sql.contains("SELECT wordId, 'RU_TO_EN'"))
        assertTrue(sql.contains("FROM english_word_progress"))
        assertTrue(sql.contains("FROM english_words"))
    }
}
