package com.personal.sleepalarm.data.repository

import com.personal.sleepalarm.data.db.dao.EnergySampleDao
import com.personal.sleepalarm.data.db.dao.FocusProtocolDao
import com.personal.sleepalarm.data.db.entity.EnergySampleEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import kotlinx.coroutines.flow.Flow

class FocusProtocolRepository(
    private val protocolDao: FocusProtocolDao,
    private val energyDao: EnergySampleDao
) {
    fun observeActive(): Flow<FocusProtocolSessionEntity?> = protocolDao.observeActive()

    fun observeLatest(): Flow<FocusProtocolSessionEntity?> = protocolDao.observeLatest()

    fun observeRecentCompleted(limit: Int = 30): Flow<List<FocusProtocolSessionEntity>> =
        protocolDao.observeRecentCompleted(limit)

    fun observeEnergyFrom(from: Long): Flow<List<EnergySampleEntity>> =
        energyDao.observeFrom(from)

    suspend fun insert(session: FocusProtocolSessionEntity): Int =
        protocolDao.insert(session).toInt()

    suspend fun getById(id: Int): FocusProtocolSessionEntity? = protocolDao.getById(id)

    suspend fun getActive(): List<FocusProtocolSessionEntity> = protocolDao.getActive()

    suspend fun update(session: FocusProtocolSessionEntity) = protocolDao.update(session)

    suspend fun incrementDistractions(id: Int) = protocolDao.incrementDistractions(id)

    suspend fun addEnergySample(sample: EnergySampleEntity) = energyDao.insert(sample)
}
