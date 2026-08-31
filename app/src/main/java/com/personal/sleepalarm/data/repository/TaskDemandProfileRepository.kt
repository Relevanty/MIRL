package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.TaskDemandProfileDao
import com.personal.sleepalarm.data.db.dao.TaskDependencyDao
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.TaskDependencyEntity
import kotlinx.coroutines.flow.Flow

class TaskDemandProfileRepository(
    private val profiles: TaskDemandProfileDao,
    private val dependencies: TaskDependencyDao
) {
    fun observeAll(): Flow<List<TaskDemandProfileEntity>> = profiles.observeAll()

    fun observeForTask(taskId: Int): Flow<TaskDemandProfileEntity?> =
        profiles.observeForTask(taskId)

    suspend fun getForTask(taskId: Int): TaskDemandProfileEntity? = profiles.getForTask(taskId)

    suspend fun save(profile: TaskDemandProfileEntity) {
        require(profile.taskId > 0) { "taskId is required" }
        profiles.upsert(profile.normalizedForStorage())
    }

    suspend fun deleteProfile(taskId: Int) = profiles.deleteForTask(taskId)

    fun observeDependencies(taskId: Int): Flow<List<TaskDependencyEntity>> =
        dependencies.observeForTask(taskId)

    fun observeAllDependencies(): Flow<List<TaskDependencyEntity>> = dependencies.observeAll()

    fun observeDependents(taskId: Int): Flow<List<TaskDependencyEntity>> =
        dependencies.observeDependents(taskId)

    suspend fun addDependency(dependency: TaskDependencyEntity) {
        require(dependency.taskId > 0 && dependency.dependsOnTaskId > 0) {
            "Both task ids are required"
        }
        require(dependency.taskId != dependency.dependsOnTaskId) {
            "A task cannot depend on itself"
        }
        dependencies.insert(
            dependency.copy(
                dependencyType = dependency.dependencyType.trim().ifBlank { "FINISH_TO_START" }
            )
        )
    }

    suspend fun removeDependency(taskId: Int, dependsOnTaskId: Int) =
        dependencies.delete(taskId, dependsOnTaskId)
}

internal fun TaskDemandProfileEntity.normalizedForStorage(): TaskDemandProfileEntity {
    val minimum = minimumBlockMinutes.coerceIn(1, 24 * 60)
    return copy(
        domain = domain.trim().ifBlank { "OTHER" },
        workMode = workMode.trim().ifBlank { "OTHER" },
        difficulty = difficulty.coerceIn(0, 4),
        concentrationDemand = concentrationDemand.coerceIn(0, 4),
        executiveDemand = executiveDemand.coerceIn(0, 4),
        memoryDemand = memoryDemand.coerceIn(0, 4),
        creativeDemand = creativeDemand.coerceIn(0, 4),
        socialDemand = socialDemand.coerceIn(0, 4),
        physicalDemand = physicalDemand.coerceIn(0, 4),
        emotionalDemand = emotionalDemand.coerceIn(0, 4),
        startFriction = startFriction.coerceIn(0, 4),
        minimumBlockMinutes = minimum,
        preferredBlockMinutes = preferredBlockMinutes.coerceIn(minimum, 24 * 60),
        interruptibility = interruptibility.coerceIn(0, 4),
        placeContext = placeContext.trim().ifBlank { "ANY" },
        toolContext = toolContext.trim(),
        internetRequirement = internetRequirement.trim().ifBlank { "ANY" },
        peopleContext = peopleContext.trim().ifBlank { "ANY" },
        provenance = provenance.trim().ifBlank { "USER" },
        confidence = confidence.coerceIn(0f, 1f),
        userLockMask = userLockMask.coerceAtLeast(0L),
        updatedAt = System.currentTimeMillis()
    )
}
