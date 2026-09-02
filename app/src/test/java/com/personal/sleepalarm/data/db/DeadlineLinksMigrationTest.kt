package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import android.database.Cursor
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class DeadlineLinksMigrationTest {
    @Test
    fun migrationAddsLinksAndCanonicalUniqueTaskIndexAtomically() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args?.firstOrNull() as String
            if (method.name == "query") Proxy.newProxyInstance(
                Cursor::class.java.classLoader, arrayOf(Cursor::class.java)
            ) { _, cursorMethod, _ -> if (cursorMethod.name == "moveToNext") false else null } else null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_28_29.migrate(database)

        assertEquals(29, APP_DATABASE_VERSION)
        assertEquals(28, AppDatabase.MIGRATION_28_29.startVersion)
        assertEquals(29, AppDatabase.MIGRATION_28_29.endVersion)
        assertEquals(
            listOf(
                "ALTER TABLE dday_events ADD COLUMN linksJson TEXT NOT NULL DEFAULT '[]'",
                "DELETE FROM dday_events",
                "CREATE UNIQUE INDEX IF NOT EXISTS index_dday_events_taskId ON dday_events (taskId)"
            ),
            statements
        )
    }
}
