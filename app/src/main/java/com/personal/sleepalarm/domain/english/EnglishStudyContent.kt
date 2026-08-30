package com.personal.sleepalarm.domain.english

import java.text.Normalizer
import java.util.Locale

enum class EnglishStudyDirection {
    EN_TO_RU,
    RU_TO_EN,
    MIXED;

    val isConcrete: Boolean
        get() = this != MIXED

    fun requireConcrete(): EnglishStudyDirection {
        require(isConcrete) { "MIXED is a session strategy, not a persisted review direction" }
        return this
    }

    companion object {
        fun fromStorage(value: String?, fallback: EnglishStudyDirection = MIXED): EnglishStudyDirection {
            return entries.firstOrNull { it.name == value } ?: fallback
        }
    }
}

data class EnglishStudySetDraft(
    val title: String,
    val description: String = "",
    val colorSeed: Int = 0,
    val defaultDirection: EnglishStudyDirection = EnglishStudyDirection.MIXED
)

data class EnglishStudyCardDraft(
    val term: String,
    val translation: String,
    val definition: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val notes: String = "",
    val dictionaryWordId: Int? = null
)

data class EnglishStudyContentValidation(
    val normalizedSetTitle: String? = null,
    val normalizedCard: EnglishStudyCardDraft? = null,
    val errors: Set<EnglishStudyContentError> = emptySet()
) {
    val isValid: Boolean get() = errors.isEmpty()
}

enum class EnglishStudyContentError {
    EMPTY_SET_TITLE,
    SET_TITLE_TOO_LONG,
    EMPTY_TERM,
    EMPTY_TRANSLATION,
    TERM_TOO_LONG,
    TRANSLATION_TOO_LONG,
    DEFINITION_TOO_LONG,
    EXAMPLE_TOO_LONG,
    NOTES_TOO_LONG
}

class EnglishStudyContentValidationException(
    val validationErrors: Set<EnglishStudyContentError>
) : IllegalArgumentException(validationErrors.joinToString(","))

object EnglishStudyContentValidator {
    const val MAX_SET_TITLE_LENGTH = 80
    const val MAX_TERM_LENGTH = 120
    const val MAX_TRANSLATION_LENGTH = 240
    const val MAX_DEFINITION_LENGTH = 2_000
    const val MAX_EXAMPLE_LENGTH = 1_000
    const val MAX_NOTES_LENGTH = 4_000

    fun validateSet(draft: EnglishStudySetDraft): EnglishStudyContentValidation {
        val title = normalizeWhitespace(draft.title)
        val errors = buildSet {
            if (title.isEmpty()) add(EnglishStudyContentError.EMPTY_SET_TITLE)
            if (title.length > MAX_SET_TITLE_LENGTH) add(EnglishStudyContentError.SET_TITLE_TOO_LONG)
        }
        return EnglishStudyContentValidation(normalizedSetTitle = title, errors = errors)
    }

    fun validateCard(draft: EnglishStudyCardDraft): EnglishStudyContentValidation {
        val normalized = draft.copy(
            term = normalizeWhitespace(draft.term),
            translation = normalizeWhitespace(draft.translation),
            definition = normalizeMultiline(draft.definition),
            example = normalizeMultiline(draft.example),
            exampleTranslation = normalizeMultiline(draft.exampleTranslation),
            notes = normalizeMultiline(draft.notes)
        )
        val errors = buildSet {
            if (normalized.term.isEmpty()) add(EnglishStudyContentError.EMPTY_TERM)
            if (normalized.translation.isEmpty()) add(EnglishStudyContentError.EMPTY_TRANSLATION)
            if (normalized.term.length > MAX_TERM_LENGTH) add(EnglishStudyContentError.TERM_TOO_LONG)
            if (normalized.translation.length > MAX_TRANSLATION_LENGTH) {
                add(EnglishStudyContentError.TRANSLATION_TOO_LONG)
            }
            if (normalized.definition.length > MAX_DEFINITION_LENGTH) {
                add(EnglishStudyContentError.DEFINITION_TOO_LONG)
            }
            if (normalized.example.length > MAX_EXAMPLE_LENGTH ||
                normalized.exampleTranslation.length > MAX_EXAMPLE_LENGTH
            ) {
                add(EnglishStudyContentError.EXAMPLE_TOO_LONG)
            }
            if (normalized.notes.length > MAX_NOTES_LENGTH) add(EnglishStudyContentError.NOTES_TOO_LONG)
        }
        return EnglishStudyContentValidation(normalizedCard = normalized, errors = errors)
    }

