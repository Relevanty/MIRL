package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishDictionaryMetadataEntity
import com.personal.sleepalarm.data.db.entity.EnglishCardProgressEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudyCardEntity
import com.personal.sleepalarm.data.db.entity.EnglishStudySetEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordSenseEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordDirectionalProgressEntity
import kotlinx.coroutines.flow.Flow

data class EnglishProgressSummaryProjection(
    val totalWords: Int,
    val startedWords: Int,
    val masteredWords: Int,
    val totalReviews: Int,
    val correctReviews: Int
)

data class EnglishProgressWithWordProjection(
    @Embedded val progress: EnglishWordProgressEntity,
    val word: String
)

data class EnglishDirectionalProgressWithWordProjection(
    @Embedded val progress: EnglishWordDirectionalProgressEntity,
    val word: String
)

data class EnglishStudySetSummaryProjection(
    @Embedded val studySet: EnglishStudySetEntity,
    val cardCount: Int,
    val reviewedDirectionCount: Int,
    val masteredDirectionCount: Int
)

data class EnglishStudyCardBackupProjection(
    @Embedded val card: EnglishStudyCardEntity,
    val dictionaryHeadword: String?
)

@Dao
interface EnglishStudyDao {
    @Query("SELECT COUNT(*) FROM english_words")
    suspend fun wordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<EnglishWordEntity>)

    @Query("DELETE FROM english_words")
    suspend fun deleteAllWords()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordSenses(senses: List<EnglishWordSenseEntity>)

    @Query("DELETE FROM english_word_senses")
    suspend fun deleteAllWordSenses()

    @Query("SELECT datasetVersion FROM english_dictionary_metadata WHERE id = 1")
    suspend fun dictionaryVersion(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDictionaryMetadata(metadata: EnglishDictionaryMetadataEntity)

    @Query(
        """
        SELECT p.*, w.word AS word
        FROM english_word_progress p
        INNER JOIN english_words w ON w.id = p.wordId
        ORDER BY p.wordId
        """
    )
    suspend fun getAllProgressWithWords(): List<EnglishProgressWithWordProjection>

    @Query(
        """
        SELECT p.*, w.word AS word
        FROM english_word_directional_progress p
        INNER JOIN english_words w ON w.id = p.wordId
        ORDER BY p.wordId, p.direction
        """
    )
    suspend fun getAllDirectionalProgressWithWords(): List<EnglishDirectionalProgressWithWordProjection>

    @Transaction
    suspend fun replaceDictionary(
        words: List<EnglishWordEntity>,
        senses: List<EnglishWordSenseEntity>,
        datasetVersion: String
    ) {
        // IDs are positional in the generated asset. Preserve learning history by
        // headword whenever a future dictionary build reorders those positions.
        val priorProgress = getAllProgressWithWords()
        val priorDirectionalProgress = getAllDirectionalProgressWithWords()
        val linkedStudyCards = getAllStudyCardsForBackup()
        deleteAllProgress()
        deleteAllDirectionalProgress()
        deleteAllWordSenses()
        deleteAllWords()
        words.chunked(500).forEach { insertWords(it) }
        senses.chunked(500).forEach { insertWordSenses(it) }
        val newIdsByWord = words.associate { it.word to it.id }
        val remapped = priorProgress.mapNotNull { saved ->
            newIdsByWord[saved.word]?.let { newId -> saved.progress.copy(wordId = newId) }
        }
        if (remapped.isNotEmpty()) insertAllProgress(remapped)
        val remappedDirectional = priorDirectionalProgress.mapNotNull { saved ->
            newIdsByWord[saved.word]?.let { newId -> saved.progress.copy(wordId = newId) }
        }
        if (remappedDirectional.isNotEmpty()) {
            insertAllDirectionalProgress(remappedDirectional)
        }
        val remappedCards = linkedStudyCards.map { saved ->
            saved.card.copy(
                dictionaryWordId = saved.dictionaryHeadword?.let(newIdsByWord::get)
            )
        }
        if (remappedCards.isNotEmpty()) upsertStudyCards(remappedCards)
        upsertDictionaryMetadata(
            EnglishDictionaryMetadataEntity(datasetVersion = datasetVersion)
        )
    }

    @Transaction
    suspend fun replaceDictionary(words: List<EnglishWordEntity>, datasetVersion: String) {
        replaceDictionary(
            words = words,
            senses = words.map { word ->
                EnglishWordSenseEntity(
                    wordId = word.id,
                    senseOrder = 0,
                    definition = word.hint,
                    translations = word.translation,
                    example = "",
                    exampleTranslation = "",
                    synonyms = "",
                    usageLabels = word.partOfSpeech
                )
            },
            datasetVersion = datasetVersion
        )
    }

    @Query(
        """
        SELECT w.* FROM english_words w
        LEFT JOIN english_word_progress p ON p.wordId = w.id
        WHERE p.wordId IS NULL OR p.dueAtMillis <= :nowMillis
        ORDER BY CASE WHEN p.wordId IS NULL THEN 1 ELSE 0 END,
                 COALESCE(p.dueAtMillis, 9223372036854775807),
                 w.frequencyRank
        LIMIT 1
        """
    )
    suspend fun nextDueWord(nowMillis: Long): EnglishWordEntity?

    @Query("SELECT * FROM english_word_progress WHERE wordId = :wordId")
    suspend fun progressForWord(wordId: Int): EnglishWordProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: EnglishWordProgressEntity)

    @Query("SELECT * FROM english_word_progress ORDER BY wordId")
    suspend fun getAllProgress(): List<EnglishWordProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllProgress(progress: List<EnglishWordProgressEntity>)

    @Query("DELETE FROM english_word_progress")
    suspend fun deleteAllProgress()

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM english_words) AS totalWords,
            COUNT(*) AS startedWords,
            COALESCE(SUM(CASE WHEN intervalMinutes >= 30240 AND repetitions >= 4 THEN 1 ELSE 0 END), 0) AS masteredWords,
            COALESCE(SUM(reviewCount), 0) AS totalReviews,
            COALESCE(SUM(correctCount), 0) AS correctReviews
        FROM english_word_progress
        """
    )
    fun observeSummary(): Flow<EnglishProgressSummaryProjection>

    @Query("SELECT COUNT(*) FROM english_word_progress WHERE dueAtMillis <= :nowMillis")
    fun observeDueCount(nowMillis: Long): Flow<Int>

    @Query(
        """
        SELECT w.* FROM english_words w
        LEFT JOIN english_word_directional_progress p
          ON p.wordId = w.id AND p.direction = :direction
        WHERE p.wordId IS NULL OR p.dueAtMillis <= :nowMillis
        ORDER BY CASE WHEN p.wordId IS NULL THEN 1 ELSE 0 END,
                 COALESCE(p.dueAtMillis, 9223372036854775807),
                 w.frequencyRank
        LIMIT 1
        """
    )
    suspend fun nextDueDictionaryWord(
        direction: String,
        nowMillis: Long
    ): EnglishWordEntity?

    @Query(
        "SELECT * FROM english_word_directional_progress WHERE wordId = :wordId AND direction = :direction"
    )
    suspend fun getDirectionalProgress(
        wordId: Int,
        direction: String
    ): EnglishWordDirectionalProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDirectionalProgress(progress: EnglishWordDirectionalProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDirectionalProgress(progress: List<EnglishWordDirectionalProgressEntity>)

    @Query("SELECT * FROM english_word_directional_progress ORDER BY wordId, direction")
    suspend fun getAllDirectionalProgress(): List<EnglishWordDirectionalProgressEntity>

    @Query("DELETE FROM english_word_directional_progress")
    suspend fun deleteAllDirectionalProgress()

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM english_words) AS totalWords,
            COUNT(*) AS startedWords,
            COALESCE(SUM(CASE WHEN intervalMinutes >= 30240 AND repetitions >= 4 THEN 1 ELSE 0 END), 0) AS masteredWords,
            COALESCE(SUM(reviewCount), 0) AS totalReviews,
            COALESCE(SUM(correctCount), 0) AS correctReviews
        FROM english_word_directional_progress
        WHERE direction = :direction
        """
    )
    fun observeDirectionalSummary(direction: String): Flow<EnglishProgressSummaryProjection>

    @Query(
        "SELECT COUNT(*) FROM english_word_directional_progress WHERE direction = :direction AND dueAtMillis <= :nowMillis"
    )
    fun observeDirectionalDueCount(direction: String, nowMillis: Long): Flow<Int>

    // User-created study sets and cards.
    @Query(
        """
        SELECT s.*,
               COUNT(DISTINCT c.id) AS cardCount,
               COUNT(DISTINCT CASE WHEN p.reviewCount > 0 THEN p.cardId || ':' || p.direction END) AS reviewedDirectionCount,
               COUNT(DISTINCT CASE WHEN p.intervalMinutes >= 30240 AND p.repetitions >= 4 THEN p.cardId || ':' || p.direction END) AS masteredDirectionCount
        FROM english_study_sets s
        LEFT JOIN english_study_cards c ON c.setId = s.id
        LEFT JOIN english_card_progress p ON p.cardId = c.id
        GROUP BY s.id
        ORDER BY s.updatedAtMillis DESC, s.id DESC
        """
    )
    fun observeStudySets(): Flow<List<EnglishStudySetSummaryProjection>>

    @Query("SELECT * FROM english_study_sets WHERE id = :setId")
    fun observeStudySet(setId: Long): Flow<EnglishStudySetEntity?>

    @Query("SELECT * FROM english_study_sets WHERE id = :setId")
    suspend fun getStudySet(setId: Long): EnglishStudySetEntity?

    @Query("SELECT * FROM english_study_sets ORDER BY updatedAtMillis DESC, id DESC")
    suspend fun getAllStudySets(): List<EnglishStudySetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudySet(studySet: EnglishStudySetEntity): Long

    @Upsert
    suspend fun upsertStudySet(studySet: EnglishStudySetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStudySets(studySets: List<EnglishStudySetEntity>)

    @Query("DELETE FROM english_study_sets WHERE id = :setId")
    suspend fun deleteStudySet(setId: Long)

    @Query("DELETE FROM english_study_sets")
    suspend fun deleteAllStudySets()

    @Query("SELECT * FROM english_study_cards WHERE setId = :setId ORDER BY position, id")
    fun observeStudyCards(setId: Long): Flow<List<EnglishStudyCardEntity>>

    @Query("SELECT * FROM english_study_cards WHERE setId = :setId ORDER BY position, id")
    suspend fun getStudyCards(setId: Long): List<EnglishStudyCardEntity>

    @Query("SELECT * FROM english_study_cards WHERE id = :cardId")
    suspend fun getStudyCard(cardId: Long): EnglishStudyCardEntity?

    @Query("SELECT * FROM english_study_cards ORDER BY setId, position, id")
    suspend fun getAllStudyCards(): List<EnglishStudyCardEntity>

    @Query(
        """
        SELECT c.*, w.word AS dictionaryHeadword
        FROM english_study_cards c
        LEFT JOIN english_words w ON w.id = c.dictionaryWordId
        ORDER BY c.setId, c.position, c.id
        """
    )
    suspend fun getAllStudyCardsForBackup(): List<EnglishStudyCardBackupProjection>

    @Query(
        "SELECT * FROM english_study_cards WHERE setId = :setId AND dictionaryWordId = :wordId LIMIT 1"
    )
    suspend fun findStudyCardForDictionaryWord(setId: Long, wordId: Int): EnglishStudyCardEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM english_study_cards WHERE setId = :setId")
    suspend fun maxStudyCardPosition(setId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudyCard(card: EnglishStudyCardEntity): Long

    @Upsert
    suspend fun upsertStudyCard(card: EnglishStudyCardEntity)

    @Upsert
    suspend fun upsertStudyCards(cards: List<EnglishStudyCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStudyCards(cards: List<EnglishStudyCardEntity>)

    @Query("DELETE FROM english_study_cards WHERE id = :cardId")
    suspend fun deleteStudyCard(cardId: Long)

    @Query("DELETE FROM english_study_cards")
    suspend fun deleteAllStudyCards()

    @Query(
        """
        SELECT c.* FROM english_study_cards c
        LEFT JOIN english_card_progress p
          ON p.cardId = c.id AND p.direction = :direction
        WHERE c.setId = :setId
          AND (p.cardId IS NULL OR p.dueAtMillis <= :nowMillis)
        ORDER BY CASE WHEN p.cardId IS NULL THEN 1 ELSE 0 END,
                 COALESCE(p.dueAtMillis, 9223372036854775807),
                 c.position,
                 c.id
        LIMIT 1
        """
    )
    suspend fun nextDueStudyCard(
        setId: Long,
        direction: String,
        nowMillis: Long
    ): EnglishStudyCardEntity?

    @Query("SELECT * FROM english_card_progress WHERE cardId = :cardId AND direction = :direction")
    suspend fun getCardProgress(cardId: Long, direction: String): EnglishCardProgressEntity?

    @Query("SELECT * FROM english_card_progress WHERE cardId = :cardId ORDER BY direction")
    fun observeCardProgress(cardId: Long): Flow<List<EnglishCardProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardProgress(progress: EnglishCardProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCardProgress(progress: List<EnglishCardProgressEntity>)

    @Query("SELECT * FROM english_card_progress ORDER BY cardId, direction")
    suspend fun getAllCardProgress(): List<EnglishCardProgressEntity>

    @Query("DELETE FROM english_card_progress")
    suspend fun deleteAllCardProgress()

    // Dictionary lookup and structured article data.
    @Query("SELECT * FROM english_words WHERE id = :wordId")
    suspend fun getDictionaryWord(wordId: Int): EnglishWordEntity?

    @Query("SELECT * FROM english_words WHERE word = :headword COLLATE NOCASE LIMIT 1")
    suspend fun getDictionaryWord(headword: String): EnglishWordEntity?

    @Query(
        """
        SELECT * FROM english_words
        WHERE word LIKE :query || '%' COLLATE NOCASE
           OR REPLACE(translation, 'ё', 'е') LIKE '%' || REPLACE(:query, 'ё', 'е') || '%'
        ORDER BY CASE WHEN word = :query COLLATE NOCASE THEN 0 ELSE 1 END,
                 frequencyRank
        LIMIT :limit
        """
    )
    suspend fun searchDictionary(query: String, limit: Int): List<EnglishWordEntity>

    @Query("SELECT * FROM english_words ORDER BY frequencyRank, id LIMIT :limit OFFSET :offset")
    suspend fun browseDictionary(limit: Int, offset: Int): List<EnglishWordEntity>

    @Query("SELECT * FROM english_word_senses WHERE wordId = :wordId ORDER BY senseOrder")
    suspend fun getDictionarySenses(wordId: Int): List<EnglishWordSenseEntity>
}
