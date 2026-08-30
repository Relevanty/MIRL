package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskAttachmentEntity
import com.personal.sleepalarm.data.db.entity.TaskLibraryLinkEntity
import com.personal.sleepalarm.data.db.entity.TaskSubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityRecordDao {
    @Query("SELECT * FROM activity_records ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM activity_records ORDER BY startedAt ASC")
    suspend fun getAll(): List<ActivityRecordEntity>

    @Query("SELECT * FROM activity_records WHERE endedAt > :from AND startedAt < :to ORDER BY startedAt ASC")
    fun observeOverlapping(from: Long, to: Long): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE endedAt > :from AND startedAt < :to ORDER BY startedAt ASC")
    suspend fun getOverlapping(from: Long, to: Long): List<ActivityRecordEntity>

    @Query("SELECT * FROM activity_records WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun observeForTask(taskId: Int): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE taskId = :taskId ORDER BY startedAt DESC")
    suspend fun getForTask(taskId: Int): List<ActivityRecordEntity>

    @Query("SELECT MAX(endedAt) FROM activity_records WHERE taskId = :taskId AND countsTowardProgress = 1")
    suspend fun getLatestEndForTask(taskId: Int): Long?

    @Query("SELECT * FROM activity_records WHERE id = :id")
    suspend fun getById(id: Int): ActivityRecordEntity?

    @Query("SELECT * FROM activity_records WHERE source = 'MANUAL' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestManual(): ActivityRecordEntity?

    @Query(
        "SELECT * FROM activity_records WHERE id != :excludeId AND endedAt > :start AND startedAt < :end ORDER BY startedAt ASC"
    )
    suspend fun findOverlaps(start: Long, end: Long, excludeId: Int = 0): List<ActivityRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ActivityRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<ActivityRecordEntity>)

    @Update
    suspend fun update(record: ActivityRecordEntity)

    @Delete
    suspend fun delete(record: ActivityRecordEntity)

    @Query("DELETE FROM activity_records WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM activity_records")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM activity_records WHERE taskId = :taskId AND countsTowardProgress = 1")
    suspend fun sumForTask(taskId: Int): Long

    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM activity_records WHERE projectId = :projectId AND countsTowardProgress = 1")
    suspend fun sumForProject(projectId: Int): Long

    @Query("UPDATE activity_records SET projectId = :projectId WHERE taskId = :taskId")
    suspend fun reassignProjectForTask(taskId: Int, projectId: Int?)

    @Query(
        """
        UPDATE tasks SET spentMillis = COALESCE((
            SELECT SUM(durationMillis) FROM activity_records
            WHERE activity_records.taskId = tasks.id AND countsTowardProgress = 1
        ), 0)
        """
    )
    suspend fun rebuildAllTaskTotals()

    @Query(
        """
        UPDATE projects SET spentMillis = COALESCE((
            SELECT SUM(durationMillis) FROM activity_records
            WHERE activity_records.projectId = projects.id AND countsTowardProgress = 1
        ), 0)
        """
    )
    suspend fun rebuildAllProjectTotals()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY isArchived ASC, dueAtMillis IS NULL, dueAtMillis ASC, updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Int): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProjectEntity>)

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE projects SET spentMillis = :spent, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSpent(id: Int, spent: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface TaskSubtaskDao {
    @Query("SELECT * FROM task_subtasks")
    suspend fun getAll(): List<TaskSubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskSubtaskEntity>)

    @Query("DELETE FROM task_subtasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM task_subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeForTask(taskId: Int): Flow<List<TaskSubtaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TaskSubtaskEntity): Long

    @Update
    suspend fun update(item: TaskSubtaskEntity)

    @Query("DELETE FROM task_subtasks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM task_subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query("DELETE FROM task_subtasks WHERE NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = task_subtasks.taskId)")
    suspend fun deleteOrphans()
}

@Dao
interface TaskAttachmentDao {
    @Query("SELECT * FROM task_attachments")
    suspend fun getAll(): List<TaskAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskAttachmentEntity>)

    @Query("DELETE FROM task_attachments")
    suspend fun deleteAll()

    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun observeForTask(taskId: Int): Flow<List<TaskAttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TaskAttachmentEntity): Long

    @Query("DELETE FROM task_attachments WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId")
    suspend fun getForTask(taskId: Int): List<TaskAttachmentEntity>

    @Query("DELETE FROM task_attachments WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query("DELETE FROM task_attachments WHERE NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = task_attachments.taskId)")
    suspend fun deleteOrphans()
}

@Dao
interface TaskLibraryLinkDao {
    @Query("SELECT * FROM task_library_links ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TaskLibraryLinkEntity>>

    @Query("SELECT * FROM task_library_links")
    suspend fun getAll(): List<TaskLibraryLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskLibraryLinkEntity>)

    @Query("DELETE FROM task_library_links")
    suspend fun deleteAll()

    @Query("SELECT * FROM task_library_links WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun observeForTask(taskId: Int): Flow<List<TaskLibraryLinkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM task_library_links WHERE taskId = :taskId AND libraryItemId = :libraryItemId)")
    suspend fun exists(taskId: Int, libraryItemId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: TaskLibraryLinkEntity)

    @Query("DELETE FROM task_library_links WHERE taskId = :taskId AND libraryItemId = :libraryItemId")
    suspend fun delete(taskId: Int, libraryItemId: Int)

    @Query("DELETE FROM task_library_links WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Int)

    @Query("DELETE FROM task_library_links WHERE libraryItemId = :libraryItemId")
    suspend fun deleteForLibraryItem(libraryItemId: Int)

    @Query(
        """
        DELETE FROM task_library_links
        WHERE NOT EXISTS (SELECT 1 FROM tasks WHERE tasks.id = task_library_links.taskId)
           OR NOT EXISTS (SELECT 1 FROM library_items WHERE library_items.id = task_library_links.libraryItemId)
        """
    )
    suspend fun deleteOrphans()
}