    private fun normalizeWhitespace(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun normalizeMultiline(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { normalizeWhitespace(it) }
        .trim()
}

data class EnglishDictionarySense(
    val order: Int,
    val definition: String,
    val translations: List<String>,
    val example: String = "",
    val exampleTranslation: String = "",
    val synonyms: List<String> = emptyList(),
    val usageLabels: List<String> = emptyList()
)

data class EnglishDictionaryArticle(
    val wordId: Int,
    val headword: String,
    val pronunciation: String,
    val partOfSpeech: String,
    val frequencyLevel: String,
    val frequencyRank: Int,
    val senses: List<EnglishDictionarySense>
)

data class EnglishStudyPrompt(
    val direction: EnglishStudyDirection,
    val prompt: String,
    val expectedAnswers: List<String>,
    val definition: String,
    val example: String,
    val exampleTranslation: String,
    val notes: String
)

object EnglishStudyPromptFactory {
    fun create(
        term: String,
        translation: String,
        definition: String,
        example: String,
        exampleTranslation: String,
        notes: String,
        direction: EnglishStudyDirection
    ): EnglishStudyPrompt {
        direction.requireConcrete()
        val translations = splitAlternatives(translation)
        return when (direction) {
            EnglishStudyDirection.RU_TO_EN -> EnglishStudyPrompt(
                direction = direction,
                prompt = translations.firstOrNull().orEmpty(),
                expectedAnswers = listOf(term),
                definition = definition,
                example = example,
                exampleTranslation = exampleTranslation,
                notes = notes
            )
            EnglishStudyDirection.EN_TO_RU -> EnglishStudyPrompt(
                direction = direction,
                prompt = term,
                expectedAnswers = translations,
                definition = definition,
                example = example,
                exampleTranslation = exampleTranslation,
                notes = notes
            )
            EnglishStudyDirection.MIXED -> error("MIXED must be resolved by the session before creating a prompt")
        }
    }

    fun splitAlternatives(value: String): List<String> = value
        .split(';', '|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy(::normalizeComparable)

    private fun normalizeComparable(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}0-9]+"), " ")
        .trim()
}

data class BilingualAnswerEvaluation(
    val isCorrect: Boolean,
    val isMinorTypo: Boolean,
    val normalizedActual: String,
    val matchedExpected: String?
)

object BilingualAnswerEvaluator {
    fun evaluate(
        actual: String,
        expectedAnswers: List<String>,
        direction: EnglishStudyDirection
    ): BilingualAnswerEvaluation {
        direction.requireConcrete()
        val normalize: (String) -> String = if (direction == EnglishStudyDirection.RU_TO_EN) {
            EnglishAnswerEvaluator::normalize
        } else {
            ::normalizeRussian
        }
        val normalizedActual = normalize(actual)
        val normalizedExpected = expectedAnswers
            .map { original -> original to normalize(original) }
            .filter { it.second.isNotEmpty() }
        val exact = normalizedExpected.firstOrNull { it.second == normalizedActual }
        if (exact != null) {
            return BilingualAnswerEvaluation(true, false, normalizedActual, exact.first)
        }
        val minor = normalizedExpected.firstOrNull { (_, expected) ->
            expected.length >= 5 && (
                EnglishAnswerEvaluator.editDistance(expected, normalizedActual) == 1 ||
                    EnglishAnswerEvaluator.hasSingleAdjacentTransposition(expected, normalizedActual)
                )
        }
        return BilingualAnswerEvaluation(
            isCorrect = minor != null,
            isMinorTypo = minor != null,
            normalizedActual = normalizedActual,
            matchedExpected = minor?.first
        )
    }

    internal fun normalizeRussian(value: String): String = Normalizer
        .normalize(value.trim().lowercase(Locale.forLanguageTag("ru")), Normalizer.Form.NFKC)
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
