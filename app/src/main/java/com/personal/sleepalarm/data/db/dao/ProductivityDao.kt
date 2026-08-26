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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: TaskLibraryLinkEntity)

    @Query("DELETE FROM task_library_links WHERE taskId = :taskId AND libraryItemId = :libraryItemId")
    suspend fun delete(taskId: Int, libraryItemId: Int)
}
