package com.personal.sleepalarm.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.canonicalizeTaskDeadlines

/** Runs inside Room's migration transaction, before the nullable unique task index is added. */
internal fun migrateCanonicalTaskDeadlines(db: SupportSQLiteDatabase) {
    val tasks = db.query(
        "SELECT id, title, nextAction, expectedResult, description, projectId, createdAt, dueAtMillis FROM tasks"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(TaskEntity(
                id = cursor.getInt(0), title = cursor.getString(1), nextAction = cursor.getString(2),
                expectedResult = cursor.getString(3), description = cursor.getString(4),
                projectId = if (cursor.isNull(5)) null else cursor.getInt(5),
                createdAt = cursor.getLong(6), dueAtMillis = if (cursor.isNull(7)) null else cursor.getLong(7)
            ))
        }
    }
    val deadlines = db.query(
        "SELECT id, title, targetDate, projectId, taskId, notes, linksJson, createdAt FROM dday_events"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(DDayEntity(
                id = cursor.getInt(0), title = cursor.getString(1), targetDate = cursor.getString(2),
                projectId = if (cursor.isNull(3)) null else cursor.getInt(3),
                taskId = if (cursor.isNull(4)) null else cursor.getInt(4),
                notes = cursor.getString(5), linksJson = cursor.getString(6), createdAt = cursor.getLong(7)
            ))
        }
    }
    val canonical = canonicalizeTaskDeadlines(tasks, deadlines, adoptLegacyDates = true)
    val originalById = tasks.associateBy { it.id }
    canonical.tasks.filter { it.dueAtMillis != originalById[it.id]?.dueAtMillis }.forEach { task ->
        db.execSQL("UPDATE tasks SET dueAtMillis = ? WHERE id = ?", arrayOf<Any?>(task.dueAtMillis, task.id))
    }
    // All content has been retained or merged in memory; rebuilding avoids
    // intermediate uniqueness conflicts. The surrounding transaction is atomic.
    db.execSQL("DELETE FROM dday_events")
    canonical.deadlines.forEach { row ->
        db.execSQL(
            "INSERT INTO dday_events (id,title,targetDate,projectId,taskId,notes,linksJson,createdAt) VALUES (?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(row.id, row.title, row.targetDate, row.projectId, row.taskId, row.notes, row.linksJson, row.createdAt)
        )
    }
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dday_events_taskId ON dday_events (taskId)")
}
