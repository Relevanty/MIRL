package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEnergyMigrationTest {
    @Test
    fun migrationCreatesAdaptiveGraphAndProjectsLegacyEnergy() {
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

        AppDatabase.MIGRATION_27_28.migrate(database)

        assertTrue(APP_DATABASE_VERSION >= 28)
        assertEquals(27, AppDatabase.MIGRATION_27_28.startVersion)
        assertEquals(28, AppDatabase.MIGRATION_27_28.endVersion)

        val sql = statements.joinToString("\n")
        listOf(
            "daily_check_ins",
            "energy_observations",
            "task_demand_profiles",
            "task_dependencies",
            "work_episode_assessments",
            "external_contexts",
            "context_snapshots",
            "recommendation_decisions"
        ).forEach { table ->
            assertTrue("missing $table", sql.contains("CREATE TABLE IF NOT EXISTS `$table`"))
        }
        assertTrue(sql.contains("index_energy_observations_taskId"))
        assertTrue(sql.contains("index_energy_observations_activityRecordId"))
        assertTrue(sql.contains("index_task_dependencies_dependsOnTaskId"))
        assertTrue(sql.contains("index_work_episode_assessments_activityRecordId"))
        assertTrue(sql.contains("index_external_contexts_localDate_regionKey_source"))
        assertTrue(sql.contains("INSERT OR IGNORE INTO `energy_observations`"))
        assertTrue(sql.contains("WHEN e.`context` = 'BEFORE_FOCUS' THEN 'BEFORE_TASK'"))
        assertTrue(sql.contains("WHEN e.`context` = 'AFTER_FOCUS' THEN 'AFTER_TASK'"))
        assertTrue(sql.contains("'LEGACY_ENERGY_SAMPLE', 'EXACT', 1.0"))
    }
}
