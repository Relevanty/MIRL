package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundscapeMigrationTest {
    @Test
    fun migrationAddsACompleteSafeSoundscapeSnapshot() {
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

        AppDatabase.MIGRATION_26_27.migrate(database)

        assertEquals(26, AppDatabase.MIGRATION_26_27.startVersion)
        assertEquals(27, AppDatabase.MIGRATION_26_27.endVersion)
        val sql = statements.joinToString("\n")
        assertTrue(sql.contains("soundscapeId TEXT NOT NULL DEFAULT 'silence'"))
        assertTrue(sql.contains("soundscapeCustomUri TEXT"))
        assertTrue(sql.contains("soundscapeCustomName TEXT"))
        assertTrue(sql.contains("soundscapeVolume INTEGER NOT NULL DEFAULT 35"))
        assertTrue(sql.contains("soundscapeSecondaryId TEXT"))
        assertTrue(sql.contains("soundscapeSecondaryVolume INTEGER NOT NULL DEFAULT 20"))
        assertTrue(sql.contains("soundscapePlayDuringRecovery INTEGER NOT NULL DEFAULT 0"))
    }
}
