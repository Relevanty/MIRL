package com.personal.sleepalarm.data.english

import com.personal.sleepalarm.data.db.dao.EnglishProgressSummaryProjection
import com.personal.sleepalarm.data.db.dao.EnglishStudyDao
import com.personal.sleepalarm.data.db.dao.EnglishStudySetSummaryProjection
import com.personal.sleepalarm.data.db.entity.EnglishCardProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudyCardEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudySetEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordDirectionalProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordProgressEntity
import com.personal.sleepalarm.domain.english.EnglishDictionaryArticle
import com.personal.sleepalarm.domain.english.EnglishDictionarySense
import com.personal.sleepalarm.domain.english.EnglishLearningMode
import com.personal.sleepalarm.domain.english.EnglishReviewGrade
import com.personal.sleepalarm.domain.english.EnglishReviewState
import com.personal.sleepalarm.domain.english.EnglishSpacedRepetitionScheduler
import com.personal.sleepalarm.domain.english.EnglishStudyCardDraft
import com.personal.sleepalarm.domain.english.EnglishStudyContentValidationException
import com.personal.sleepalarm.domain.english.EnglishStudyContentValidator
import com.personal.sleepalarm.domain.english.EnglishStudyDirection
import com.personal.sleepalarm.domain.english.EnglishStudyPromptFactory
import com.personal.sleepalarm.domain.english.EnglishStudySetDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class EnglishVocabularyRepository(
    private val dao: EnglishStudyDao,
    private val assetSource: EnglishDictionaryAssetSource
) {
    private val reviewMutex = Mutex()
    private val contentMutex = Mutex()
    private val seedMutex = Mutex()

    fun observeSummary(): Flow<EnglishProgressSummaryProjection> = dao.observeSummary()

    fun observeDueCount(nowMillis: Long): Flow<Int> = dao.observeDueCount(nowMillis)

    suspend fun ensureSeeded() {
        if (isCurrentDictionaryInstalled()) return
        seedMutex.withLock {
            // A second screen or restored ViewModel may have completed seeding while
            // this caller was waiting. Avoid parsing and replacing 10,000 rows twice.
            if (isCurrentDictionaryInstalled()) return@withLock
            val bundle = withContext(Dispatchers.IO) { assetSource.loadBundle() }
            dao.replaceDictionary(
                words = bundle.words,
                senses = bundle.senses,
                datasetVersion = EnglishDictionaryAssetSource.DATASET_VERSION
            )
        }
    }

    suspend fun nextWord(nowMillis: Long): EnglishWordEntity? = dao.nextDueWord(nowMillis)

    suspend fun recordReview(
        word: EnglishWordEntity,
        mode: EnglishLearningMode,
        grade: EnglishReviewGrade,
        isCorrect: Boolean,
        nowMillis: Long
    ): EnglishWordProgressEntity = reviewMutex.withLock {
        val previous = dao.progressForWord(word.id)
        val schedule = EnglishSpacedRepetitionScheduler.schedule(
            previous = previous?.let {
                EnglishReviewState(
                    intervalMinutes = it.intervalMinutes,
                    easePermille = it.easePermille,
                    repetitions = it.repetitions,
                    lapses = it.lapses
                )
            },
            grade = grade,
            nowMillis = nowMillis
        )
        val updated = EnglishWordProgressEntity(
            wordId = word.id,
            dueAtMillis = schedule.dueAtMillis,
            intervalMinutes = schedule.intervalMinutes,
            easePermille = schedule.easePermille,
            repetitions = schedule.repetitions,
            lapses = schedule.lapses,
            reviewCount = (previous?.reviewCount ?: 0) + 1,
            correctCount = (previous?.correctCount ?: 0) + if (isCorrect) 1 else 0,
            cardReviews = (previous?.cardReviews ?: 0) + if (mode == EnglishLearningMode.CARDS) 1 else 0,
            writingReviews = (previous?.writingReviews ?: 0) + if (mode == EnglishLearningMode.WRITING) 1 else 0,
            pronunciationReviews = (previous?.pronunciationReviews ?: 0) + if (mode == EnglishLearningMode.PRONUNCIATION) 1 else 0,
            listeningReviews = (previous?.listeningReviews ?: 0) + if (mode == EnglishLearningMode.LISTENING) 1 else 0,
            lastGrade = grade.name,
            lastMode = mode.name,
            lastReviewedAtMillis = nowMillis
        )
        dao.upsertProgress(updated)
        updated
    }

    // ---------- User sets and cards ----------

    fun observeStudySets(): Flow<List<EnglishStudySetSummaryProjection>> = dao.observeStudySets()

    fun observeStudySet(setId: Long): Flow<EnglishStudySetEntity?> = dao.observeStudySet(setId)

    fun observeStudyCards(setId: Long): Flow<List<EnglishStudyCardEntity>> =
        dao.observeStudyCards(setId)

    suspend fun studySet(setId: Long): EnglishStudySetEntity? = dao.getStudySet(setId)

    suspend fun studyCard(cardId: Long): EnglishStudyCardEntity? = dao.getStudyCard(cardId)

    fun observeCardProgress(cardId: Long): Flow<List<EnglishCardProgressEntity>> =
        dao.observeCardProgress(cardId)

    suspend fun createStudySet(
        draft: EnglishStudySetDraft,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = contentMutex.withLock {
        val validation = EnglishStudyContentValidator.validateSet(draft)
        if (!validation.isValid) throw EnglishStudyContentValidationException(validation.errors)
        dao.insertStudySet(
            EnglishStudySetEntity(
                title = requireNotNull(validation.normalizedSetTitle),
                description = draft.description.trim(),
                colorSeed = draft.colorSeed,
                defaultDirection = draft.defaultDirection.name,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
        )
    }

    suspend fun updateStudySet(
        setId: Long,
        draft: EnglishStudySetDraft,
        nowMillis: Long = System.currentTimeMillis()
    ) = contentMutex.withLock {
        val existing = requireNotNull(dao.getStudySet(setId)) { "Unknown English study set: $setId" }
        val validation = EnglishStudyContentValidator.validateSet(draft)
        if (!validation.isValid) throw EnglishStudyContentValidationException(validation.errors)
        dao.upsertStudySet(
            existing.copy(
                title = requireNotNull(validation.normalizedSetTitle),
                description = draft.description.trim(),
                colorSeed = draft.colorSeed,
                defaultDirection = draft.defaultDirection.name,
                updatedAtMillis = nowMillis
            )
        )
    }

    suspend fun deleteStudySet(setId: Long) = contentMutex.withLock {
        dao.deleteStudySet(setId)
    }

    suspend fun createStudyCard(
        setId: Long,
        draft: EnglishStudyCardDraft,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = contentMutex.withLock {
        requireNotNull(dao.getStudySet(setId)) { "Unknown English study set: $setId" }
        val normalized = validatedCard(draft)
        val id = dao.insertStudyCard(
            normalized.toEntity(
                setId = setId,
                position = dao.maxStudyCardPosition(setId) + 1,
                nowMillis = nowMillis
            )
        )
        touchSet(setId, nowMillis)
        id
    }

    suspend fun addDictionaryWordToSet(
        setId: Long,
        wordId: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        ensureSeeded()
        return contentMutex.withLock {
            requireNotNull(dao.getStudySet(setId)) { "Unknown English study set: $setId" }
            dao.findStudyCardForDictionaryWord(setId, wordId)?.let { return@withLock it.id }
            val word = requireNotNull(dao.getDictionaryWord(wordId)) { "Unknown dictionary word: $wordId" }
            val id = dao.insertStudyCard(
                EnglishStudyCardEntity(
                    setId = setId,
                    dictionaryWordId = word.id,
                    term = word.word,
                    translation = word.translation,
                    definition = word.hint,
                    example = "",
                    exampleTranslation = "",
                    notes = "",
                    position = dao.maxStudyCardPosition(setId) + 1,
                    createdAtMillis = nowMillis,
                    updatedAtMillis = nowMillis
                )
            )
            touchSet(setId, nowMillis)
            id
        }
    }

    suspend fun updateStudyCard(
        cardId: Long,
        draft: EnglishStudyCardDraft,
        nowMillis: Long = System.currentTimeMillis()
    ) = contentMutex.withLock {
        val existing = requireNotNull(dao.getStudyCard(cardId)) { "Unknown English study card: $cardId" }
        val normalized = validatedCard(draft)
        dao.upsertStudyCard(
            existing.copy(
                dictionaryWordId = normalized.dictionaryWordId,
                term = normalized.term,
                translation = normalized.translation,
                definition = normalized.definition,
                example = normalized.example,
                exampleTranslation = normalized.exampleTranslation,
                notes = normalized.notes,
                updatedAtMillis = nowMillis
            )
        )
        touchSet(existing.setId, nowMillis)
    }

    suspend fun deleteStudyCard(
        cardId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) = contentMutex.withLock {
        val existing = dao.getStudyCard(cardId) ?: return@withLock
        dao.deleteStudyCard(cardId)
        compactPositions(existing.setId, nowMillis)
    }

    suspend fun moveStudyCard(
        cardId: Long,
        targetPosition: Int,
        nowMillis: Long = System.currentTimeMillis()
    ) = contentMutex.withLock {
        val target = requireNotNull(dao.getStudyCard(cardId)) { "Unknown English study card: $cardId" }
        val cards = dao.getStudyCards(target.setId).toMutableList()
        val oldIndex = cards.indexOfFirst { it.id == cardId }
        if (oldIndex < 0) return@withLock
        val card = cards.removeAt(oldIndex)
        cards.add(targetPosition.coerceIn(0, cards.size), card)
        dao.upsertStudyCards(cards.mapIndexed { index, item ->
            item.copy(position = index, updatedAtMillis = if (item.id == cardId) nowMillis else item.updatedAtMillis)
        })
        touchSet(target.setId, nowMillis)
    }

    // ---------- Direction-aware study ----------

    suspend fun nextStudyCard(
        setId: Long,
        requestedDirection: EnglishStudyDirection? = null,
        mixedTurn: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): EnglishStudyCardCandidate? {
        val set = requireNotNull(dao.getStudySet(setId)) { "Unknown English study set: $setId" }
        val configured = requestedDirection
            ?: EnglishStudyDirection.fromStorage(set.defaultDirection)
        val direction = resolveAvailableCardDirection(setId, configured, mixedTurn, nowMillis)
            ?: return null
        val card = dao.nextDueStudyCard(setId, direction.name, nowMillis) ?: return null
        return EnglishStudyCardCandidate(
            card = card,
            direction = direction,
            prompt = EnglishStudyPromptFactory.create(
                term = card.term,
                translation = card.translation,
                definition = card.definition,
                example = card.example,
                exampleTranslation = card.exampleTranslation,
                notes = card.notes,
                direction = direction
            )
        )
    }

    suspend fun recordStudyCardReview(
        cardId: Long,
        direction: EnglishStudyDirection,
        mode: EnglishLearningMode,
        grade: EnglishReviewGrade,
        isCorrect: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): EnglishCardProgressEntity = reviewMutex.withLock {
        direction.requireConcrete()
        requireNotNull(dao.getStudyCard(cardId)) { "Unknown English study card: $cardId" }
        val previous = dao.getCardProgress(cardId, direction.name)
        val schedule = scheduleReview(previous?.toReviewState(), grade, nowMillis)
        val updated = EnglishCardProgressEntity(
            cardId = cardId,
            direction = direction.name,
            dueAtMillis = schedule.dueAtMillis,
            intervalMinutes = schedule.intervalMinutes,
            easePermille = schedule.easePermille,
            repetitions = schedule.repetitions,
            lapses = schedule.lapses,
            reviewCount = (previous?.reviewCount ?: 0) + 1,
            correctCount = (previous?.correctCount ?: 0) + if (isCorrect) 1 else 0,
            cardReviews = (previous?.cardReviews ?: 0) + if (mode == EnglishLearningMode.CARDS) 1 else 0,
            writingReviews = (previous?.writingReviews ?: 0) + if (mode == EnglishLearningMode.WRITING) 1 else 0,
            pronunciationReviews = (previous?.pronunciationReviews ?: 0) + if (mode == EnglishLearningMode.PRONUNCIATION) 1 else 0,
            listeningReviews = (previous?.listeningReviews ?: 0) + if (mode == EnglishLearningMode.LISTENING) 1 else 0,
            lastGrade = grade.name,
            lastMode = mode.name,
            lastReviewedAtMillis = nowMillis
        )
        dao.upsertCardProgress(updated)
        updated
    }

    suspend fun nextDictionaryWord(
        requestedDirection: EnglishStudyDirection = EnglishStudyDirection.MIXED,
        mixedTurn: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): EnglishDictionaryWordCandidate? {
        ensureSeeded()
        val preferred = if (mixedTurn % 2 == 0) EnglishStudyDirection.EN_TO_RU else EnglishStudyDirection.RU_TO_EN
        val directions = when (requestedDirection) {
            EnglishStudyDirection.MIXED -> listOf(preferred, opposite(preferred))
            else -> listOf(requestedDirection.requireConcrete())
        }
        for (direction in directions) {
            val word = dao.nextDueDictionaryWord(direction.name, nowMillis) ?: continue
            return EnglishDictionaryWordCandidate(
                word = word,
                direction = direction,
                prompt = EnglishStudyPromptFactory.create(
                    term = word.word,
                    translation = word.translation,
                    definition = word.hint,
                    example = "",
                    exampleTranslation = "",
                    notes = "",
                    direction = direction
                )
            )
        }
        return null
    }

    suspend fun recordDictionaryReview(
        wordId: Int,
        direction: EnglishStudyDirection,
        mode: EnglishLearningMode,
        grade: EnglishReviewGrade,
        isCorrect: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): EnglishWordDirectionalProgressEntity = reviewMutex.withLock {
        direction.requireConcrete()
        requireNotNull(dao.getDictionaryWord(wordId)) { "Unknown dictionary word: $wordId" }
        val previous = dao.getDirectionalProgress(wordId, direction.name)
        val schedule = scheduleReview(previous?.toReviewState(), grade, nowMillis)
        val updated = EnglishWordDirectionalProgressEntity(
            wordId = wordId,
            direction = direction.name,
            dueAtMillis = schedule.dueAtMillis,
            intervalMinutes = schedule.intervalMinutes,
            easePermille = schedule.easePermille,
            repetitions = schedule.repetitions,
            lapses = schedule.lapses,
            reviewCount = (previous?.reviewCount ?: 0) + 1,
            correctCount = (previous?.correctCount ?: 0) + if (isCorrect) 1 else 0,
            cardReviews = (previous?.cardReviews ?: 0) + if (mode == EnglishLearningMode.CARDS) 1 else 0,
            writingReviews = (previous?.writingReviews ?: 0) + if (mode == EnglishLearningMode.WRITING) 1 else 0,
            pronunciationReviews = (previous?.pronunciationReviews ?: 0) + if (mode == EnglishLearningMode.PRONUNCIATION) 1 else 0,
            listeningReviews = (previous?.listeningReviews ?: 0) + if (mode == EnglishLearningMode.LISTENING) 1 else 0,
            lastGrade = grade.name,
            lastMode = mode.name,
            lastReviewedAtMillis = nowMillis
        )
        dao.upsertDirectionalProgress(updated)
        updated
    }

    fun observeDictionarySummary(direction: EnglishStudyDirection): Flow<EnglishProgressSummaryProjection> =
        dao.observeDirectionalSummary(direction.requireConcrete().name)

    fun observeDictionaryDueCount(
        direction: EnglishStudyDirection,
        nowMillis: Long
    ): Flow<Int> = dao.observeDirectionalDueCount(direction.requireConcrete().name, nowMillis)

    // ---------- Dictionary search and articles ----------

    suspend fun searchDictionary(query: String, limit: Int = 30): List<EnglishWordEntity> {
        ensureSeeded()
        // SQLite NOCASE is ASCII-only. Lowercasing in Kotlin keeps Russian search
        // predictable; the DAO additionally treats е/ё as equivalent.
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return browseDictionary(limit = limit)
        return dao.searchDictionary(normalized, limit.coerceIn(1, 100))
    }

    suspend fun browseDictionary(limit: Int = 50, offset: Int = 0): List<EnglishWordEntity> {
        ensureSeeded()
        return dao.browseDictionary(
            limit = limit.coerceIn(1, 100),
            offset = offset.coerceAtLeast(0)
        )
    }

    suspend fun dictionaryArticle(wordId: Int): EnglishDictionaryArticle? {
        ensureSeeded()
        val word = dao.getDictionaryWord(wordId) ?: return null
        val senses = dao.getDictionarySenses(wordId).map { sense ->
            EnglishDictionarySense(
                order = sense.senseOrder,
                definition = sense.definition,
                translations = splitStoredList(sense.translations),
                example = sense.example,
                exampleTranslation = sense.exampleTranslation,
                synonyms = splitStoredList(sense.synonyms),
                usageLabels = splitStoredList(sense.usageLabels)
            )
        }
        return EnglishDictionaryArticle(
            wordId = word.id,
            headword = word.word,
            pronunciation = word.pronunciation,
            partOfSpeech = word.partOfSpeech,
            frequencyLevel = word.level,
            frequencyRank = word.frequencyRank,
            senses = senses.ifEmpty {
                listOf(
                    EnglishDictionarySense(
                        order = 0,
                        definition = word.hint,
                        translations = splitStoredList(word.translation)
                    )
                )
            }
        )
    }

    suspend fun dictionaryArticle(headword: String): EnglishDictionaryArticle? {
        ensureSeeded()
        return dao.getDictionaryWord(headword)?.let { dictionaryArticle(it.id) }
    }

    private suspend fun resolveAvailableCardDirection(
        setId: Long,
        requested: EnglishStudyDirection,
        mixedTurn: Int,
        nowMillis: Long
    ): EnglishStudyDirection? {
        val preferred = if (mixedTurn % 2 == 0) EnglishStudyDirection.EN_TO_RU else EnglishStudyDirection.RU_TO_EN
        val directions = when (requested) {
            EnglishStudyDirection.MIXED -> listOf(preferred, opposite(preferred))
            else -> listOf(requested.requireConcrete())
        }
        return directions.firstOrNull { dao.nextDueStudyCard(setId, it.name, nowMillis) != null }
    }

    private suspend fun compactPositions(setId: Long, nowMillis: Long) {
        val cards = dao.getStudyCards(setId)
        dao.upsertStudyCards(cards.mapIndexed { index, card -> card.copy(position = index) })
        touchSet(setId, nowMillis)
    }

    private suspend fun touchSet(setId: Long, nowMillis: Long) {
        dao.getStudySet(setId)?.let { dao.upsertStudySet(it.copy(updatedAtMillis = nowMillis)) }
    }

    private fun validatedCard(draft: EnglishStudyCardDraft): EnglishStudyCardDraft {
        val validation = EnglishStudyContentValidator.validateCard(draft)
        if (!validation.isValid) throw EnglishStudyContentValidationException(validation.errors)
        return requireNotNull(validation.normalizedCard)
    }

    private fun EnglishStudyCardDraft.toEntity(
        setId: Long,
        position: Int,
        nowMillis: Long
    ) = EnglishStudyCardEntity(
        setId = setId,
        dictionaryWordId = dictionaryWordId,
        term = term,
        translation = translation,
        definition = definition,
        example = example,
        exampleTranslation = exampleTranslation,
        notes = notes,
        position = position,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis
    )

    private fun EnglishCardProgressEntity.toReviewState() = EnglishReviewState(
        intervalMinutes = intervalMinutes,
        easePermille = easePermille,
        repetitions = repetitions,
        lapses = lapses
    )

    private fun EnglishWordDirectionalProgressEntity.toReviewState() = EnglishReviewState(
        intervalMinutes = intervalMinutes,
        easePermille = easePermille,
        repetitions = repetitions,
        lapses = lapses
    )

    private fun scheduleReview(
        previous: EnglishReviewState?,
        grade: EnglishReviewGrade,
        nowMillis: Long
    ) = EnglishSpacedRepetitionScheduler.schedule(previous, grade, nowMillis)

    private fun opposite(direction: EnglishStudyDirection): EnglishStudyDirection = when (direction) {
        EnglishStudyDirection.EN_TO_RU -> EnglishStudyDirection.RU_TO_EN
        EnglishStudyDirection.RU_TO_EN -> EnglishStudyDirection.EN_TO_RU
        EnglishStudyDirection.MIXED -> error("MIXED has no single opposite direction")
    }

    private fun splitStoredList(value: String): List<String> = value
        .split(';', '|')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    private suspend fun isCurrentDictionaryInstalled(): Boolean =
        dao.wordCount() == EnglishDictionaryAssetSource.EXPECTED_WORD_COUNT &&
            dao.dictionaryVersion() == EnglishDictionaryAssetSource.DATASET_VERSION
}
